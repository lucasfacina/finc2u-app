package br.com.finc2u.server.features.user.entity;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.shared.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    List<CardAccount> cardAccountList;

}
