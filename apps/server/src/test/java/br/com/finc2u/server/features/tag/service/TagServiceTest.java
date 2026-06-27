package br.com.finc2u.server.features.tag.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    private Tag tag;
    private UUID tagId;

    @BeforeEach
    void setUp() {
        tagId = UUID.randomUUID();
        tag = Tag.builder()
                .name("Alimentação")
                .build();
        tag.setId(tagId);
    }

    @Test
    void create_shouldReturnTag_whenNameIsUnique() {
        when(tagRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(tag);

        Tag response = tagService.create(tag);

        assertNotNull(response);
        assertEquals(tag.getName(), response.getName());
        verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    void create_shouldThrowBusinessException_whenNameAlreadyExists() {
        when(tagRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(tag));

        assertThrows(BusinessException.class, () -> tagService.create(tag));
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    void getById_shouldReturnTag_whenIdExists() {
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));

        Tag response = tagService.getById(tagId);

        assertNotNull(response);
        assertEquals(tagId, response.getId());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(tagRepository.findById(tagId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagService.getById(tagId));
    }

    @Test
    void getAll_shouldReturnListOfTags() {
        when(tagRepository.findAll()).thenReturn(List.of(tag));

        List<Tag> response = tagService.getAll();

        assertNotNull(response);
        assertFalse(response.isEmpty());
        assertEquals(1, response.size());
    }

    @Test
    void getByIds_shouldReturnListOfTags() {
        List<UUID> ids = List.of(tagId);
        when(tagRepository.findAllById(ids)).thenReturn(List.of(tag));

        List<Tag> response = tagService.getByIds(ids);

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(tagId, response.getFirst().getId());
    }

    @Test
    void update_shouldReturnUpdatedTag_whenTagExists() {
        Tag tagUpdates = Tag.builder().name("Lazer").build();
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));
        when(tagRepository.findByNameIgnoreCase("Lazer")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(tag);

        Tag response = tagService.update(tagId, tagUpdates);

        assertNotNull(response);
        assertEquals("Lazer", tag.getName());
        verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    void update_shouldThrowBusinessException_whenNameAlreadyExistsOnAnotherTag() {
        Tag other = Tag.builder().name("Lazer").build();
        other.setId(UUID.randomUUID());

        Tag tagUpdates = Tag.builder().name("Lazer").build();
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));
        when(tagRepository.findByNameIgnoreCase("Lazer")).thenReturn(Optional.of(other));

        assertThrows(BusinessException.class, () -> tagService.update(tagId, tagUpdates));
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    void update_shouldAllowSameName_whenRenameToOwnCurrentName() {
        Tag tagUpdates = Tag.builder().name("Alimentação").build();
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));
        when(tagRepository.findByNameIgnoreCase("Alimentação")).thenReturn(Optional.of(tag));
        when(tagRepository.save(any(Tag.class))).thenReturn(tag);

        assertDoesNotThrow(() -> tagService.update(tagId, tagUpdates));
        verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    void delete_shouldCallDelete_whenTagExists() {
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));
        doNothing().when(tagRepository).delete(tag);

        tagService.delete(tagId);

        verify(tagRepository, times(1)).delete(tag);
    }

}
