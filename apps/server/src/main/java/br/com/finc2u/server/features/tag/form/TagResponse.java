package br.com.finc2u.server.features.tag.form;

import br.com.finc2u.server.features.tag.entity.Tag;

import java.util.UUID;

public record TagResponse(
        UUID id,
        String name,
        Long colorCode
) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getColorCode()
        );
    }

}
