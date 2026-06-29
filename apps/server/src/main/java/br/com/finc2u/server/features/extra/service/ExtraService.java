package br.com.finc2u.server.features.extra.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.extra.repository.ExtraRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtraService {

    private final ExtraRepository extraRepository;
    private final UserService userService;

    @Transactional
    public Extra create(Extra extra, UUID userId) {
        User user = userService.getById(userId);
        extra.setUser(user);
        return extraRepository.save(extra);
    }

    @Transactional(readOnly = true)
    public Extra getById(UUID id) {
        return extraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ganho extra não encontrado com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Extra> getByUser(UUID userId) {
        userService.getById(userId);
        return extraRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Extra> getByUserAndPeriod(UUID userId, LocalDate start, LocalDate end) {
        userService.getById(userId);
        return extraRepository.findByUserIdAndDateBetween(userId, start, end);
    }

    @Transactional
    public Extra update(UUID id, Extra updates) {
        Extra extra = getById(id);
        extra.setDescription(updates.getDescription());
        extra.setValue(updates.getValue());
        extra.setDate(updates.getDate());
        return extraRepository.save(extra);
    }

    @Transactional
    public void delete(UUID id) {
        Extra extra = getById(id);
        extraRepository.delete(extra);
    }

}
