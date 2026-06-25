package br.com.finc2u.server.features.tag.entity;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.shared.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "tb_tag")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag extends BaseModel {

    private String name;
    private Long colorCode;

    @JsonIgnore
    @ManyToMany(mappedBy = "tags")
    List<Expense> expenseList;

}
