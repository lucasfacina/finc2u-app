package br.com.finc2u.server.features.card.entity;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Entity
@Table(
    name = "tb_card_account",
    uniqueConstraints = @UniqueConstraint(name = "uk_card_user_name", columnNames = {"user_id", "card_name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardAccount extends BaseModel {

    private String cardName;
    private Integer closingDate;
    private Integer dueDate;

    // Último dia do mês de validade do cartão; nulo quando a validade não é informada.
    private LocalDate expirationDate;

    @ManyToOne(optional = false)
    private User user;

    // A validade informada por mês/ano vira o último dia desse mês; o CardAccountService associa o usuário.
    public static CardAccount of(String cardName, Integer closingDate, Integer dueDate, YearMonth expiration) {
        return CardAccount.builder()
            .cardName(cardName)
            .closingDate(closingDate)
            .dueDate(dueDate)
            .expirationDate(expiration != null
                ? expiration.atEndOfMonth()
                : null)
            .build();
    }

    /*
     * Cartão com validade só aceita compras até o último dia do mês de validade; sem validade, aceita sempre.
     */
    public void assertUsableOn(LocalDate purchaseDate) {
        if (expirationDate != null && purchaseDate.isAfter(expirationDate)) {
            throw new BusinessException(
                "Cartão expirado em " + YearMonth.from(expirationDate).format(DateTimeFormatter.ofPattern("MM/yyyy")) + "."
            );
        }
    }

    /*
     * Determina o vencimento da fatura em que a compra cai.
     * Até o dia de fechamento, a compra entra na fatura que fecha no mês corrente; depois, na do mês seguinte.
     * Se o dia de vencimento é anterior ao de fechamento, a fatura vence no mês seguinte ao fechamento
     * (ex.: cartão fecha dia 30 e vence dia 15).
     */
    public LocalDate calculateInvoiceDueDate(LocalDate purchaseDate) {
        int closingDay = this.closingDate;
        int dueDay = this.dueDate;

        YearMonth closingMonth = YearMonth.from(purchaseDate);
        if (purchaseDate.getDayOfMonth() > closingDay) {
            closingMonth = closingMonth.plusMonths(1);
        }

        YearMonth dueMonth = dueDay < closingDay
            ? closingMonth.plusMonths(1)
            : closingMonth;

        int day = Math.min(dueDay, dueMonth.lengthOfMonth());
        return dueMonth.atDay(day);
    }

}
