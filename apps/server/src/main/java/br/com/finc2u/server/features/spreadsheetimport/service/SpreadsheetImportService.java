package br.com.finc2u.server.features.spreadsheetimport.service;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.expense.vo.RecurrenceExpenseCommand;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import br.com.finc2u.server.features.spreadsheetimport.enums.EntryKind;
import br.com.finc2u.server.features.spreadsheetimport.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/*
 * Grava o que o SpreadsheetReader leu, sempre pelos services das outras features (nunca pelo repository), numa transação
 * só: se algo falhar, nada fica pela metade. É idempotente: antes de criar, procura o que já existe com a mesma chave
 * (descrição, vencimento, valor, parcela e cartão) e só cria o que falta, então rodar de novo não duplica.
 *
 * Como cada parte da planilha vira dado do backend:
 * - `FIXOS` viram modelos recorrentes (um por trecho de meses com o mesmo valor) e as despesas dos meses já passados entram
 *   pelo `createFromRecurrence`, porque o gerador normal nunca volta no tempo.
 * - A parcela `n/m` vira compra parcelada: a primeira parcela lida cria as seguintes (como o POST /expenses), e depois as
 *   parcelas dos meses que a planilha cobre são ajustadas ao que ela diz (valor e pago) ou removidas, se a planilha não as
 *   tem naquele mês; o que passa do último mês da planilha fica como projeção.
 * - `GERAIS` (compras à vista) vira uma despesa por cartão e mês; os valores negativos (ajustes de saldo) viram uma renda
 *   extra "Ajuste de balanço", porque a despesa não aceita valor negativo.
 * - Contas fixas e extras viram uma despesa cada; o `PG` vira PAID com `paidAt` nulo (a planilha não diz o dia).
 * - Toda a renda vira renda extra no mês de pagamento: a planilha tem o total recebido de cada mês, e o salário (que o
 *   backend guarda por vigência) aparece em poucos meses e com valores soltos.
 */
@Service
@RequiredArgsConstructor
public class SpreadsheetImportService {

    private static final String ADJUSTMENT = "Ajuste de balanço";

    private final CardAccountService cardAccountService;
    private final ExpenseService expenseService;
    private final RecurringExpenseService recurringExpenseService;
    private final ExtraIncomeService extraIncomeService;

    @Transactional
    public ImportResult importFor(UUID userId, SpreadsheetData data, ImportOptions options) {
        var tally = new Tally();
        var cards = ensureCards(userId, data.cards(), tally);
        var lastMonth = data.entries().stream()
            .map(SpreadsheetEntry::paymentMonth)
            .max(YearMonth::compareTo)
            .orElse(null);
        if (lastMonth == null) return tally.result();

        var context = new Context(userId, cards, options, lastMonth);
        var existing = index(expenseService.getByUser(userId));
        var fixed = data.entries().stream().filter(entry -> entry.kind() == EntryKind.CARD_FIXED).toList();
        var installments = data.entries().stream().filter(entry -> entry.installment() != null).toList();

        importFixed(context, fixed, tally);
        importInstallments(context, existing, installments, tally);
        importSingles(context, existing, data.entries(), tally);
        importIncome(context, data.entries(), tally);

        return tally.result();
    }

    // ---- cartões ----

    private Map<String, CardAccount> ensureCards(UUID userId, List<SpreadsheetCard> sheetCards, Tally tally) {
        var cards = new HashMap<String, CardAccount>();
        var current = cardAccountService.getByUser(userId, null);

        for (var sheetCard : sheetCards) {
            var name = titled(sheetCard.name());
            var found = current.stream()
                .filter(card -> card.getCardName().equalsIgnoreCase(name))
                .findFirst();

            cards.put(sheetCard.name(), found.orElseGet(() -> {
                tally.cards++;
                return cardAccountService.create(CardAccount.of(name, sheetCard.closingDay(), sheetCard.dueDay(), null), userId);
            }));
        }

        return cards;
    }

    // ---- assinaturas fixas ----

