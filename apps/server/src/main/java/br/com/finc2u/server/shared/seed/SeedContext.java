package br.com.finc2u.server.shared.seed;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * A memória entre os seeders: o que um deixa persistido, os seguintes usam (os ids são UUID gerados no persist, não há ids
 * fixos para referenciar). Genérico de propósito: guarda instâncias por tipo e chave sem conhecer nenhuma feature, então o
 * shared não depende das features. Duas formas: uma instância por tipo (ex.: o usuário do seed) ou várias do mesmo tipo,
 * cada uma com a sua chave natural (ex.: categorias e cartões pelo nome).
 */
public class SeedContext {

    // Chave da instância única de um tipo; quem guarda várias do mesmo tipo usa a chave natural de cada uma.
    private static final String SINGLE = "#único";

    private final Map<Class<?>, Map<String, Object>> instances = new HashMap<>();

    public <T> void put(Class<T> type, T instance) {
        put(type, SINGLE, instance);
    }

    public <T> void put(Class<T> type, String key, T instance) {
        instances.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(key, instance);
    }

    public <T> T get(Class<T> type) {
        return get(type, SINGLE);
    }

    // Falha na hora, com mensagem clara, se o seeder anterior não guardou o que este precisa (em vez de devolver null).
    public <T> T get(Class<T> type, String key) {
        var instance = instances.getOrDefault(type, Map.of()).get(key);
        if (instance == null) {
            throw new IllegalStateException("O seed ainda não guardou " + type.getSimpleName()
                + (SINGLE.equals(key) ? "" : " \"" + key + "\"") + ": confira a ordem dos seeders.");
        }

        return type.cast(instance);
    }

}
