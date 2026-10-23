package br.com.finc2u.server.features.recurringexpense.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpenseSkip;
import br.com.finc2u.server.features.recurringexpense.mapper.RecurringExpenseMapper;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseSkipRepository;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.text.TextOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Modelos de despesa mensal (assinaturas, contas que se repetem). O modelo guarda a regra; as despesas reais, uma por mês, são
 * geradas pelo {@link RecurringExpenseMaterializer}. Categoria e cartão do modelo precisam ser do dono (os de outro usuário são
 * tratados como inexistentes); quem os resolve é o {@link RecurringExpenseMapper}.
 */
@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseSkipRepository recurringExpenseSkipRepository;
    private final UserService userService;
    private final ExpenseService expenseService;
    private final RecurringExpenseMapper recurringExpenseMapper;
    private final RecurringExpenseMaterializer recurringExpenseMaterializer;

    /**
     * Cadastra o modelo e já gera as despesas do mês corrente e do seguinte (a assinatura aparece sem esperar o job). Não gera mês
     * passado nem vencimento que já passou.
     *
     * @throws ResourceNotFoundException usuário, categoria ou cartão inexistente (ou de outro usuário)
     *                                   (lançada por {@code UserService#getById} e {@code RecurringExpenseMapper#applyReferences})
     */
    @Transactional
    public RecurringExpense create(RecurringExpenseCommand command, UUID userId) {
        var recurringExpense = command.recurringExpense();

        recurringExpense.setUser(userService.getById(userId));
        recurringExpenseMapper.applyReferences(recurringExpense, command);

        var saved = recurringExpenseRepository.save(recurringExpense);
        recurringExpenseMaterializer.materializeUpcoming(saved);

        return saved;
    }

    /**
     * Busca o modelo pelo id.
     *
     * @throws ResourceNotFoundException se não existir
     */
    @Transactional(readOnly = true)
    public RecurringExpense getById(UUID id) {
        return recurringExpenseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Despesa recorrente não encontrada com o ID: " + id));
    }

    /**
     * Modelos do usuário, ordenados pela descrição na ordem alfabética do português ({@code TextOrder}).
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    @Transactional(readOnly = true)
    public List<RecurringExpense> getByUser(UUID userId) {
        userService.getById(userId);
        return recurringExpenseRepository.findByUserId(userId).stream()
            .sorted(TextOrder.by(RecurringExpense::getDescription))
            .toList();
    }

    /**
     * Modelos que ainda geram despesa a partir do mês (sem término, ou com término neste mês ou depois), <b>de todos os usuários</b>. É a
     * exceção à consulta escopada por usuário: só o job mensal a usa.
     */
    @Transactional(readOnly = true)
    public List<RecurringExpense> getActiveIn(YearMonth month) {
        return recurringExpenseRepository.findByEndMonthIsNullOrEndMonthGreaterThanEqual(month.atDay(1));
    }

    /**
     * Altera o modelo. Vale daqui para frente: as despesas já geradas não mudam.
     *
     * @throws ResourceNotFoundException modelo, categoria ou cartão inexistente
     *                                   (lançada por {@code RecurringExpenseService#getById} e {@code RecurringExpenseMapper#applyReferences})
     */
    @Transactional
    public RecurringExpense update(UUID id, RecurringExpenseCommand command) {
        var recurringExpense = getById(id);

        recurringExpenseMapper.applyEditableFields(recurringExpense, command.recurringExpense());
        recurringExpenseMapper.applyReferences(recurringExpense, command);

        return recurringExpenseRepository.save(recurringExpense);
    }

    /**
     * Apaga o modelo. As despesas já geradas ficam, sem modelo: apagar não apaga o histórico (para parar de gerar, use o
     * {@code endMonth}). Os pulos do modelo saem junto, antes dele, por causa da chave estrangeira.
     *
     * @throws ResourceNotFoundException se não existir (lançada por {@code RecurringExpenseService#getById})
     */
    @Transactional
    public void delete(UUID id) {
        var recurringExpense = getById(id);
        expenseService.detachFromRecurringExpense(id);
        recurringExpenseSkipRepository.deleteByRecurringExpenseId(id);
        recurringExpenseRepository.delete(recurringExpense);
    }

    /**
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

}
