package br.com.finc2u.server.features.summary.entity;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/*
 * Total gasto numa categoria dentro de um resumo mensal. É um snapshot gravado no cálculo (como o resto do resumo), para
 * quem consome o resumo não precisar refazer a soma. Fica numa tabela própria, sem coleção inversa na MonthlySummary.
 */
@Entity
@Table(
    name = "tb_monthly_summary_category",
    uniqueConstraints = @UniqueConstraint(name = "uk_summary_category", columnNames = {"summary_id", "category_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySummaryCategoryTotal extends BaseModel {

    @Column(name = "category_total")
    private BigDecimal total;

    // LAZY: a resposta lê só o id do resumo; a categoria vem por @EntityGraph nas consultas que montam o Response.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MonthlySummary summary;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Category category;

    public static MonthlySummaryCategoryTotal of(MonthlySummary summary, Category category, BigDecimal total) {
        return MonthlySummaryCategoryTotal.builder()
            .summary(summary)
            .category(category)
            .total(total)
            .build();
    }

}
