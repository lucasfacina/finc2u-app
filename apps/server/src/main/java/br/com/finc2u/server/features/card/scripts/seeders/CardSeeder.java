package br.com.finc2u.server.features.card.scripts.seeders;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.repository.CardAccountRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CardSeeder implements Seeder {

    private final CardAccountRepository cardAccountRepository;

    @Override
    public int order() {
        return 2;
    }

    @Override
    public void seed(SeedContext context) {
        var user = context.getUser();

        // Encontrar ou criar por item: cartão ausente é criado mesmo que os demais já existam (context.card nunca devolve null).
        var existingCards = cardAccountRepository.findByUserId(user.getId());
        existingCards.forEach(context::putCard);

        var existingNames = existingCards.stream().map(CardAccount::getCardName).toList();
        var missingCards = seedCards(user).stream()
            .filter(card -> !existingNames.contains(card.getCardName()))
            .toList();

        cardAccountRepository.saveAll(missingCards).forEach(context::putCard);
    }

    private List<CardAccount> seedCards(User user) {
        var cards = List.of(
            CardAccount.of("Nubank", 9, 15, YearMonth.of(2030, 12)),
            CardAccount.of("Mercado Pago", 9, 14, null)
        );
        cards.forEach(card -> card.setUser(user));

        return cards;
    }

}
