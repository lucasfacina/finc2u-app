package br.com.finc2u.server.features.tag.entity;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "tb_tag",
    uniqueConstraints = @UniqueConstraint(name = "uk_tag_user_name", columnNames = {"user_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag extends BaseModel {

    private String name;

    // LAZY: nenhum Response lê o dono da tag; evita uma consulta extra a cada listagem de tags.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    public static Tag of(String name) {
        return Tag.builder()
            .name(name)
            .build();
    }

}
