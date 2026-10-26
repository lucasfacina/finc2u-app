package br.com.finc2u.server.shared.model;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.user.entity.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Uma associação LAZY entrega um proxy do Hibernate no lugar da entidade. O proxy, a entidade carregada e uma cópia solta com o mesmo id
 * são a mesma linha do banco: o equals precisa ser verdadeiro nos dois sentidos e o hashCode igual, senão um HashSet (o calculador
 * agrupa por Category) guardaria a mesma categoria duas vezes.
 */
@DataJpaTest
class BaseModelProxyTest {

    @Autowired
    private TestEntityManager testEntityManager;

    private Category proxy;
    private Category detachedCopy;

    @BeforeEach
    void setUp() {
        var user = testEntityManager.persist(User.builder()
            .name("Ana")
            .email("ana.proxy@email.com")
            .savingsBalance(BigDecimal.ZERO)
            .build());
        var persisted = testEntityManager.persistAndFlush(Category.builder()
            .name("Alimentação")
            .user(user)
            .build());
        testEntityManager.clear();

        proxy = testEntityManager.getEntityManager().getReference(Category.class, persisted.getId());
        detachedCopy = Category.of("Alimentação");
        detachedCopy.setId(persisted.getId());
    }

    @Test
    void theProxyIsReallyAProxy_notTheEntityItself() {
        assertFalse(Hibernate.isInitialized(proxy), "sem isso o teste não prova nada sobre proxy");
        assertNotEquals(Category.class, proxy.getClass());
    }

    @Test
    void equals_shouldBeTrueBothWays_betweenTheProxyAndADetachedCopyWithTheSameId() {
        assertEquals(proxy, detachedCopy);
        assertEquals(detachedCopy, proxy);
    }

    @Test
    void hashCode_shouldBeTheSame_forTheProxyAndTheDetachedCopy() {
        assertEquals(proxy.hashCode(), detachedCopy.hashCode());
    }

    @Test
    void hashSet_shouldHoldOneElement_whenTheProxyAndTheDetachedCopyAreAdded() {
        Set<Category> categories = new HashSet<>();

        categories.add(proxy);
        categories.add(detachedCopy);

        assertEquals(1, categories.size());
    }

    @Test
    void equals_shouldBeTrueBothWays_betweenTheProxyAndTheLoadedEntity() {
        var loaded = testEntityManager.find(Category.class, detachedCopy.getId());

        assertEquals(proxy, loaded);
        assertEquals(loaded, proxy);
    }

    @Test
    void equals_shouldBeFalse_forAnotherIdOrWithoutId() {
        var other = Category.of("Lazer");
        other.setId(UUID.randomUUID());

        assertNotEquals(proxy, other);
        assertNotEquals(Category.of("Sem id"), Category.of("Sem id"));
    }

}