    private void importFixed(Context context, List<SpreadsheetEntry> entries, Tally tally) {
        var models = new ArrayList<>(recurringExpenseService.getByUser(context.userId()));
        var instances = new ArrayList<PendingInstance>();

        for (var span : FixedSpan.from(entries)) {
            var card = context.cards().get(span.card());
            var firstCharge = chargeMonth(span.firstMonth(), card);
            var endsBeforeTheSheet = span.lastMonth().isBefore(context.lastMonth());
            var endCharge = endsBeforeTheSheet
                ? chargeMonth(span.lastMonth(), card)
                : null;
            var model = models.stream()
                .filter(existing -> matches(existing, span, card, firstCharge))
                .findFirst()
                .orElse(null);

            if (model == null) {
                model = recurringExpenseService.create(new RecurringExpenseCommand(
                    RecurringExpense.of(span.description(), span.value(), card.getClosingDate(), firstCharge, endCharge),
                    null, context.options().categoryFor(span.description()), card.getId()), context.userId());
                models.add(model);
                tally.recurring++;
            } else {
                tally.alreadyThere++;
            }

            for (var entry : span.entries()) {
                var dueDate = model.dueDateIn(chargeMonth(entry.paymentMonth(), card));
                var created = expenseService.createFromRecurrence(new RecurrenceExpenseCommand(model.getId(), span.description(),
                    entry.value(), dueDate, model.getUser(), model.getCategory(), model.getCardAccount()));

                tally.expenses += created.isPresent()
                    ? 1
                    : 0;
                instances.add(new PendingInstance(model.getId(), dueDate, entry.paid()));
            }
        }

        // As despesas que o modelo já tinha gerado ao ser criado (mês corrente e seguinte) só ficam pagas depois de recarregadas.
        var byInstance = new HashMap<String, Expense>();
        expenseService.getByUser(context.userId()).stream()
            .filter(expense -> expense.getRecurringExpenseId() != null)
            .forEach(expense -> byInstance.put(expense.getRecurringExpenseId() + "|" + expense.getDueDate(), expense));

        for (var instance : instances) {
            var expense = byInstance.get(instance.modelId + "|" + instance.dueDate);
            if (expense == null) continue;

            reconcile(expense, expense.getValue(), instance.paid);
        }
    }

    private boolean matches(RecurringExpense model, FixedSpan span, CardAccount card, YearMonth firstCharge) {
        return model.getDescription().equalsIgnoreCase(span.description())
            && model.getValue().compareTo(span.value()) == 0
            && model.getCardAccount() != null && model.getCardAccount().getId().equals(card.getId())
            && YearMonth.from(model.getStartMonth()).equals(firstCharge);
    }

    // O modelo conta por mês de compra, e a despesa do mês cai na fatura que vence no mês de pagamento: se o cartão vence antes
    // de fechar (fecha dia 30, vence dia 15), a compra do mês anterior é a que vence no mês de pagamento.
    private YearMonth chargeMonth(YearMonth paymentMonth, CardAccount card) {
        return card.getDueDate() < card.getClosingDate()
            ? paymentMonth.minusMonths(1)
            : paymentMonth;
    }

    // ---- compras parceladas ----

    private void importInstallments(Context context, Map<String, Deque<Expense>> existing, List<SpreadsheetEntry> entries, Tally tally) {
        for (var chain : InstallmentChain.from(entries)) {
            var first = chain.first();
            var card = cardOf(context, first);
            var cardName = nameOf(card);
            var description = description(first, card);
            var dueDate = dueDate(context, first);
            var key = key(description, dueDate, first.value(), ExpenseType.PARCELED, first.installment(), chain.total(), cardName);
            if (take(existing, key) != null) {
                tally.alreadyThere++;
                continue;
            }

            var purchase = Expense.builder()
                .description(description)
                .value(first.value())
                .dueDate(dueDate)
                .expenseType(ExpenseType.PARCELED)
                .paymentStatus(status(first))
                .currentInstallment(first.installment())
                .totalInstallment(chain.total())
                .build();
            var saved = expenseService.create(context.userId(),
                new ExpenseCommand(purchase, null, cardName, null, context.options().categoryFor(chain.description()), null));
            var rows = expenseService.getByInstallmentGroup(saved.getInstallmentGroupId());
            tally.expenses += rows.size();

            for (var row : rows) {
                var month = YearMonth.from(row.getDueDate());
                if (month.isAfter(context.lastMonth())) continue;

                var observed = chain.entries().stream().filter(entry -> entry.paymentMonth().equals(month)).findFirst();
                if (observed.isPresent()) {
                    reconcile(row, observed.get().value(), observed.get().paid());
                } else {
                    expenseService.delete(row.getId());
                    tally.expenses--;
                }
            }
        }
    }

