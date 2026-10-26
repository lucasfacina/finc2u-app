package br.com.finc2u.server.features.expense.entity;

import br.com.finc2u.server.config.BusinessConstants;
import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "tb_expense",
    uniqueConstraints = @UniqueConstraint(name = "uk_expense_recurring_month", columnNames = {"recurring_expense_id", "recurrence_month"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends BaseModel {

    private String description;
    private LocalDate dueDate;

    // Data real do pagamento. Só existe com PAID; desconhecida = null (não se inventa data).
    private LocalDate paidAt;

    private Integer currentInstallment;
    private Integer totalInstallment;

    // Liga todas as parcelas de uma mesma compra (só PARCELED); nulo nas demais despesas.
    private UUID installmentGroupId;

    @Column(name = "expense_value")
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    private ExpenseType expenseType;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    // LAZY: nenhum Response lê o dono da despesa (só o id dele é usado, e o id de um proxy não gera consulta).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @ManyToOne
    private CardAccount cardAccount;

    // LAZY: só as consultas que montam o ExpenseResponse carregam a categoria (via @EntityGraph); o resumo mensal não a lê.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Category category;

    // Id do modelo recorrente que gerou a despesa (nulo nas demais). Só o id, sem associação: a feature recurringexpense depende
    // de expense, e o contrário fecharia um ciclo. O JPA não cria a FK; ela entra à mão na V1 do Flyway.
    private UUID recurringExpenseId;

    // Mês em que o modelo recorrente gerou a despesa (sempre o dia 1; nulo nas demais). Anda junto com recurringExpenseId: os dois são
    // nulos ou os dois preenchidos. É a chave da geração: o vencimento pode ser editado depois, o mês não.
    private LocalDate recurrenceMonth;

    /*
     * Resolve a data de vencimento da despesa:
     * - Se dueDate já foi informado, ele prevalece (override manual).
     * - Com cartão e sem dueDate: calcula a partir de purchaseDate + fechamento/vencimento do cartão. A data de compra
     *   chega já resolvida (o service usa "hoje" quando o cliente não informa): a entidade não lê o relógio.
     * - Sem cartão e sem dueDate: erro (despesa avulsa exige vencimento manual).
     */
    public void resolveDueDate(LocalDate purchaseDate) {
        if (dueDate != null) return;

        if (cardAccount == null)
            throw new BusinessException("Data de vencimento é obrigatória para despesas sem cartão.");

        if (cardAccount.getClosingDate() == null || cardAccount.getDueDate() == null)
            throw new BusinessException("Cartão não possui datas de fechamento/vencimento configuradas.");

        this.dueDate = cardAccount.calculateInvoiceDueDate(purchaseDate);
    }

    /*
     * Define o status de pagamento por padrão quando não informado: PENDING. Se o vencimento já passou (anterior à data
     * de referência, que é hoje), o sistema não adivinha: o cliente precisa dizer se a despesa já foi paga.
     */
    public void resolveDefaultPaymentStatus(LocalDate referenceDate) {
        if (paymentStatus != null) return;

        if (dueDate.isBefore(referenceDate)) {
            throw new InvalidRequestException(
                "Vencimento anterior a hoje: informe se a despesa já foi paga (paymentStatus).");
        }

        paymentStatus = PaymentStatus.PENDING;
    }

    /*
     * Valida regras de PARCELED: total entre o mínimo e o teto de parcelas, parcela atual a partir da primeira (padrão:
     * a primeira) e nunca acima do total. O status não depende da parcela. O teto e o piso impedem que a projeção de
     * parcelas futuras gere uma quantidade absurda de objetos (ex.: parcela atual negativa).
     */
    public void applyInstallmentRules() {
        if (isParceled()) {
            if (totalInstallment == null || totalInstallment < BusinessConstants.MIN_INSTALLMENTS)
                throw new BusinessException("Total de parcelas deve ser pelo menos 1 para despesas parceladas");

            if (totalInstallment > BusinessConstants.MAX_INSTALLMENTS) {
                throw new BusinessException(
                    "Total de parcelas (" + totalInstallment + ") não pode ser maior que " + BusinessConstants.MAX_INSTALLMENTS + "."
                );
            }
            if (currentInstallment == null) {
                currentInstallment = BusinessConstants.DEFAULT_FIRST_INSTALLMENT;
            }
            if (currentInstallment < BusinessConstants.DEFAULT_FIRST_INSTALLMENT)
                throw new BusinessException("Parcela atual (" + currentInstallment + ") deve ser pelo menos 1.");

            if (currentInstallment > totalInstallment) {
                throw new BusinessException(
                    "Parcela atual (" + currentInstallment + ") não pode ser maior que o total (" + totalInstallment + ")."
                );
            }
        }
    }

    /*
     * Confere se uma edição respeita o grupo de parcelas, comparando esta despesa com o que o usuário mandou (`edited`):
     * - Numa parcela (com installmentGroupId), o tipo, o total e a parcela atual são a identidade dela: mandar outro valor é
     *   erro, e valor nulo quer dizer "não mexer". Descrição, valor, vencimento e status seguem editáveis por parcela.
     * - Numa despesa avulsa, não dá para virar parcelada por edição: o parcelamento nasce da compra, pelo POST.
     */
    public void assertEditKeepsTheInstallments(Expense edited) {
        if (installmentGroupId == null && edited.isParceled())
            throw new BusinessException("Para parcelar, lance a compra parcelada pelo POST.");

        if (installmentGroupId == null) return;

        var changesType = edited.expenseType != null && edited.expenseType != expenseType;
        var changesTotal = edited.totalInstallment != null && !edited.totalInstallment.equals(totalInstallment);
        var changesCurrent = edited.currentInstallment != null && !edited.currentInstallment.equals(currentInstallment);

        if (changesType || changesTotal || changesCurrent)
            throw new BusinessException("Uma parcela não muda de tipo nem de numeração: apague a compra e lance de novo.");
    }

    /*
     * Normaliza o estado derivado da despesa antes de persistir, na ordem correta:
     * resolve o vencimento, define o status padrão (que depende do vencimento) e aplica
     * as regras de parcelamento. As duas datas chegam resolvidas: a da compra (base do vencimento pelo cartão) e a de
     * referência, que é "hoje" (base do status padrão).
     */
    public void prepareForPersistence(LocalDate purchaseDate, LocalDate referenceDate) {
        resolveDueDate(purchaseDate);
        resolveDefaultPaymentStatus(referenceDate);
        applyInstallmentRules();
        assertPaidAtConsistent(paymentStatus, paidAt, referenceDate);
    }

    /*
     * Confere que o cartão da despesa vale na data da compra: compra depois da validade é recusada. Sem cartão não há o que
     * conferir (a despesa pode ser avulsa). Fica fora do prepareForPersistence porque, na alteração, só roda quando a data da
     * compra é informada.
     */
    public void assertCardUsableOn(LocalDate purchaseDate) {
        if (cardAccount == null) return;

        cardAccount.assertUsableOn(purchaseDate);
    }

    /*
     * Muda o status de pagamento mantendo o paidAt coerente: PAID sem data usa a data de referência (hoje); PENDING
     * limpa o paidAt (informá-lo junto com PENDING é erro). Valida antes de alterar, para não deixar a entidade pela metade.
     */
    public void changePaymentStatus(PaymentStatus status, LocalDate paidAt, LocalDate referenceDate) {
        var effectivePaidAt = status == PaymentStatus.PAID && paidAt == null
            ? referenceDate
            : paidAt;
        assertPaidAtConsistent(status, effectivePaidAt, referenceDate);

        this.paymentStatus = status;
        this.paidAt = effectivePaidAt;
    }

    // O paidAt só vale com PAID e nunca é posterior à data de referência (hoje).
    private static void assertPaidAtConsistent(PaymentStatus status, LocalDate paidAt, LocalDate referenceDate) {
        if (paidAt == null) return;

        if (status != PaymentStatus.PAID)
            throw new BusinessException("A data do pagamento só pode ser informada para despesa paga.");

        if (paidAt.isAfter(referenceDate))
            throw new BusinessException("A data do pagamento não pode ser posterior a hoje.");
    }

    /*
     * A parcela da mesma compra que vence `monthsAfter` meses depois desta. Copia os dados da compra (descrição, valor,
     * tipo, total de parcelas, grupo, dono, cartão e categoria), numera a parcela a partir da atual e calcula o vencimento
     * sobre o desta, nunca encadeado (31/jan → 28/fev → 31/mar, e não 28/mar). Nasce PENDING, sem paidAt e sem id.
     */
    public Expense installmentAfter(int monthsAfter) {
        return Expense.builder()
            .description(description)
            .value(value)
            .dueDate(dueDate.plusMonths(monthsAfter))
            .expenseType(expenseType)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(currentInstallment + monthsAfter)
            .totalInstallment(totalInstallment)
            .installmentGroupId(installmentGroupId)
            .user(user)
            .cardAccount(cardAccount)
            .category(category)
            .build();
    }

    /*
     * Atraso derivado: PENDING com vencimento anterior à data de referência. Não é coluna nem status: nenhum job
     * muda a despesa, e a mesma data de referência produz sempre a mesma resposta.
     */
    public boolean isOverdue(LocalDate referenceDate) {
        return isPending() && dueDate.isBefore(referenceDate);
    }

    // O tipo e o status em forma de pergunta, para a regra ler como frase (o `isOverdue` já era assim).
    public boolean isParceled() {
        return expenseType == ExpenseType.PARCELED;
    }

    public boolean isFixed() {
        return expenseType == ExpenseType.FIXED;
    }

    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }

    public boolean isPending() {
        return paymentStatus == PaymentStatus.PENDING;
    }

}
