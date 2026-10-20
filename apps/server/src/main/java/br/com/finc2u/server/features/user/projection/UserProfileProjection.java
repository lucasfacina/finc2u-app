package br.com.finc2u.server.features.user.projection;

import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.form.UserResponse;
import br.com.finc2u.server.features.user.service.UserService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public record UserProfileProjection(UserService userService, SalaryHistoryService salaryHistoryService) {

    public UserResponse toProfile(User user) {
        var currentSalary = salaryHistoryService.getCurrentBaseSalary(user.getId())
            .orElse(null);
        return UserResponse.from(user, currentSalary);
    }

    public UserResponse getProfile(UUID id) {
        return toProfile(userService.getById(id));
    }

    public List<UserResponse> getProfiles() {
        var users = userService.getAll();
        Map<UUID, BigDecimal> salaries = salaryHistoryService.getCurrentBaseSalaries(
            users.stream()
                .map(User::getId)
                .toList());

        return users.stream()
            .map(user -> UserResponse.from(user, salaries.get(user.getId())))
            .toList();
    }

}