    // ---- despesas avulsas ----

    private void importSingles(Context context, Map<String, Deque<Expense>> existing, List<SpreadsheetEntry> entries, Tally tally) {
        for (var entry : entries) {
            var handledElsewhere = entry.kind() == EntryKind.CARD_FIXED || entry.kind() == EntryKind.INCOME || entry.installment() != null;
            if (handledElsewhere || entry.value().signum() < 0) continue;

            var card = cardOf(context, entry);
            var cardName = nameOf(card);
            var description = description(entry, card);
            var dueDate = dueDate(context, entry);
            var type = entry.kind() == EntryKind.BILL
                ? ExpenseType.FIXED
                : ExpenseType.VARIABLE;

            var found = take(existing, key(description, dueDate, entry.value(), type, null, null, cardName));
            if (found != null) {
                tally.alreadyThere++;
                reconcile(found, entry.value(), entry.paid());
                continue;
            }

            var expense = Expense.builder()
                .description(description)
                .value(entry.value())
                .dueDate(dueDate)
                .expenseType(type)
                .paymentStatus(status(entry))
                .build();
            expenseService.create(context.userId(), new ExpenseCommand(expense, null, cardName, null, context.options().categoryFor(entry.description()), null));
            tally.expenses++;
        }
    }

    // ---- renda ----

    private void importIncome(Context context, List<SpreadsheetEntry> entries, Tally tally) {
        var existing = new HashMap<String, Integer>();
        extraIncomeService.getByUser(context.userId()).forEach(income ->
            existing.merge(incomeKey(income.getDescription(), income.getDate(), income.getValue()), 1, Integer::sum));

        for (var entry : entries) {
            var credit = entry.kind() == EntryKind.CARD_GENERAL && entry.value().signum() < 0;
            if (entry.kind() != EntryKind.INCOME && !credit) continue;

            var description = credit
                ? ADJUSTMENT + " no " + titled(entry.card())
                : entry.description();
            var value = entry.value().abs();
            var date = incomeDate(context, entry);
            var key = incomeKey(description, date, value);
            var remaining = existing.getOrDefault(key, 0);
            if (remaining > 0) {
                existing.put(key, remaining - 1);
                tally.alreadyThere++;
                continue;
            }

            extraIncomeService.create(ExtraIncome.builder().description(description).value(value).date(date).build(), context.userId());
            tally.incomes++;
        }
    }

    // ---- apoio ----

    // Ajusta uma despesa ao que a planilha diz: o valor e o "PG". Nunca desfaz um pagamento já registrado.
    private void reconcile(Expense expense, BigDecimal value, boolean paid) {
        var differentValue = expense.getValue().compareTo(value) != 0;
        var needsPayment = paid && expense.getPaymentStatus() != PaymentStatus.PAID;
        if (!differentValue && !needsPayment) return;

        var edited = Expense.builder()
            .description(expense.getDescription())
            .value(value)
            .dueDate(expense.getDueDate())
            .expenseType(expense.getExpenseType())
            .paymentStatus(needsPayment
                ? PaymentStatus.PAID
                : expense.getPaymentStatus())
            .paidAt(needsPayment
                ? null
                : expense.getPaidAt())
            .currentInstallment(expense.getCurrentInstallment())
            .totalInstallment(expense.getTotalInstallment())
            .build();
        var cardId = expense.getCardAccount() == null
            ? null
            : expense.getCardAccount().getId();

        expenseService.update(expense.getId(), new ExpenseCommand(edited, cardId, null, expense.getCategory().getId(), null, null));
    }

