package br.com.finc2u.server.features.category.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DefaultCategory {

    FOOD("Alimentação"),
    TRANSPORT("Transporte"),
    HOUSING("Moradia"),
    HEALTH("Saúde"),
    LEISURE("Lazer"),
    EDUCATION("Educação"),
    SUBSCRIPTIONS("Assinaturas"),
    OTHERS("Outros");

    private final String categoryName;

}
