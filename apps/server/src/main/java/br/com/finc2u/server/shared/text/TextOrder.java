package br.com.finc2u.server.shared.text;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * A ordem alfabética do português do Brasil, a mesma em qualquer banco: as listas de texto que vão para a resposta são
 * ordenadas aqui, em Java, e não por {@code ORDER BY}, cuja ordem depende da collation do servidor de banco.
 *
 * <p>Compara com o {@link Collator} {@code pt-BR}: as letras acentuadas ficam junto da letra base ("Água" antes de
 * "alimentação"), e, entre textos que só diferem na caixa, a minúscula vem antes ("alimentação", "Alimentação"). Os textos
 * que o {@code Collator} considera iguais (por exemplo, o mesmo acento escrito de duas formas em Unicode) desempatam por
 * {@link String#compareTo}, então a ordem nunca depende da posição de entrada.
 */
public class TextOrder {

    private static final Collator COLLATOR = Collator.getInstance(Locale.forLanguageTag("pt-BR"));

    private static final Comparator<String> COMPARATOR = Comparator.<String, String>comparing(text -> text, COLLATOR::compare)
        .thenComparing(Comparator.naturalOrder());

    private TextOrder() {
    }

    /**
     * Ordena os objetos pelo texto que {@code text} extrai de cada um (ex.: {@code TextOrder.by(Category::getName)}).
     */
    public static <T> Comparator<T> by(Function<? super T, String> text) {
        return Comparator.comparing(text, COMPARATOR);
    }

}
