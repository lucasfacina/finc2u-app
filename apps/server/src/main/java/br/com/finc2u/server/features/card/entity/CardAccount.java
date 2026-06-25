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

}
