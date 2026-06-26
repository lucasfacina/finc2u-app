package br.com.finc2u.server.features.extra.entity;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tb_extra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Extra extends BaseModel {

    private String description;

    @Column(name = "extra_value")
    private BigDecimal value;

    @Column(name = "received_date")
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

}
