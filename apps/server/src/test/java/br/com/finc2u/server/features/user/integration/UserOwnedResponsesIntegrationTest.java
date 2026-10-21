package br.com.finc2u.server.features.user.integration;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.form.CardAccountResponse;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.form.ExtraIncomeResponse;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.salaryhistory.form.SalaryHistoryResponse;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.summary.form.MonthlySummaryResponse;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * O dono (User) das entidades de cartão, ganho extra, salário e resumo é LAZY e o open-in-view está desligado. Estes
 * testes montam o Response como o controller faz — DEPOIS que o service devolveu, sem transação aberta — e provam que nada
 * dispara LazyInitializationException: o CardAccountResponse lê o NOME do dono (o @EntityGraph o traz) e os demais leem só
 * o id (o id de um proxy não precisa de consulta).
 */
@SpringBootTest
class UserOwnedResponsesIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CardAccountService cardAccountService;

    @Autowired
    private ExtraIncomeService extraIncomeService;

    @Autowired
    private SalaryHistoryService salaryHistoryService;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @Test
    void cardAccountResponse_shouldReadTheOwnerName_afterGetById() {
        var userId = registerUser("dono.cartao.id@email.com");
        var created = cardAccountService.create(CardAccount.of("Nubank", 9, 15, null), userId);

        var response = CardAccountResponse.from(cardAccountService.getById(created.getId()));

        assertEquals("Ana", response.userName());
    }

    @Test
    void cardAccountResponse_shouldReadTheOwnerName_inTheListing() {
        var userId = registerUser("dono.cartao.lista@email.com");
        cardAccountService.create(CardAccount.of("Nubank", 9, 15, null), userId);
        cardAccountService.create(CardAccount.of("Mercado Pago", 9, 14, null), userId);

        var responses = cardAccountService.getByUser(userId, null).stream()
            .map(CardAccountResponse::from)
            .toList();

        assertEquals(2, responses.size());
        assertTrue(responses.stream().allMatch(response -> "Ana".equals(response.userName())));
    }

    @Test
    void cardAccountResponse_shouldReadTheOwnerName_afterUpdate() {
        var userId = registerUser("dono.cartao.edicao@email.com");
        var created = cardAccountService.create(CardAccount.of("Nubank", 9, 15, null), userId);

        var updated = cardAccountService.update(created.getId(), CardAccount.of("Nubank Ultravioleta", 10, 16, null));

        assertEquals("Ana", CardAccountResponse.from(updated).userName());
    }

    @Test
    void extraIncomeResponse_shouldReadTheOwnerId_afterGetById() {
        var userId = registerUser("dono.extra@email.com");
        var created = extraIncomeService.create(ExtraIncome.builder()
            .description("Freela")
            .value(BigDecimal.TEN)
            .date(LocalDate.of(2026, 10, 5))
            .build(), userId);

        var response = ExtraIncomeResponse.from(extraIncomeService.getById(created.getId()));

        assertEquals(userId, response.userId());
    }

    @Test
    void salaryHistoryResponse_shouldReadTheOwnerId_inTheListing() {
        var userId = registerUser("dono.salario@email.com");

        var responses = salaryHistoryService.getByUser(userId).stream()
            .map(SalaryHistoryResponse::from)
            .toList();

        assertEquals(1, responses.size());
        assertEquals(userId, responses.getFirst().userId());
    }

    @Test
    void monthlySummaryResponse_shouldReadTheOwnerId_afterCalculateAndInTheHistory() {
        var userId = registerUser("dono.resumo@email.com");
        var period = YearMonth.of(2026, 10);

        var calculated = monthlySummaryService.calculateOrRecalculate(userId, period.getMonthValue(), period.getYear()).summary();
        var history = monthlySummaryService.getByUser(userId);

        assertEquals(userId, MonthlySummaryResponse.from(calculated, List.of()).userId());
        assertEquals(1, history.size());
        assertEquals(userId, MonthlySummaryResponse.from(history.getFirst().summary(), history.getFirst().categoryTotals()).userId());
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

}
