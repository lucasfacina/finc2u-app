package br.com.finc2u.server.features.user.projection;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.form.UserResponse;
import br.com.finc2u.server.features.user.port.CurrentSalaryPort;
import br.com.finc2u.server.features.user.service.UserService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/*
 * Monta o UserResponse com o salário vigente, que vive no SalaryHistory (não no User) e chega pela porta CurrentSalaryPort,
 * sem que o user conheça a feature do salário. Na listagem, busca o salário de todos os usuários de uma vez, para não gerar
 * uma consulta por usuário (N+1).
 */
@Component
public record UserProfileProjection(UserService userService, CurrentSalaryPort currentSalaryPort) {

    public UserResponse toProfile(User user) {
        var currentSalary = currentSalaryPort.getCurrentBaseSalary(user.getId())
            .orElse(null);
        return UserResponse.from(user, currentSalary);
    }

    public UserResponse getProfile(UUID id) {
        return toProfile(userService.getById(id));
    }

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
