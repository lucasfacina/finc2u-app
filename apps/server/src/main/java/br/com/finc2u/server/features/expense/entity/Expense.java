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

@Entity
@Table(name = "tb_expense")
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

    @Column(name = "expense_value")
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    private ExpenseType expenseType;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @ManyToOne(optional = false)
    private User user;

    @ManyToOne
    private CardAccount cardAccount;

    // LAZY: só as consultas que montam o ExpenseResponse carregam a categoria (via @EntityGraph); o resumo mensal não a lê.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Category category;

    /*
     * Resolve a data de vencimento da despesa:
     * - Se dueDate já foi informado, ele prevalece (override manual).
     * - Com cartão e sem dueDate: calcula a partir de purchaseDate + fechamento/vencimento do cartão. A data de compra
     *   chega já resolvida (o service usa "hoje" quando o cliente não informa): a entidade não lê o relógio.
     * - Sem cartão e sem dueDate: erro (despesa avulsa exige vencimento manual).
     */
    public void resolveDueDate(LocalDate purchaseDate) {
        if (dueDate != null) {
            return;
        }

        if (cardAccount == null) {
            throw new BusinessException("Data de vencimento é obrigatória para despesas sem cartão.");
        }

        if (cardAccount.getClosingDate() == null || cardAccount.getDueDate() == null) {
            throw new BusinessException("Cartão não possui datas de fechamento/vencimento configuradas.");
        }

        this.dueDate = cardAccount.calculateInvoiceDueDate(purchaseDate);
    }

    /*
     * Define o status de pagamento por padrão quando não informado: PENDING. Se o vencimento já passou (anterior à data
     * de referência, que é hoje), o sistema não adivinha: o cliente precisa dizer se a despesa já foi paga (D13).
     */
    public void resolveDefaultPaymentStatus(LocalDate referenceDate) {
        if (paymentStatus != null) {
            return;
        }

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
        if (expenseType == ExpenseType.PARCELED) {
            if (totalInstallment == null || totalInstallment < BusinessConstants.MIN_INSTALLMENTS) {
                throw new BusinessException("Total de parcelas deve ser pelo menos 1 para despesas parceladas");
            }
            if (totalInstallment > BusinessConstants.MAX_INSTALLMENTS) {
                throw new BusinessException(
                    "Total de parcelas (" + totalInstallment + ") não pode ser maior que " + BusinessConstants.MAX_INSTALLMENTS + "."
                );
            }
            if (currentInstallment == null) {
                currentInstallment = BusinessConstants.DEFAULT_FIRST_INSTALLMENT;
            }
            if (currentInstallment < BusinessConstants.DEFAULT_FIRST_INSTALLMENT) {
                throw new BusinessException("Parcela atual (" + currentInstallment + ") deve ser pelo menos 1.");
            }
            if (currentInstallment > totalInstallment) {
                throw new BusinessException(
                    "Parcela atual (" + currentInstallment + ") não pode ser maior que o total (" + totalInstallment + ")."
                );
            }
        }
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
        if (paidAt == null) {
            return;
        }
        if (status != PaymentStatus.PAID) {
            throw new BusinessException("A data do pagamento só pode ser informada para despesa paga.");
        }
        if (paidAt.isAfter(referenceDate)) {
            throw new BusinessException("A data do pagamento não pode ser posterior a hoje.");
        }
    }

    /*
     * Atraso derivado: PENDING com vencimento anterior à data de referência. Não é coluna nem status: nenhum job
     * muda a despesa, e a mesma data de referência produz sempre a mesma resposta.
     */
    public boolean isOverdue(LocalDate referenceDate) {
        return paymentStatus == PaymentStatus.PENDING && dueDate.isBefore(referenceDate);
    }

}
