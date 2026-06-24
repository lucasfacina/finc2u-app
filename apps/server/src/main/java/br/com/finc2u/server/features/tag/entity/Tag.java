package br.com.finc2u.server.features.tag.entity;

import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

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

}
