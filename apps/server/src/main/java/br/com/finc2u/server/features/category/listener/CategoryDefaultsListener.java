package br.com.finc2u.server.features.category.listener;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.user.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cria as categorias padrão do usuário recém-cadastrado. Roda de forma síncrona, na mesma transação do cadastro: se falhar, o
 * cadastro inteiro é desfeito e o usuário não fica sem categorias (a despesa exige uma). Reage ao {@code UserRegisteredEvent}, então
 * a feature {@code user} não conhece as categorias.
 */
@Component
@RequiredArgsConstructor
public class CategoryDefaultsListener {

    private final CategoryService categoryService;

    /**
     * Garante as categorias padrão do usuário do evento. Idempotente: as que ele já tem não são recriadas.
     */
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        categoryService.createDefaults(event.user());
    }

}
