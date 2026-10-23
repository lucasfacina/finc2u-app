package br.com.finc2u.server.features.card.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.repository.CardAccountRepository;
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
        var user = userService.getById(userId);

        // A constraint unique do banco diferencia maiúsculas de minúsculas ("Nubank" × "nubank"); esta checagem não,
        // e devolve um erro de negócio claro em vez de uma violação de integridade.
        if (cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, cardAccount.getCardName()).isPresent())
            throw new BusinessException("Cartão já cadastrado!");

        cardAccount.setUser(user);

        return cardAccountRepository.save(cardAccount);
    }

    @Transactional(readOnly = true)
    public CardAccount getById(UUID id) {
        return cardAccountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta/Cartão não encontrado com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public CardAccount getByIdAndUser(UUID id, UUID userId) {
        return cardAccountRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conta/Cartão não encontrado com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public CardAccount getByUserAndName(UUID userId, String name) {
        return cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, name)
            .orElseThrow(() -> new ResourceNotFoundException("Conta/Cartão não encontrado com o nome: " + name));
    }

    @Transactional(readOnly = true)
    public List<CardAccount> getByUser(UUID userId, String name) {
        userService.getById(userId);
        var searchTerm = name == null
            ? ""
            : name.trim();
        return cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCaseOrderByCardNameAsc(userId, searchTerm);
    }

    @Transactional
    public CardAccount update(UUID id, CardAccount cardUpdated) {
        var cardAccount = getById(id);

        cardAccountRepository.findByUserIdAndCardNameIgnoreCase(cardAccount.getUser().getId(), cardUpdated.getCardName())
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(__ -> {
                throw new BusinessException("Cartão já cadastrado!");
            });

        cardAccount.setCardName(cardUpdated.getCardName());
        cardAccount.setClosingDate(cardUpdated.getClosingDate());
        cardAccount.setDueDate(cardUpdated.getDueDate());
        cardAccount.setExpirationDate(cardUpdated.getExpirationDate());

        return cardAccountRepository.save(cardAccount);
    }

    @Transactional
    public void delete(UUID id) {
        var cardAccount = getById(id);
        cardAccountRepository.delete(cardAccount);
    }

}
