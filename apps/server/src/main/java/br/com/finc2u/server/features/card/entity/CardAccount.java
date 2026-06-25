package br.com.finc2u.server.features.card.entity;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Entity
@Table(name = "tb_card_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardAccount extends BaseModel {

    private String cardName;
    private Integer closingDate;
    private Integer dueDate;

    @ManyToOne
    private User user;

    @JsonIgnore
    @OneToMany(mappedBy = "cardAccount")
    private List<Expense> expenseList;


    /*
     * Determina o vencimento da fatura em que a compra cai.
     * Até o dia de fechamento, a compra entra na fatura que fecha no mês corrente; depois, na do mês seguinte.
     * Se o dia de vencimento é anterior ao de fechamento, a fatura vence no mês seguinte ao fechamento
     * (ex.: cartão fecha dia 30 e vence dia 15).
     */
    public LocalDate calculateInvoiceDueDate(LocalDate pucrchaseDate) {
        int closingDay = this.closingDate;
        int dueDay = this.dueDate;

        YearMonth closingMonth = YearMonth.from(pucrchaseDate);
        if (pucrchaseDate.getDayOfMonth() > closingDay) {
            closingMonth = closingMonth.plusMonths(1);
        }

        YearMonth dueMonth = dueDay < closingDay
                ? closingMonth.plusMonths(1)
                : closingMonth;

        int day = Math.min(dueDay, dueMonth.lengthOfMonth());
        return dueMonth.atDay(day);
    }

}
