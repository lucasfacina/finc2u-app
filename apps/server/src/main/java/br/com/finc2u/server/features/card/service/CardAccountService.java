package br.com.finc2u.server.features.card.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.repository.CardAccountRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardAccountService {

    private final CardAccountRepository cardAccountRepository;
    private final UserService userService;

    @Transactional
    public CardAccount create(CardAccount cardAccount, UUID userId) {
        User user = userService.getById(userId);
        cardAccount.setUser(user);

        return cardAccountRepository.save(cardAccount);
    }

    public CardAccount getById(UUID id) {
        return cardAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta/Cartão não encontrado com o ID: " + id));
    }

    public List<CardAccount> getByUser(UUID userId) {
        userService.getById(userId);
        return cardAccountRepository.findByUserIdOrderByCardNameAsc(userId);
    }

    @Transactional
    public CardAccount update(UUID id, CardAccount cardUpdated) {
        CardAccount cardAccount = getById(id);

        cardAccount.setCardName(cardUpdated.getCardName());
        cardAccount.setClosingDate(cardUpdated.getClosingDate());
        cardAccount.setDueDate(cardUpdated.getDueDate());

        return cardAccountRepository.save(cardAccount);
    }

    @Transactional
    public void delete(UUID id) {
        CardAccount cardAccount = getById(id);
        cardAccountRepository.delete(cardAccount);
    }

}
