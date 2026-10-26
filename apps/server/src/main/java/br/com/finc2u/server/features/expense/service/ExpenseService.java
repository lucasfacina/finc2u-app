package br.com.finc2u.server.features.expense.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.event.GeneratedExpenseDeletedEvent;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.mapper.ExpenseMapper;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.specification.ExpenseSpecification;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.expense.vo.InstallmentSchedule;
import br.com.finc2u.server.features.expense.vo.RecurrenceExpenseCommand;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida das despesas do usuário: criar, ler, alterar, quitar e apagar, inclusive a compra parcelada (uma linha por
 * parcela) e as despesas geradas por um modelo recorrente.
 * <p>
 * As regras do próprio estado da despesa ficam na entidade ({@link Expense#prepareForPersistence}) e a montagem a partir do
 * request, no {@link ExpenseMapper}. Aqui entram as regras que cruzam entidades (usuário, cartão, categoria) e a data de
 * "hoje", sempre lida do {@link Clock} e passada por parâmetro.
 * <p>
 * {@link #create}, {@link #update}, {@link #updateStatus}, {@link #delete} e {@link #deleteInstallmentGroup} publicam o
 * {@link PeriodDataChangedEvent} com o mês mais antigo afetado, para o resumo mensal se manter em dia. {@link #createFromRecurrence}
 * e {@link #detachFromRecurringExpense} não publicam.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserService userService;
    private final ExpenseMapper expenseMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Cria a despesa do usuário. Cartão e categoria vêm do comando por id ou por nome (a categoria por nome é criada se ainda não
     * existe). Sem vencimento informado, ele sai da fatura do cartão; sem status, a despesa nasce {@code PENDING} (se o vencimento
     * for anterior a hoje, o status passa a ser obrigatório). Uma compra
     * {@code PARCELED} grava as parcelas {@code k..N}, ligadas por um grupo novo, e devolve só a informada.
     *
     * @param userId  dono da despesa
     * @param command despesa transitória com as referências e a data da compra (padrão: hoje)
     * @return a despesa gravada (no parcelamento, a parcela informada)
     * @throws ResourceNotFoundException usuário, cartão ou categoria inexistente (ou de outro usuário)
     *                                   (lançada por {@code UserService#getById},
     *                                   {@code ExpenseMapper#applyCardAccount} e {@code ExpenseMapper#applyCategory})
     * @throws BusinessException         cartão expirado na data da compra ou regra da própria despesa violada (vencimento ausente
     *                                   sem cartão, parcelas inválidas, {@code paidAt} incoerente)
     * @throws InvalidRequestException   vencimento anterior a hoje sem informar o status de pagamento
     */
    @Transactional
    public Expense create(UUID userId, ExpenseCommand command) {
        var expense = command.expense();
        var today = LocalDate.now(clock);
        var purchaseDate = command.purchaseDate() != null
            ? command.purchaseDate()
            : today;

        expense.setUser(userService.getById(userId));
        expenseMapper.applyCardAccount(expense, command.cardAccountId(), command.cardName());
        expenseMapper.applyCategory(expense, command.categoryId(), command.categoryName());
        expense.assertCardUsableOn(purchaseDate);
        expense.prepareForPersistence(purchaseDate, today);

        var saved = expense.isParceled()
            ? saveInstallments(expense)
            : expenseRepository.save(expense);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(userId, expense.getDueDate()));

        return saved;
    }

    /**
     * Busca a despesa pelo id.
     *
     * @throws ResourceNotFoundException se não existir
     */
    @Transactional(readOnly = true)
    public Expense getById(UUID id) {
        return expenseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada com o ID: " + id));
    }

    /**
     * Todas as despesas do usuário, sem filtro.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    @Transactional(readOnly = true)
    public List<Expense> getByUser(UUID userId) {
        userService.getById(userId);
        return expenseRepository.findByUserId(userId);
    }

    /**
     * Despesas com vencimento entre as duas datas, inclusive. É a leitura que o resumo mensal usa, no lugar do repositório.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     * @throws InvalidRequestException   se a data inicial for posterior à final
     */
    @Transactional(readOnly = true)
    public List<Expense> getByUserAndPeriod(UUID userId, LocalDate start, LocalDate end) {
        if (start.isAfter(end))
            throw new InvalidRequestException("A data inicial não pode ser posterior à data final.");

        userService.getById(userId);
        return expenseRepository.findByUserIdAndDueDateBetween(userId, start, end);
    }

    /**
     * Despesas do usuário que atendem ao filtro, aplicado no banco por {@code Specification}. O atraso ({@code overdue}) é derivado
     * da data de hoje, lida do {@link Clock}.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    @Transactional(readOnly = true)
    public List<Expense> getWithFilters(UUID userId, ExpenseFilter filter) {
        userService.getById(userId);
        return expenseRepository.findAll(ExpenseSpecification.ofFilter(userId, filter, LocalDate.now(clock)));
    }

    /**
     * Altera a despesa: troca cartão e categoria (por id ou nome), copia os campos editáveis e normaliza de novo o vencimento, o
     * status e as parcelas. A validade do cartão só é conferida quando o comando traz a data da compra. O resumo é avisado a
     * partir do mês mais antigo entre o vencimento antigo e o novo.
     *
     * @throws ResourceNotFoundException despesa, cartão ou categoria inexistente
     *                                   (lançada por {@code ExpenseMapper#applyCardAccount},
     *                                   {@code ExpenseMapper#applyCategory} e {@code ExpenseService#getById})
     * @throws BusinessException         cartão expirado ou regra da própria despesa violada
     * @throws InvalidRequestException   vencimento anterior a hoje sem informar o status de pagamento
     */
    @Transactional
    public Expense update(UUID id, ExpenseCommand command) {
        var expense = getById(id);
        var previousDueDate = expense.getDueDate();
        var today = LocalDate.now(clock);
        var purchaseDate = command.purchaseDate() != null
            ? command.purchaseDate()
            : today;

        expenseMapper.applyCardAccount(expense, command.cardAccountId(), command.cardName());
        expenseMapper.applyCategory(expense, command.categoryId(), command.categoryName());
        expenseMapper.applyEditableFields(expense, command.expense());
        if (command.purchaseDate() != null) expense.assertCardUsableOn(purchaseDate);
        expense.prepareForPersistence(purchaseDate, today);

        var saved = expenseRepository.save(expense);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(expense.getUser().getId(), previousDueDate, expense.getDueDate()));

        return saved;
    }

    /**
     * Muda o status de pagamento. {@code PAID} sem data usa hoje; {@code PENDING} limpa a data. O atraso nunca é gravado: é derivado.
     *
     * @param paidAt data do pagamento (só com {@code PAID} e nunca no futuro)
     * @throws ResourceNotFoundException se a despesa não existir (lançada por {@code ExpenseService#getById})
     * @throws BusinessException         se {@code paidAt} vier com {@code PENDING} ou for posterior a hoje
     */
    @Transactional
    public Expense updateStatus(UUID id, PaymentStatus status, LocalDate paidAt) {
        var expense = getById(id);
        expense.changePaymentStatus(status, paidAt, LocalDate.now(clock));

        var saved = expenseRepository.save(expense);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(expense.getUser().getId(), expense.getDueDate()));
        return saved;
    }

    /**
     * Apaga uma despesa (uma parcela, se for parcelada). Se ela foi gerada por um modelo recorrente, publica também o
     * {@link GeneratedExpenseDeletedEvent}: o modelo passa a pular esse vencimento e não a recria.
     *
     * @throws ResourceNotFoundException se a despesa não existir (lançada por {@code ExpenseService#getById})
     */
    @Transactional
    public void delete(UUID id) {
        var expense = getById(id);

        expenseRepository.delete(expense);

        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(expense.getUser().getId(), expense.getDueDate()));
        if (expense.getRecurringExpenseId() != null) {
            eventPublisher.publishEvent(new GeneratedExpenseDeletedEvent(expense.getRecurringExpenseId(), expense.getDueDate()));
        }
    }

    /**
     * Todas as parcelas de uma compra, da menor para a maior.
     *
     * @throws ResourceNotFoundException se não houver compra com o grupo informado
     */
    @Transactional(readOnly = true)
    public List<Expense> getByInstallmentGroup(UUID groupId) {
        var installments = expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId);
        if (installments.isEmpty())
            throw new ResourceNotFoundException("Compra parcelada não encontrada com o grupo: " + groupId);

        return installments;
    }

    /**
     * Remove a compra parcelada inteira; {@link #delete} continua removendo uma parcela só.
     *
     * @throws ResourceNotFoundException se não houver compra com o grupo informado
     *                                   (lançada por {@code ExpenseService#getByInstallmentGroup})
     */
    @Transactional
    public void deleteInstallmentGroup(UUID groupId) {
        var installments = getByInstallmentGroup(groupId);
        expenseRepository.deleteAll(installments);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(installments.getFirst().getUser().getId(),
            installments.stream().map(Expense::getDueDate).toList()));
    }

    /**
     * Cria a despesa do modelo recorrente no vencimento: {@code FIXED} e {@code PENDING}, com os dados do modelo. Idempotente: se o
     * modelo já tem despesa nesse mês, não cria nada, mesmo que o vencimento dela tenha sido editado. Grava também o mês do modelo em que ela foi gerada ({@code recurrenceMonth},
     * o dia 1 dele). O cartão não é revalidado (a validade vale na data da compra). Não
     * publica o {@code PeriodDataChangedEvent}: o aviso é de quem chama (o {@code RecurringExpenseMaterializer}), que dentro do
     * cálculo do resumo não avisa, porque seria recursão.
     *
     * @return a despesa criada, ou vazio se o mês já tinha uma
     */
    @Transactional
    public Optional<Expense> createFromRecurrence(RecurrenceExpenseCommand command) {
        var recurrenceMonth = command.recurrenceMonth().atDay(1);
        if (expenseRepository.existsByRecurringExpenseIdAndRecurrenceMonth(command.recurringExpenseId(), recurrenceMonth))
            return Optional.empty();

        return Optional.of(expenseRepository.save(Expense.builder()
            .description(command.description())
            .value(command.value())
            .dueDate(command.dueDate())
            .expenseType(ExpenseType.FIXED)
            .paymentStatus(PaymentStatus.PENDING)
            .user(command.user())
            .cardAccount(command.cardAccount())
            .category(command.category())
            .recurringExpenseId(command.recurringExpenseId())
            .recurrenceMonth(recurrenceMonth)
            .build()));
    }

    /**
     * Solta do modelo as despesas que ele gerou (o histórico fica). Chamado antes de apagar o modelo recorrente.
     */
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

}
