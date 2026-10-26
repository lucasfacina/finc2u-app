package br.com.finc2u.server.features.card.entity;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
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

    // LAZY: só o CardAccountResponse lê o nome do dono; as consultas que o montam trazem o usuário por @EntityGraph.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
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
     * Ao cadastrar ou mudar a validade, ela não pode estar num mês passado (o mês corrente vale). A data de referência, que é hoje,
     * chega pronta: a entidade não lê o relógio. Sem validade, não há o que conferir.
     */
    public void assertExpirationNotInThePast(LocalDate referenceDate) {
        if (expirationDate != null && expirationDate.isBefore(referenceDate))
            throw new InvalidRequestException("A validade do cartão não pode estar no passado.");
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

        var closingMonth = YearMonth.from(purchaseDate);
        if (purchaseDate.getDayOfMonth() > closingDay) {
            closingMonth = closingMonth.plusMonths(1);
        }

        var dueMonth = dueDay < closingDay
            ? closingMonth.plusMonths(1)
            : closingMonth;

        var day = Math.min(dueDay, dueMonth.lengthOfMonth());
        return dueMonth.atDay(day);
    }

}
