package br.com.finc2u.server.features.summary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class MonthlySummaryId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "summary_month")
    private Integer month;

    @Column(name = "summary_year")
    private Integer year;

}