    private PaymentStatus status(SpreadsheetEntry entry) {
        return entry.paid()
            ? PaymentStatus.PAID
            : PaymentStatus.PENDING;
    }

    private CardAccount cardOf(Context context, SpreadsheetEntry entry) {
        return entry.card() == null
            ? null
            : context.cards().get(entry.card());
    }

    private String nameOf(CardAccount card) {
        return card == null
            ? null
            : card.getCardName();
    }

    // A planilha não descreve os itens do cartão nem os gerais (só o nome do cartão), então a descrição vem do cartão.
    private String description(SpreadsheetEntry entry, CardAccount card) {
        if (entry.description() != null) return entry.description();

        var cardName = card == null
            ? titled(entry.card())
            : card.getCardName();

        return entry.kind() == EntryKind.CARD_GENERAL
            ? "Compras à vista no " + cardName
            : "Compra no " + cardName;
    }

    private LocalDate dueDate(Context context, SpreadsheetEntry entry) {
        if (entry.card() == null) return incomeDate(context, entry);

        var month = entry.paymentMonth();
        return month.atDay(Math.min(context.sheetCardDueDay(entry.card()), month.lengthOfMonth()));
    }

    private LocalDate incomeDate(Context context, SpreadsheetEntry entry) {
        var month = entry.paymentMonth();
        return context.options().dueDay() == null
            ? month.atEndOfMonth()
            : month.atDay(Math.min(context.options().dueDay(), month.lengthOfMonth()));
    }

    private Map<String, Deque<Expense>> index(List<Expense> expenses) {
        var index = new HashMap<String, Deque<Expense>>();

        for (var expense : expenses) {
            var key = key(expense.getDescription(), expense.getDueDate(), expense.getValue(), expense.getExpenseType(),
                expense.getCurrentInstallment(), expense.getTotalInstallment(), nameOf(expense.getCardAccount()));
            index.computeIfAbsent(key, k -> new ArrayDeque<>()).add(expense);
        }

        return index;
    }

    private Expense take(Map<String, Deque<Expense>> index, String key) {
        var rows = index.get(key);
        return rows == null
            ? null
            : rows.poll();
    }

    private String key(String description, LocalDate dueDate, BigDecimal value, ExpenseType type, Integer installment, Integer total, String card) {
        var cardKey = card == null
            ? "-"
            : card.toLowerCase(Locale.ROOT);

        return String.join("|", description.toLowerCase(Locale.ROOT), String.valueOf(dueDate), value.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            String.valueOf(type), String.valueOf(installment), String.valueOf(total), cardKey);
    }

    private String incomeKey(String description, LocalDate date, BigDecimal value) {
        return String.join("|", description.toLowerCase(Locale.ROOT), String.valueOf(date), value.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    // "MERCADO PAGO" -> "Mercado Pago"
    private String titled(String name) {
        var words = name.trim().toLowerCase(Locale.ROOT).split("\\s+");
        var titled = new StringBuilder();

        for (var word : words) {
            var separator = titled.isEmpty()
                ? ""
                : " ";
            titled.append(separator).append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return titled.toString();
    }

    private record PendingInstance(UUID modelId, LocalDate dueDate, boolean paid) {
    }

    private record Context(UUID userId, Map<String, CardAccount> cards, ImportOptions options, YearMonth lastMonth) {

        // O vencimento do item de cartão é o do cartão cadastrado (criado com os dias da planilha quando ainda não existia).
        private int sheetCardDueDay(String sheetCardName) {
            return cards.get(sheetCardName).getDueDate();
        }

    }

    private static final class Tally {

        private int cards;
        private int recurring;
        private int expenses;
        private int incomes;
        private int alreadyThere;

        private ImportResult result() {
            return ImportResult.of(cards, recurring, expenses, incomes, alreadyThere);
        }

    }

}
