package br.com.finc2u.server.features.expense.entity;

import br.com.finc2u.server.config.BusinessConstants;
import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    @ManyToMany
    @JoinTable(
            name = "tb_expense_tag",
            joinColumns = @JoinColumn(name = "expense_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags;

    /*
     * Resolve a data de vencimento da despesa:
     * - Se dueDate já foi informado, ele prevalece (override manual).
     * - Com cartão e sem dueDate: calcula a partir de purchaseDate + fechamento/vencimento do cartão.
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

        LocalDate purchase = purchaseDate != null
                ? purchaseDate
                : LocalDate.now();
        this.dueDate = cardAccount.calculateInvoiceDueDate(purchase);
    }

    /*
     * Define o status de pagamento por padrão quando não informado:
     * vencimento até hoje conta como PAID (assume pagamento em dia); vencimento futuro fica PENDING.
     */
    public void resolveDefaultPaymentStatus() {
        if (paymentStatus == null) {
            paymentStatus = !dueDate.isAfter(LocalDate.now())
                    ? PaymentStatus.PAID
                    : PaymentStatus.PENDING;
        }
    }

    /* Valida regras de PARCELED: mínimo de parcelas, parcela inicial padrão e auto-PAID na última. */
    public void applyInstallmentRules() {
        if (expenseType == ExpenseType.PARCELED) {
            if (totalInstallment == null || totalInstallment < BusinessConstants.MIN_INSTALLMENTS) {
                throw new BusinessException("Total de parcelas deve ser pelo menos 1 para despesas parceladas");
            }
            if (currentInstallment == null) {
                currentInstallment = BusinessConstants.DEFAULT_FIRST_INSTALLMENT;
            }
            if (currentInstallment.equals(totalInstallment)) {
                paymentStatus = PaymentStatus.PAID;
            }
        }
    }

    /*
     * Normaliza o estado derivado da despesa antes de persistir, na ordem correta:
     * resolve o vencimento, define o status padrão (que depende do vencimento) e aplica
     * as regras de parcelamento.
     */
    public void prepareForPersistence(LocalDate purchaseDate) {
        resolveDueDate(purchaseDate);
        resolveDefaultPaymentStatus();
        applyInstallmentRules();
    }

}
