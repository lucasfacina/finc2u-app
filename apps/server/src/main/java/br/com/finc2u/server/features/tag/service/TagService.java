package br.com.finc2u.server.features.tag.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.repository.TagRepository;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final UserService userService;

    @Transactional
    public Tag create(Tag tag, UUID userId) {
        var user = userService.getById(userId);

        // A constraint unique do banco diferencia maiúsculas de minúsculas ("Lazer" × "lazer"); esta checagem não,
        // e devolve um erro de negócio claro em vez de uma violação de integridade.
        if (tagRepository.findByUserIdAndNameIgnoreCase(userId, tag.getName()).isPresent()) {
            throw new BusinessException("Tag já cadastrada!");
        }

        tag.setUser(user);

        return tagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public Tag getById(UUID id) {
        return tagRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tag não encontrada com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Tag> getByUser(UUID userId) {
        userService.getById(userId);
        return tagRepository.findByUserIdOrderByNameAsc(userId);
    }

    // Tag de outro usuário é tratada como inexistente: a resposta não revela que o id existe.
    @Transactional(readOnly = true)
    public List<Tag> getByIds(List<UUID> ids, UUID userId) {
        Set<UUID> requestedIds = new LinkedHashSet<>(ids);
        if (requestedIds.isEmpty()) {
            return List.of();
        }

        List<Tag> tags = tagRepository.findByIdInAndUserId(requestedIds, userId);
        if (tags.size() < requestedIds.size()) {
            Set<UUID> foundIds = tags.stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());
            List<UUID> missingIds = requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
            throw new ResourceNotFoundException("Tag não encontrada com o(s) ID(s): " + missingIds);
        }

        return tags;
    }

    @Transactional
    public Tag update(UUID id, Tag tagUpdated) {
        Tag tag = getById(id);

        tagRepository.findByUserIdAndNameIgnoreCase(tag.getUser().getId(), tagUpdated.getName())
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(__ -> {
                throw new BusinessException("Tag já cadastrada!");
            });

        tag.setName(tagUpdated.getName());

        return tagRepository.save(tag);
    }

    @Transactional
    public void delete(UUID id) {
        Tag tag = getById(id);
        tagRepository.delete(tag);
    }

}
