package br.com.finc2u.server.features.user.entity;

import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name="tb_user_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserConfiguration extends BaseModel {

    private BigDecimal baseSalary;
    private BigDecimal savingsBalance;

}
