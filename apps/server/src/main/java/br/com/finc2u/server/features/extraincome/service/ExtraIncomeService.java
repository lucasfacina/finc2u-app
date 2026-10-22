package br.com.finc2u.server.features.extraincome.service;

import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtraIncomeService {

    private final ExtraIncomeRepository extraIncomeRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ExtraIncome create(ExtraIncome extraIncome, UUID userId) {
        User user = userService.getById(userId);
        extraIncome.setUser(user);

        var saved = extraIncomeRepository.save(extraIncome);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(userId, extraIncome.getDate()));
        return saved;
    }

    @Transactional(readOnly = true)
    public ExtraIncome getById(UUID id) {
        return extraIncomeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ganho extra não encontrado com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<ExtraIncome> getByUser(UUID userId) {
        userService.getById(userId);
        return extraIncomeRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<ExtraIncome> getByUserAndPeriod(UUID userId, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new InvalidRequestException("A data inicial não pode ser posterior à data final.");
        }
        userService.getById(userId);
        return extraIncomeRepository.findByUserIdAndDateBetween(userId, start, end);
    }

    @Transactional
    public ExtraIncome update(UUID id, ExtraIncome updates) {
        ExtraIncome extraIncome = getById(id);
        var previousDate = extraIncome.getDate();
        extraIncome.setDescription(updates.getDescription());
        extraIncome.setValue(updates.getValue());
        extraIncome.setDate(updates.getDate());

        var saved = extraIncomeRepository.save(extraIncome);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(extraIncome.getUser().getId(), previousDate, extraIncome.getDate()));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        ExtraIncome extraIncome = getById(id);
        extraIncomeRepository.delete(extraIncome);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(extraIncome.getUser().getId(), extraIncome.getDate()));
    }

}
