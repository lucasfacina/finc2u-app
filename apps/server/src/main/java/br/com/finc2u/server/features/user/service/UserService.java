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

/**
 * Cadastro de usuários. O e-mail é único. O salário <b>não</b> é do usuário: vem do histórico de salários. Ao cadastrar,
 * grava o usuário e publica o {@code UserRegisteredEvent}, e as features interessadas (primeira vigência de salário, categorias
 * padrão) reagem na mesma transação. É a base que quase todas as outras features usam para validar o dono dos dados ({@link #getById}).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Cadastra o usuário e avisa as outras features por evento (o primeiro salário e as categorias padrão nascem dos
     * <i>listeners</i>, na mesma transação).
     *
     * @param baseSalary    primeiro salário, entregue ao evento
     * @param effectiveFrom início da vigência desse salário (padrão: dia 1 do mês de hoje, lido do {@link Clock})
     * @throws BusinessException se o e-mail já está cadastrado
     */
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

    /**
     * Busca o usuário pelo id.
     *
     * @throws ResourceNotFoundException se não existir
     */
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com o ID: " + id));
    }

    /**
     * Todos os usuários cadastrados, sem filtro.
     */
    @Transactional(readOnly = true)
    public List<User> getAll() {
        return userRepository.findAll();
    }

    /**
     * Altera nome, e-mail e saldo de poupança. O salário não passa por aqui: ele muda pelo histórico de salários.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     * @throws BusinessException         se o novo e-mail já pertence a outro usuário
     */
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

    /**
     * Apaga o usuário.
     *
     * @throws ResourceNotFoundException se não existir (lançada por {@code UserService#getById})
     */
    @Transactional
    public void delete(UUID id) {
        var user = getById(id);
        userRepository.delete(user);
    }

}
