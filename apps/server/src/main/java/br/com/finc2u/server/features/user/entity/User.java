package br.com.finc2u.server.features.user.entity;

import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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

    private BigDecimal savingsBalance;

    public BigDecimal savingsBalanceOrZero() {
        return savingsBalance != null
            ? savingsBalance
            : BigDecimal.ZERO;
    }

}
