package br.com.finc2u.server.features.expense.service;

import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.mapper.ExpenseMapper;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.specification.ExpenseSpecification;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.expense.vo.InstallmentSchedule;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserService userService;
    private final ExpenseMapper expenseMapper;
    private final Clock clock;

    @Transactional
    public Expense create(UUID userId, ExpenseCommand command) {
        var expense = command.expense();
        expense.setUser(userService.getById(userId));

        expenseMapper.applyCardAccount(expense, command.cardAccountId(), command.cardName());
        expenseMapper.applyCategory(expense, command.categoryId());

        var today = LocalDate.now(clock);
        var purchaseDate = command.purchaseDate() != null
            ? command.purchaseDate()
            : today;
        assertCardUsable(expense, purchaseDate);

        expense.prepareForPersistence(purchaseDate, today);

        if (expense.getExpenseType() == ExpenseType.PARCELED) {
            return saveInstallments(expense);
        }
        return expenseRepository.save(expense);
    }

    @Transactional(readOnly = true)
    public Expense getById(UUID id) {
        return expenseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Expense> getByUser(UUID userId) {
        userService.getById(userId);
        return expenseRepository.findByUserId(userId);
    }

    // Despesas com vencimento entre as duas datas (inclusive): é a leitura que o resumo mensal usa, no lugar do repositório.
    @Transactional(readOnly = true)
    public List<Expense> getByUserAndPeriod(UUID userId, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new InvalidRequestException("A data inicial não pode ser posterior à data final.");
        }
        userService.getById(userId);
        return expenseRepository.findByUserIdAndDueDateBetween(userId, start, end);
    }

    @Transactional(readOnly = true)
    public List<Expense> getWithFilters(UUID userId, ExpenseFilter filter) {
        userService.getById(userId);
        return expenseRepository.findAll(ExpenseSpecification.ofFilter(userId, filter, LocalDate.now(clock)));
    }

    @Transactional
    public Expense update(UUID id, ExpenseCommand command) {
        var expense = getById(id);

        expenseMapper.applyCardAccount(expense, command.cardAccountId(), command.cardName());
        expenseMapper.applyCategory(expense, command.categoryId());
        expenseMapper.applyEditableFields(expense, command.expense());

        if (command.purchaseDate() != null) {
            assertCardUsable(expense, command.purchaseDate());
        }

        var today = LocalDate.now(clock);
        expense.prepareForPersistence(command.purchaseDate() != null
            ? command.purchaseDate()
            : today, today
        );

        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense updateStatus(UUID id, PaymentStatus status, LocalDate paidAt) {
        Expense expense = getById(id);
        expense.changePaymentStatus(status, paidAt, LocalDate.now(clock));

        return expenseRepository.save(expense);
    }

    @Transactional
    public void delete(UUID id) {
        Expense expense = getById(id);
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public List<Expense> getByInstallmentGroup(UUID groupId) {
        var installments = expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId);
        if (installments.isEmpty()) {
            throw new ResourceNotFoundException("Compra parcelada não encontrada com o grupo: " + groupId);
        }
        return installments;
    }

    // Remove a compra parcelada inteira; delete(id) continua removendo uma parcela só.
    @Transactional
    public void deleteInstallmentGroup(UUID groupId) {
        expenseRepository.deleteAll(getByInstallmentGroup(groupId));
    }

    /*
     * Cria a despesa do modelo recorrente no vencimento: FIXED e PENDING, com os dados do modelo. Idempotente: se o modelo
     * já tem despesa nesse vencimento, não cria nada. O cartão não é revalidado (a validade vale na data da compra).
     */
    @Transactional
    public Optional<Expense> createFromRecurrence(RecurringExpense recurringExpense, LocalDate dueDate) {
        if (expenseRepository.existsByRecurringExpenseIdAndDueDate(recurringExpense.getId(), dueDate)) {
            return Optional.empty();
        }

        return Optional.of(expenseRepository.save(Expense.builder()
            .description(recurringExpense.getDescription())
            .value(recurringExpense.getValue())
            .dueDate(dueDate)
            .expenseType(ExpenseType.FIXED)
            .paymentStatus(PaymentStatus.PENDING)
            .user(recurringExpense.getUser())
            .cardAccount(recurringExpense.getCardAccount())
            .category(recurringExpense.getCategory())
            .recurringExpense(recurringExpense)
            .build()));
    }

    // Solta do modelo as despesas que ele gerou (o histórico fica); chamado antes de apagar o modelo recorrente.
    @Transactional
    public void detachFromRecurringExpense(UUID recurringExpenseId) {
        expenseRepository.detachFromRecurringExpense(recurringExpenseId);
    }

    // Compra parcelada vira uma linha por mês (k..N) ligadas por um grupo novo; tudo na transação do create. Devolve a
    // parcela informada.
    private Expense saveInstallments(Expense first) {
        first.setInstallmentGroupId(UUID.randomUUID());
        expenseRepository.saveAll(InstallmentSchedule.from(first).installments());
        return first;
    }

    private void assertCardUsable(Expense expense, LocalDate purchaseDate) {
        if (expense.getCardAccount() != null) {
            expense.getCardAccount().assertUsableOn(purchaseDate);
        }
    }

}
