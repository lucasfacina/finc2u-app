package br.com.finc2u.server.shared.seed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// O contexto é genérico: os testes usam tipos simples do Java, sem nenhuma entidade das features.
class SeedContextTest {

    private final SeedContext context = new SeedContext();

    @Test
    void get_shouldReturnTheSingleInstanceOfTheType() {
        context.put(String.class, "o usuário");

        assertEquals("o usuário", context.get(String.class));
    }

    @Test
    void get_shouldReturnEachInstanceByItsKey_whenSeveralOfTheSameTypeWereStored() {
        context.put(Integer.class, "Nubank", 1);
        context.put(Integer.class, "Mercado Pago", 2);

        assertEquals(1, context.get(Integer.class, "Nubank"));
        assertEquals(2, context.get(Integer.class, "Mercado Pago"));
    }

    @Test
    void get_shouldNotMixTypes_whenTheyShareTheSameKey() {
        context.put(Integer.class, "Lazer", 7);
        context.put(String.class, "Lazer", "categoria");

        assertEquals(7, context.get(Integer.class, "Lazer"));
        assertEquals("categoria", context.get(String.class, "Lazer"));
    }

    @Test
    void get_shouldKeepTheSingleInstanceApartFromTheKeyedOnes() {
        context.put(String.class, "única");
        context.put(String.class, "chave", "com chave");

        assertEquals("única", context.get(String.class));
        assertEquals("com chave", context.get(String.class, "chave"));
    }

    // Recarregar o que já existia (seeder idempotente) guarda de novo sob a mesma chave: vale a última.
    @Test
    void put_shouldReplaceTheInstanceStoredUnderTheSameKey() {
        context.put(Integer.class, "Nubank", 1);
        context.put(Integer.class, "Nubank", 10);

        assertEquals(10, context.get(Integer.class, "Nubank"));
    }

    @Test
    void get_shouldFailWithAClearMessage_whenTheKeyedInstanceWasNotStored() {
        context.put(Integer.class, "Nubank", 1);

        var failure = assertThrows(IllegalStateException.class, () -> context.get(Integer.class, "Itaú"));

        assertTrue(failure.getMessage().contains("Integer"));
        assertTrue(failure.getMessage().contains("Itaú"));
    }

    @Test
    void get_shouldFailWithAClearMessage_whenTheSingleInstanceWasNotStored() {
        var failure = assertThrows(IllegalStateException.class, () -> context.get(String.class));

        assertTrue(failure.getMessage().contains("String"));
        assertFalse(failure.getMessage().contains("\""));
    }

}
