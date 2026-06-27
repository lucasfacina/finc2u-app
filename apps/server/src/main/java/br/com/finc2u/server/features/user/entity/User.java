package br.com.finc2u.server.features.user.entity;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.shared.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "tb_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseModel {

    private String name;

    @Column(unique = true)
    private String email;

    @OneToOne(cascade = CascadeType.ALL)
    private UserConfiguration userConfiguration;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<CardAccount> cardAccountList;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Extra> extraList;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<MonthlySummary> monthlySummaryList;

    /**
     * Salário-base configurado ou zero quando não há configuração/valor
     */
    public BigDecimal baseSalaryOrZero() {
        return userConfiguration != null && userConfiguration.getBaseSalary() != null
                ? userConfiguration.getBaseSalary()
                : BigDecimal.ZERO;
    }

    /**
     * Saldo de poupança configurado ou zero quando não há configuração/valor.
     */
    public BigDecimal savingsBalanceOrZero() {
        return userConfiguration != null && userConfiguration.getSavingsBalance() != null
                ? userConfiguration.getSavingsBalance()
                : BigDecimal.ZERO;
    }

}
