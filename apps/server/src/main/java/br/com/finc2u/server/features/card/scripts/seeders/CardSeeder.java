package br.com.finc2u.server.features.card.scripts.seeders;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.repository.CardAccountRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        User user = context.getUser();

        List<CardAccount> existingCards = cardAccountRepository.findByUserId(user.getId());
        if (!existingCards.isEmpty()) {
            existingCards.forEach(context::putCard);
            return;
        }

        createSeedCards(user).forEach(context::putCard);
    }

    private List<CardAccount> createSeedCards(User user) {
        List<CardAccount> cards = List.of(
                CardAccount.builder()
                        .cardName("Nubank")
                        .closingDate(9)
                        .dueDate(15)
                        .user(user)
                        .build(),
                CardAccount.builder()
                        .cardName("Mercado Pago")
                        .closingDate(9)
                        .dueDate(14)
                        .user(user)
                        .build()
        );

        return cardAccountRepository.saveAll(cards);
    }

}
