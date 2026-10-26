package br.com.finc2u.server.features.card.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.repository.CardAccountRepository;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.text.TextOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cartões e contas do usuário. O nome é único por usuário, sem diferenciar maiúsculas de minúsculas. O dia de fechamento e o de
 * vencimento do cartão definem em qual fatura cai cada compra (veja {@code CardAccount.calculateInvoiceDueDate}).
 */
@Service
@RequiredArgsConstructor
public class CardAccountService {

    private final CardAccountRepository cardAccountRepository;
    private final UserService userService;
    private final Clock clock;

    /**
     * Cadastra o cartão do usuário.
     *
     * @throws InvalidRequestException   se a validade informada está num mês passado (o mês de hoje vale)
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     * @throws BusinessException         se o usuário já tem um cartão com esse nome
     */
    @Transactional
    public CardAccount create(CardAccount cardAccount, UUID userId) {
        cardAccount.assertExpirationNotInThePast(LocalDate.now(clock));

        var user = userService.getById(userId);

        // A constraint unique do banco diferencia maiúsculas de minúsculas ("Nubank" × "nubank"); esta checagem não,
        // e devolve um erro de negócio claro em vez de uma violação de integridade.
        if (cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, cardAccount.getCardName()).isPresent())
            throw new BusinessException("Cartão já cadastrado!");

        cardAccount.setUser(user);

        return cardAccountRepository.save(cardAccount);
    }

    /**
     * Busca o cartão pelo id.
     *
     * @throws ResourceNotFoundException se não existir
     */
    @Transactional(readOnly = true)
    public CardAccount getById(UUID id) {
        return cardAccountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta/Cartão não encontrado com o ID: " + id));
    }

    /**
     * Busca o cartão do usuário. O de outro usuário é tratado como inexistente (a resposta não revela que o id existe).
     *
     * @throws ResourceNotFoundException se não existir ou for de outro usuário
     */
    @Transactional(readOnly = true)
    public CardAccount getByIdAndUser(UUID id, UUID userId) {
        return cardAccountRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conta/Cartão não encontrado com o ID: " + id));
    }

    /**
     * Busca o cartão do usuário pelo nome exato, sem diferenciar maiúsculas de minúsculas. É o que vincula a despesa ao cartão
     * quando ela informa o nome.
     *
     * @throws ResourceNotFoundException se o usuário não tem cartão com esse nome
     */
    @Transactional(readOnly = true)
    public CardAccount getByUserAndName(UUID userId, String name) {
        return cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, name)
            .orElseThrow(() -> new ResourceNotFoundException("Conta/Cartão não encontrado com o nome: " + name));
    }

    /**
     * Cartões do usuário, ordenados pelo nome na ordem alfabética do português ({@code TextOrder}). {@code name} é uma busca parcial opcional (sem ele, devolve todos).
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    @Transactional(readOnly = true)
    public List<CardAccount> getByUser(UUID userId, String name) {
        userService.getById(userId);
        var searchTerm = name == null
            ? ""
            : name.trim();
        return cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCase(userId, searchTerm).stream()
            .sorted(TextOrder.by(CardAccount::getCardName))
            .toList();
    }

    /**
     * Altera nome, fechamento, vencimento e validade do cartão. As despesas já lançadas não mudam. A validade só é conferida se mudou:
     * renomear um cartão que já venceu continua possível.
     *
     * @throws ResourceNotFoundException se o cartão não existir (lançada por {@code CardAccountService#getById})
     * @throws InvalidRequestException   se a nova validade está num mês passado (o mês de hoje vale)
     * @throws BusinessException         se outro cartão do mesmo usuário já tem o nome
     */
    @Transactional
    public CardAccount update(UUID id, CardAccount cardUpdated) {
        var cardAccount = getById(id);

        if (!Objects.equals(cardAccount.getExpirationDate(), cardUpdated.getExpirationDate()))
            cardUpdated.assertExpirationNotInThePast(LocalDate.now(clock));

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

    /**
     * Apaga o cartão. Com despesas ou modelos usando-o, o banco recusa (violação de integridade, 409 na camada web).
     *
     * @throws ResourceNotFoundException se não existir (lançada por {@code CardAccountService#getById})
     */
    @Transactional
    public void delete(UUID id) {
        var cardAccount = getById(id);
        cardAccountRepository.delete(cardAccount);
    }

}
