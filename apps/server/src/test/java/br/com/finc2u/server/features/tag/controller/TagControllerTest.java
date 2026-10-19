package br.com.finc2u.server.features.tag.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.features.tag.dto.TagRequest;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TagController.class, GlobalExceptionHandler.class})
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @Autowired
    private ObjectMapper objectMapper;

    private Tag tag;
    private UUID tagId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tagId = UUID.randomUUID();
        userId = UUID.randomUUID();
        tag = Tag.builder()
            .name("Alimentação")
            .build();
        tag.setId(tagId);
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        TagRequest request = new TagRequest("Alimentação");
        when(tagService.create(any(Tag.class), eq(userId)))
            .thenReturn(tag);

        mockMvc.perform(post("/tags")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(tagId.toString()))
            .andExpect(jsonPath("$.name").value("Alimentação"));
    }

    @Test
    void create_shouldReturnBadRequest_whenInvalidInput() throws Exception {
        TagRequest invalidRequest = new TagRequest("");

        mockMvc.perform(post("/tags")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
        TagRequest request = new TagRequest("Alimentação");

        mockMvc.perform(post("/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getByUserId_shouldReturnOk() throws Exception {
        when(tagService.getByUser(userId))
            .thenReturn(List.of(tag));

        mockMvc.perform(get("/tags")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Alimentação"));
    }

    @Test
    void getByUserId_shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
        mockMvc.perform(get("/tags"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(tagService.getById(tagId))
            .thenReturn(tag);

        mockMvc.perform(get("/tags/{id}", tagId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(tagId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        TagRequest request = new TagRequest("Alimentação");
        when(tagService.update(eq(tagId), any(Tag.class)))
            .thenReturn(tag);

        mockMvc.perform(put("/tags/{id}", tagId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alimentação"));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(tagService).delete(tagId);

        mockMvc.perform(delete("/tags/{id}", tagId))
            .andExpect(status().isNoContent());
    }

}
