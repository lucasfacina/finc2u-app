package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.category.entity.Category;

import java.math.BigDecimal;

// Quanto o usuário gastou numa categoria no mês (pago + pendente): é a base do orçamento por categoria.
public record CategoryTotal(Category category, BigDecimal total) {
}
