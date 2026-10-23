package br.com.finc2u.server.features.user.projection;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.form.UserResponse;
import br.com.finc2u.server.features.user.port.CurrentSalaryPort;
import br.com.finc2u.server.features.user.service.UserService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Monta o {@code UserResponse} com o salário vigente, que vive no histórico de salários (não no {@code User}) e chega pela porta
 * {@link CurrentSalaryPort}, sem que o {@code user} conheça a feature do salário. Na listagem, busca o salário de todos os
 * usuários de uma vez, para não gerar uma consulta por usuário (N+1).
 */
@Component
public record UserProfileProjection(UserService userService, CurrentSalaryPort currentSalaryPort) {

    /**
     * Perfil do usuário já carregado, com o salário vigente hoje (nulo se ele ainda não tem nenhuma vigência iniciada).
     */
    public UserResponse toProfile(User user) {
        var currentSalary = currentSalaryPort.getCurrentBaseSalary(user.getId())
            .orElse(null);
        return UserResponse.from(user, currentSalary);
    }

    /**
     * Perfil do usuário pelo id.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    public UserResponse getProfile(UUID id) {
        return toProfile(userService.getById(id));
    }

    /**
     * Perfil de todos os usuários, com os salários vigentes lidos numa consulta só.
     */
    public List<UserResponse> getProfiles() {
        var users = userService.getAll();
        var salaries = currentSalaryPort.getCurrentBaseSalaries(
            users.stream()
                .map(User::getId)
                .toList());

        return users.stream()
            .map(user -> UserResponse.from(user, salaries.get(user.getId())))
            .toList();
    }

}
