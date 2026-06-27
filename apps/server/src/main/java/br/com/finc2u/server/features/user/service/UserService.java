package br.com.finc2u.server.features.user.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User create(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new BusinessException("E-mail já cadastrado!");
        }

        return userRepository.save(user);
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
        User user = getById(id);

        if (!user.getEmail().equals(userUpdated.getEmail()) &&
                userRepository.findByEmail(userUpdated.getEmail()).isPresent()) {
            throw new BusinessException("E-mail já cadastrado!");
        }

        user.setName(userUpdated.getName());
        user.setEmail(userUpdated.getEmail());

        if (user.getUserConfiguration() != null && userUpdated.getUserConfiguration() != null) {
            user.getUserConfiguration().setBaseSalary(userUpdated.getUserConfiguration().getBaseSalary());
            user.getUserConfiguration().setSavingsBalance(userUpdated.getUserConfiguration().getSavingsBalance());
        }

        return userRepository.save(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = getById(id);
        userRepository.delete(user);
    }

}
