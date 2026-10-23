package br.com.finc2u.server.features.extraincome.service;

import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ganhos extras do mês (entradas além do salário), somados como renda no resumo mensal. Toda escrita publica o
 * {@code PeriodDataChangedEvent} a partir do mês mais antigo afetado, para o resumo se manter em dia.
 */
@Service
@RequiredArgsConstructor
public class ExtraIncomeService {

    private final ExtraIncomeRepository extraIncomeRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Registra o ganho extra do usuário.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    @Transactional
    public ExtraIncome create(ExtraIncome extraIncome, UUID userId) {
        var user = userService.getById(userId);
        extraIncome.setUser(user);

        var saved = extraIncomeRepository.save(extraIncome);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(userId, extraIncome.getDate()));
        return saved;
    }

    /**
     * Busca o ganho extra pelo id.
     *
     * @throws ResourceNotFoundException se não existir
     */
    @Transactional(readOnly = true)
    public ExtraIncome getById(UUID id) {
        return extraIncomeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ganho extra não encontrado com o ID: " + id));
    }

    /**
     * Todos os ganhos extras do usuário.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    @Transactional(readOnly = true)
    public List<ExtraIncome> getByUser(UUID userId) {
        userService.getById(userId);
        return extraIncomeRepository.findByUserId(userId);
    }

    /**
     * Ganhos extras com data entre as duas datas, inclusive. É a leitura que o resumo mensal usa, no lugar do repositório.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     * @throws InvalidRequestException   se a data inicial for posterior à final
     */
    @Transactional(readOnly = true)
    public List<ExtraIncome> getByUserAndPeriod(UUID userId, LocalDate start, LocalDate end) {
        if (start.isAfter(end))
            throw new InvalidRequestException("A data inicial não pode ser posterior à data final.");

        userService.getById(userId);
        return extraIncomeRepository.findByUserIdAndDateBetween(userId, start, end);
    }

    /**
     * Altera descrição, valor e data. O resumo é avisado a partir do mês mais antigo entre a data antiga e a nova.
     *
     * @throws ResourceNotFoundException se o ganho não existir (lançada por {@code ExtraIncomeService#getById})
     */
    @Transactional
    public ExtraIncome update(UUID id, ExtraIncome updates) {
        var extraIncome = getById(id);
        var previousDate = extraIncome.getDate();

        extraIncome.setDescription(updates.getDescription());
        extraIncome.setValue(updates.getValue());
        extraIncome.setDate(updates.getDate());

        var saved = extraIncomeRepository.save(extraIncome);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(extraIncome.getUser().getId(), previousDate, extraIncome.getDate()));

        return saved;
    }

    /**
     * Apaga o ganho extra e avisa o resumo a partir do mês dele.
     *
     * @throws ResourceNotFoundException se não existir (lançada por {@code ExtraIncomeService#getById})
     */
    @Transactional
    public void delete(UUID id) {
        var extraIncome = getById(id);
        extraIncomeRepository.delete(extraIncome);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(extraIncome.getUser().getId(), extraIncome.getDate()));
    }

}
