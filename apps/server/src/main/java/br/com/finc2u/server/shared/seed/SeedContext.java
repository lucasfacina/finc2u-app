package br.com.finc2u.server.shared.seed;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.user.entity.User;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class SeedContext {

    private User user;

    private final Map<String, CardAccount> cardsByName = new LinkedHashMap<>();

    public void putCard(CardAccount card) {
        cardsByName.put(card.getCardName(), card);
    }

    public CardAccount card(String name) {
        return cardsByName.get(name);
    }

    private final Map<String, Tag> tagsByName = new LinkedHashMap<>();

    public void putTag(Tag tag) {
        tagsByName.put(tag.getName(), tag);
    }

    public Tag tag(String name) {
        return tagsByName.get(name);
    }

}
