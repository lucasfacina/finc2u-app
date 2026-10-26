package br.com.finc2u.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Lê a especificação real em /v3/api-docs e confere que a API está documentada por inteiro: cada endpoint com resumo,
 * descrição, `operationId` único e tag; cada parâmetro e cada campo com descrição; e os erros no formato do `ErrorResponse`.
 * Os erros de regra (404, 409, 422) só aparecem onde o código os lança de fato: as conferências de fidelidade estão no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsIntegrationTest {

    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete");

    private record Endpoint(String path, String method, JsonNode node) {
        String label() {
            return method.toUpperCase() + " " + path;
        }

        boolean hasResponse(String code) {
            return node.path("responses").has(code);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();
    private JsonNode docs;
    private List<Endpoint> endpoints;

    @BeforeEach
    void loadSpecification() throws Exception {
        var body = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        docs = json.readTree(body);
        endpoints = new ArrayList<>();
        docs.path("paths").properties().forEach(path ->
            HTTP_METHODS.stream()
                .filter(method -> path.getValue().has(method))
                .forEach(method -> endpoints.add(new Endpoint(path.getKey(), method, path.getValue().get(method)))));
    }

    private Endpoint endpoint(String method, String path) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.method().equals(method) && endpoint.path().equals(path))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Endpoint ausente na especificação: " + method.toUpperCase() + " " + path));
    }

    @Test
    void specification_shouldDocumentEveryProductionEndpoint_andNothingElse() {
        assertEquals(45, endpoints.size(), "endpoints documentados");
        assertTrue(endpoints.stream().noneMatch(endpoint -> endpoint.path().startsWith("/test")), "controllers de teste vazaram");
    }

    @Test
    void everyEndpoint_shouldHaveSummaryDescriptionTagAndUniqueOperationId() {
        var ids = new HashSet<String>();
        for (var endpoint : endpoints) {
            assertFalse(endpoint.node().path("summary").asString("").isBlank(), "sem resumo: " + endpoint.label());
            assertFalse(endpoint.node().path("description").asString("").isBlank(), "sem descrição: " + endpoint.label());
            assertEquals(1, endpoint.node().path("tags").size(), "deve ter exatamente uma tag: " + endpoint.label());

            var id = endpoint.node().path("operationId").asString("");
            assertFalse(id.isBlank(), "sem operationId: " + endpoint.label());
            assertFalse(id.matches(".*_\\d+$"), "operationId gerado automaticamente (sufixo numérico): " + endpoint.label() + " -> " + id);
            assertTrue(ids.add(id), "operationId repetido: " + id);
        }
    }

    @Test
    void everyTag_shouldBeDescribed() {
        var described = new HashSet<String>();
        docs.path("tags").forEach(tag -> {
            assertFalse(tag.path("description").asString("").isBlank(), "tag sem descrição: " + tag.path("name").asString());
            described.add(tag.path("name").asString());
        });
        endpoints.forEach(endpoint -> assertTrue(described.contains(endpoint.node().path("tags").get(0).asString()),
            "tag sem descrição para " + endpoint.label()));
    }

    @Test
    void everyParameter_shouldHaveADescription() {
        for (var endpoint : endpoints) {
            endpoint.node().path("parameters").forEach(parameter -> assertFalse(
                parameter.path("description").asString("").isBlank(),
                "parâmetro sem descrição: " + parameter.path("name").asString() + " em " + endpoint.label()));
        }
    }

    @Test
    void filterEndpoint_shouldExposeOneParameterPerFilterField_notAnObject() {
        var names = new HashSet<String>();
        endpoint("get", "/expenses/filters").node().path("parameters").forEach(parameter -> names.add(parameter.path("name").asString()));

        assertEquals(Set.of("userId", "month", "year", "status", "type", "cardAccountId", "categoryId", "minPrice", "maxPrice", "overdue"), names);
    }

    @Test
    void everySchemaField_shouldHaveADescription() {
        docs.path("components").path("schemas").properties().forEach(schema ->
            schema.getValue().path("properties").properties().forEach(property -> {
                var description = property.getValue().path("description").asString("");
                if (description.isBlank() && property.getValue().has("$ref")) {
                    var target = property.getValue().get("$ref").asString();
                    description = docs.path("components").path("schemas").path(target.substring(target.lastIndexOf('/') + 1))
                        .path("description").asString("");
                }
                assertFalse(description.isBlank(), "campo sem descrição: " + schema.getKey() + "." + property.getKey());
            }));
    }

    @Test
    void everyEndpoint_shouldDocument500_andTheValidationError400_whenItReceivesAnything() {
        for (var endpoint : endpoints) {
            assertTrue(endpoint.hasResponse("500"), "sem 500: " + endpoint.label());

            var receivesInput = !endpoint.node().path("parameters").isEmpty() || endpoint.node().has("requestBody");
            assertEquals(receivesInput, endpoint.hasResponse("400"), "400 só onde há entrada: " + endpoint.label());
        }
    }

    @Test
    void everyErrorResponse_shouldUseTheErrorResponseSchemaAsJson() {
        for (var endpoint : endpoints) {
            endpoint.node().path("responses").properties().stream()
                .filter(response -> Integer.parseInt(response.getKey()) >= 400)
                .forEach(response -> {
                    var schema = response.getValue().path("content").path("application/json").path("schema").path("$ref").asString("");
                    assertTrue(schema.endsWith("/ErrorResponse"), "erro " + response.getKey() + " sem o schema ErrorResponse: " + endpoint.label());
                    assertFalse(response.getValue().path("description").asString("").isBlank(), "erro sem descrição: " + endpoint.label());
                });
        }
    }

    @Test
    void errorResponseSchema_shouldDescribeItsFourFields() {
        var properties = docs.path("components").path("schemas").path("ErrorResponse").path("properties");

        assertEquals(Set.of("status", "message", "detail", "timestamp"), Set.copyOf(properties.propertyNames()));
    }

    @Test
    void successStatus_shouldMatchWhatTheControllerReturns() {
        for (var endpoint : endpoints) {
            var isMonthlySummaryCalculation = endpoint.method().equals("post") && endpoint.path().equals("/monthly-summary");
            if (isMonthlySummaryCalculation) {
                assertTrue(endpoint.hasResponse("201") && endpoint.hasResponse("200"), "o cálculo do resumo cria (201) ou recalcula (200)");
            } else if (endpoint.method().equals("post")) {
                assertTrue(endpoint.hasResponse("201") && !endpoint.hasResponse("200"), "criação devolve 201: " + endpoint.label());
            } else if (endpoint.method().equals("delete")) {
                assertTrue(endpoint.hasResponse("204") && !endpoint.hasResponse("200"), "exclusão devolve 204: " + endpoint.label());
            } else {
                assertTrue(endpoint.hasResponse("200"), "leitura/alteração devolve 200: " + endpoint.label());
            }
        }
    }

    @Test
    void ruleErrors_shouldAppearOnlyWhereTheCodeThrowsThem() {
        // lançam 404 (usuário, cartão, categoria ou recurso não encontrado) e 422 (regra de negócio)
        assertTrue(endpoint("post", "/expenses").hasResponse("404"));
        assertTrue(endpoint("post", "/expenses").hasResponse("422"));
        assertTrue(endpoint("patch", "/expenses/{id}/status").hasResponse("422"));
        assertTrue(endpoint("post", "/users").hasResponse("422"));
        assertTrue(endpoint("post", "/salary-history").hasResponse("422"));
        assertTrue(endpoint("get", "/monthly-summary/{userId}/{year}/{month}").hasResponse("404"));
        assertTrue(endpoint("get", "/salary-history").hasResponse("404"), "listar vigências confere o usuário");
        assertTrue(endpoint("get", "/monthly-summary/history").hasResponse("404"), "o histórico confere o usuário");
        assertTrue(endpoint("get", "/expenses/projections").hasResponse("404"), "a projeção confere o usuário");

        // apagar o que tem dependentes é recusado pelo banco (409)
        assertTrue(endpoint("delete", "/categories/{id}").hasResponse("409"));
        assertTrue(endpoint("delete", "/card-accounts/{id}").hasResponse("409"));
        assertTrue(endpoint("delete", "/users/{id}").hasResponse("409"));

        // o código nunca lança estes: documentá-los seria inventar
        assertFalse(endpoint("post", "/extra-incomes").hasResponse("422"), "ExtraIncomeService não tem regra de negócio");
        assertFalse(endpoint("delete", "/expenses/{id}").hasResponse("409"), "apagar despesa não tem dependentes");
        assertFalse(endpoint("get", "/users").hasResponse("400"), "listar usuários não recebe entrada");
    }

}
