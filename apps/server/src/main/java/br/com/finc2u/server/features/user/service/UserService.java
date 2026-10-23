package br.com.finc2u.server.features.user.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.event.UserRegisteredEvent;
import br.com.finc2u.server.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public User create(User user, BigDecimal baseSalary, LocalDate effectiveFrom) {
        if (userRepository.findByEmail(user.getEmail()).isPresent())
            throw new BusinessException("E-mail já cadastrado!");

        var savedUser = userRepository.save(user);
        var salaryEffectiveFrom = effectiveFrom != null
            ? effectiveFrom
            : YearMonth.now(clock).atDay(1);
        eventPublisher.publishEvent(new UserRegisteredEvent(savedUser, baseSalary, salaryEffectiveFrom));

        return savedUser;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<User> getAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User update(UUID id, User userUpdated) {
        var user = getById(id);

        if (!user.getEmail().equals(userUpdated.getEmail()) &&
            userRepository.findByEmail(userUpdated.getEmail()).isPresent()) {
            throw new BusinessException("E-mail já cadastrado!");
        }

        user.setName(userUpdated.getName());
        user.setEmail(userUpdated.getEmail());

        user.setSavingsBalance(userUpdated.getSavingsBalance());

        return userRepository.save(user);
    }

    @Transactional
    public void delete(UUID id) {
        var user = getById(id);
        userRepository.delete(user);
    }

}
