package br.com.finc2u.server.features.expense.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.mapper.ExpenseMapper;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.specification.ExpenseSpecification;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CardAccountService cardAccountService;
    private final UserService userService;
    private final ExpenseMapper expenseMapper;

    @Transactional
    public Expense create(UUID userId, ExpenseCommand command) {
        var expense = command.expense();
        expense.setUser(userService.getById(userId));

        expenseMapper.applyCardAccount(expense, command.cardAccountId());
        expenseMapper.applyCategory(expense, command.categoryId());

        expense.prepareForPersistence(command.purchaseDate());

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

    @Transactional(readOnly = true)
    public List<Expense> getWithFilters(UUID userId, ExpenseFilter filter) {
        userService.getById(userId);
        return expenseRepository.findAll(ExpenseSpecification.ofFilter(userId, filter));
    }

    @Transactional
    public Expense update(UUID id, ExpenseCommand command) {
        var expense = getById(id);

        expenseMapper.applyCardAccount(expense, command.cardAccountId());
        expenseMapper.applyCategory(expense, command.categoryId());
        expenseMapper.applyEditableFields(expense, command.expense());

        expense.prepareForPersistence(command.purchaseDate());

        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense updateStatus(UUID id, PaymentStatus status) {
        Expense expense = getById(id);
        expense.setPaymentStatus(status);

        return expenseRepository.save(expense);
    }

    @Transactional
    public void delete(UUID id) {
        Expense expense = getById(id);
        expenseRepository.delete(expense);
    }

}
