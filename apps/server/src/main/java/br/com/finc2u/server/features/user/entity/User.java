package br.com.finc2u.server.features.user.entity;

import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "tb_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseModel {

    private String name;
    private String email;

    @OneToOne(cascade = CascadeType.ALL)
    private UserConfiguration userConfiguration;

}
