package br.com.finc2u.server.features.salaryhistory.vo;

import java.math.BigDecimal;
import java.util.UUID;

public record CurrentSalary(UUID userId, BigDecimal baseSalary) {

}
