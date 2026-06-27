package br.com.finc2u.server.features.tag.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional
    public Tag create(Tag tag) {
        if (tagRepository.findByNameIgnoreCase(tag.getName()).isPresent()) {
            throw new BusinessException("Tag já cadastrada");
        }

        return tagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public Tag getById(UUID id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag não encontrada com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Tag> getAll() {
        return tagRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Tag> getByIds(List<UUID> ids) {
        return tagRepository.findAllById(ids);
    }

    @Transactional
    public Tag update(UUID id, Tag tagUpdated) {
        Tag tag = getById(id);

        tagRepository.findByNameIgnoreCase(tagUpdated.getName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(__ -> { throw new BusinessException("Tag já cadastrada"); });

        tag.setName(tagUpdated.getName());
        tag.setColorCode(tagUpdated.getColorCode());

        return tagRepository.save(tag);
    }

    @Transactional
    public void delete(UUID id) {
        Tag tag = getById(id);
        tagRepository.delete(tag);
    }

}
