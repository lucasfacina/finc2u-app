package br.com.finc2u.server.features.user.event;

import br.com.finc2u.server.features.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

/*
 * Publicado pelo `UserService` quando um usuário é cadastrado. Cada feature que precisa preparar dados iniciais do
 * usuário (primeira vigência de salário, categorias padrão) reage a ele, sem que o `UserService` as conheça.
 */
public record UserRegisteredEvent(
    User user,
    BigDecimal baseSalary,
    LocalDate salaryEffectiveFrom
) {

}
