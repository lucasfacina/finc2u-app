package br.com.finc2u.server.features.user.entity;

import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserConfiguration userConfiguration;

    public void setUserConfiguration(UserConfiguration userConfiguration) {
        this.userConfiguration = userConfiguration;
        if (userConfiguration != null) {
            userConfiguration.setUser(this);
        }
    }

    // Salário-base configurado ou zero quando não há configuração/valor
    public BigDecimal baseSalaryOrZero() {
        return userConfiguration != null && userConfiguration.getBaseSalary() != null
            ? userConfiguration.getBaseSalary()
            : BigDecimal.ZERO;
    }

    // Saldo de poupança configurado ou zero quando não há configuração/valor.
    public BigDecimal savingsBalanceOrZero() {
        return userConfiguration != null && userConfiguration.getSavingsBalance() != null
            ? userConfiguration.getSavingsBalance()
            : BigDecimal.ZERO;
    }

}
