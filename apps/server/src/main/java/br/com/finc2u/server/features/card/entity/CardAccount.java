package br.com.finc2u.server.features.card.entity;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

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

}
