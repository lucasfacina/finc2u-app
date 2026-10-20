package br.com.finc2u.server.features.category.entity;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "tb_category",
    uniqueConstraints = @UniqueConstraint(name = "uk_category_user_name", columnNames = {"user_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseModel {

    private String name;

    // LAZY: nenhum Response lê o dono da categoria; evita uma consulta extra a cada listagem.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    public static Category of(String name) {
        return Category.builder()
            .name(name)
            .build();
    }

}
