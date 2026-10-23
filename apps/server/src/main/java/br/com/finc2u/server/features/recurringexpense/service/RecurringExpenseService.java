package br.com.finc2u.server.features.recurringexpense.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpenseSkip;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseSkipRepository;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseSkipRepository recurringExpenseSkipRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final CardAccountService cardAccountService;
    private final ExpenseService expenseService;
    private final RecurringExpenseMaterializer recurringExpenseMaterializer;

    @Transactional
    public RecurringExpense create(RecurringExpenseCommand command, UUID userId) {
        var recurringExpense = command.recurringExpense();

        recurringExpense.setUser(userService.getById(userId));
        applyReferences(recurringExpense, command);

        var saved = recurringExpenseRepository.save(recurringExpense);
        recurringExpenseMaterializer.materializeUpcoming(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public RecurringExpense getById(UUID id) {
        return recurringExpenseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Despesa recorrente não encontrada com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<RecurringExpense> getByUser(UUID userId) {
        userService.getById(userId);
        return recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(userId);
    }

    // Modelos que ainda geram despesa a partir do mês (sem término, ou com término neste mês ou depois), de todos os usuários.
    @Transactional(readOnly = true)
    public List<RecurringExpense> getActiveIn(YearMonth month) {
        return recurringExpenseRepository.findByEndMonthIsNullOrEndMonthGreaterThanEqual(month.atDay(1));
    }

    @Transactional
    public RecurringExpense update(UUID id, RecurringExpenseCommand command) {
        var recurringExpense = getById(id);
        var updates = command.recurringExpense();

        recurringExpense.setDescription(updates.getDescription());
        recurringExpense.setValue(updates.getValue());
        recurringExpense.setDayOfMonth(updates.getDayOfMonth());
        recurringExpense.setStartMonth(updates.getStartMonth());
        recurringExpense.setEndMonth(updates.getEndMonth());
        applyReferences(recurringExpense, command);

        return recurringExpenseRepository.save(recurringExpense);
    }

    // As despesas já geradas ficam (sem modelo): apagar o modelo não apaga o histórico. Para parar de gerar, use o endMonth.
    // Os pulos do modelo saem junto (antes dele, por causa da FK).
    @Transactional
    public void delete(UUID id) {
        var recurringExpense = getById(id);
        expenseService.detachFromRecurringExpense(id);
        recurringExpenseSkipRepository.deleteByRecurringExpenseId(id);
        recurringExpenseRepository.delete(recurringExpense);
    }

    /*
     * Registra o vencimento como pulado: o usuário apagou a despesa que o modelo gerou nele, e o modelo não a recria. Idempotente
     * (pular de novo o mesmo vencimento não duplica) e tolerante: um modelo que já não existe não tem o que pular.
     */
    @Transactional
    public void skipMonth(UUID recurringExpenseId, LocalDate dueDate) {
        recurringExpenseRepository.findById(recurringExpenseId).ifPresent(recurringExpense -> {
            if (!recurringExpenseSkipRepository.existsByRecurringExpenseIdAndDueDate(recurringExpenseId, dueDate)) {
                recurringExpenseSkipRepository.save(RecurringExpenseSkip.of(recurringExpense, dueDate));
            }
        });
    }

    private void applyReferences(RecurringExpense recurringExpense, RecurringExpenseCommand command) {
        var ownerId = recurringExpense.getUser().getId();

        recurringExpense.setCategory(command.categoryId() != null
            ? categoryService.getByIdAndUser(command.categoryId(), ownerId)
            : categoryService.getOrCreateByName(ownerId, command.categoryName()));
        recurringExpense.setCardAccount(command.cardAccountId() != null
            ? cardAccountService.getByIdAndUser(command.cardAccountId(), ownerId)
            : null);
    }

}
