package br.com.finc2u.server.features.tag.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.repository.TagRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TagService tagService;

    private User user;
    private UUID userId;
    private Tag tag;
    private UUID tagId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .name("Ana")
            .email("ana@email.com")
            .build();
        user.setId(userId);

        tagId = UUID.randomUUID();
        tag = Tag.builder()
            .name("Essencial")
            .user(user)
            .build();
        tag.setId(tagId);
    }

    @Test
    void create_shouldAttachUserAndSave_whenNameIsUniqueForTheUser() {
        var newTag = Tag.of("Essencial");
        when(userService.getById(userId))
            .thenReturn(user);
        when(tagRepository.findByUserIdAndNameIgnoreCase(userId, "Essencial"))
            .thenReturn(Optional.empty());
        when(tagRepository.save(newTag))
            .thenReturn(tag);

        var response = tagService.create(newTag, userId);

        assertSame(tag, response);
        assertSame(user, newTag.getUser());
        verify(tagRepository, times(1)).save(newTag);
    }

    // "Mesmo nome em outro usuário é aceito" é garantido pela consulta escopada por usuário (TagRepositoryTest).
    @Test
    void create_shouldThrowBusinessException_whenNameAlreadyExistsForTheUser_ignoringCase() {
        var newTag = Tag.of("ESSENCIAL");
        when(userService.getById(userId))
            .thenReturn(user);
        when(tagRepository.findByUserIdAndNameIgnoreCase(userId, "ESSENCIAL"))
            .thenReturn(Optional.of(tag));

        assertThrows(BusinessException.class, () -> tagService.create(newTag, userId));
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    void create_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        assertThrows(ResourceNotFoundException.class, () -> tagService.create(Tag.of("Essencial"), userId));
        verifyNoInteractions(tagRepository);
    }

    @Test
    void getById_shouldReturnTag_whenIdExists() {
        when(tagRepository.findById(tagId))
            .thenReturn(Optional.of(tag));

        var response = tagService.getById(tagId);

        assertNotNull(response);
        assertEquals(tagId, response.getId());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(tagRepository.findById(tagId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagService.getById(tagId));
    }

    @Test
    void getByUser_shouldReturnTagsOfTheUser() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(tagRepository.findByUserIdOrderByNameAsc(userId))
            .thenReturn(List.of(tag));

        var response = tagService.getByUser(userId);

        assertEquals(List.of(tag), response);
    }

    @Test
    void getByUser_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        assertThrows(ResourceNotFoundException.class, () -> tagService.getByUser(userId));
        verifyNoInteractions(tagRepository);
    }

    @Test
    void getByIds_shouldReturnListOfTags() {
        when(tagRepository.findByIdInAndUserId(anyCollection(), eq(userId)))
            .thenReturn(List.of(tag));

        var response = tagService.getByIds(List.of(tagId), userId);

        assertEquals(1, response.size());
        assertEquals(tagId, response.getFirst().getId());
    }

    @Test
    void getByIds_shouldThrowResourceNotFoundException_whenSomeIdDoesNotExist() {
        var missingId = UUID.randomUUID();
        when(tagRepository.findByIdInAndUserId(anyCollection(), eq(userId)))
            .thenReturn(List.of(tag));

        var exception = assertThrows(
            ResourceNotFoundException.class,
            () -> tagService.getByIds(List.of(tagId, missingId), userId)
        );

        assertTrue(exception.getMessage().contains(missingId.toString()));
        assertFalse(exception.getMessage().contains(tagId.toString()));
    }

    @Test
    void getByIds_shouldThrowResourceNotFoundException_whenTagBelongsToAnotherUser() {
        // A consulta é escopada pelo dono: a tag de outro usuário não volta e é tratada como inexistente.
        when(tagRepository.findByIdInAndUserId(anyCollection(), eq(userId)))
            .thenReturn(List.of());

        var exception = assertThrows(
            ResourceNotFoundException.class,
            () -> tagService.getByIds(List.of(tagId), userId)
        );

        assertTrue(exception.getMessage().contains(tagId.toString()));
    }

    @Test
    void getByIds_shouldNotFail_whenRequestHasDuplicatedIds() {
        when(tagRepository.findByIdInAndUserId(anyCollection(), eq(userId)))
            .thenReturn(List.of(tag));

        var response = tagService.getByIds(List.of(tagId, tagId), userId);

        assertEquals(1, response.size());
        assertEquals(tagId, response.getFirst().getId());
    }

    @Test
    void getByIds_shouldReturnEmptyList_whenIdsIsEmpty() {
        var response = tagService.getByIds(List.of(), userId);

        assertTrue(response.isEmpty());
        verifyNoInteractions(tagRepository);
    }

    @Test
    void update_shouldReturnUpdatedTag_whenTagExists() {
        var tagUpdates = Tag.of("Supérfluo");
        when(tagRepository.findById(tagId))
            .thenReturn(Optional.of(tag));
        when(tagRepository.findByUserIdAndNameIgnoreCase(userId, "Supérfluo"))
            .thenReturn(Optional.empty());
        when(tagRepository.save(tag))
            .thenReturn(tag);

        var response = tagService.update(tagId, tagUpdates);

        assertNotNull(response);
        assertEquals("Supérfluo", tag.getName());
        verify(tagRepository, times(1)).save(tag);
    }

    @Test
    void update_shouldThrowBusinessException_whenNameAlreadyExistsOnAnotherTagOfTheUser() {
        var other = Tag.builder()
            .name("Supérfluo")
            .user(user)
            .build();
        other.setId(UUID.randomUUID());
        when(tagRepository.findById(tagId))
            .thenReturn(Optional.of(tag));
        when(tagRepository.findByUserIdAndNameIgnoreCase(userId, "supérfluo"))
            .thenReturn(Optional.of(other));

        assertThrows(BusinessException.class, () -> tagService.update(tagId, Tag.of("supérfluo")));
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    void update_shouldAllowSameName_whenRenameToOwnCurrentName() {
        when(tagRepository.findById(tagId))
            .thenReturn(Optional.of(tag));
        when(tagRepository.findByUserIdAndNameIgnoreCase(userId, "Essencial"))
            .thenReturn(Optional.of(tag));
        when(tagRepository.save(tag))
            .thenReturn(tag);

        assertDoesNotThrow(() -> tagService.update(tagId, Tag.of("Essencial")));
        verify(tagRepository, times(1)).save(tag);
    }

    @Test
    void delete_shouldCallDelete_whenTagExists() {
        when(tagRepository.findById(tagId))
            .thenReturn(Optional.of(tag));
        doNothing().when(tagRepository).delete(tag);

        tagService.delete(tagId);

        verify(tagRepository, times(1)).delete(tag);
    }

}
