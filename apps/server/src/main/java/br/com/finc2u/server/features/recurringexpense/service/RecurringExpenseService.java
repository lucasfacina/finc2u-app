package br.com.finc2u.server.features.recurringexpense.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final CardAccountService cardAccountService;
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

    @Transactional
    public void delete(UUID id) {
        var recurringExpense = getById(id);
        recurringExpenseRepository.delete(recurringExpense);
    }

    private void applyReferences(RecurringExpense recurringExpense, RecurringExpenseCommand command) {
        var ownerId = recurringExpense.getUser().getId();
        recurringExpense.setCategory(categoryService.getByIdAndUser(command.categoryId(), ownerId));
        recurringExpense.setCardAccount(command.cardAccountId() != null
            ? cardAccountService.getByIdAndUser(command.cardAccountId(), ownerId)
            : null);
    }

}
