package br.com.finc2u.server.config;

import br.com.finc2u.server.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

// Metadados da documentação OpenAPI. Swagger UI em /swagger-ui.html e especificação em /v3/api-docs.
@Configuration
public class OpenApiConfig {

    private static final String JSON = "application/json";
    private static final String ERROR_SCHEMA = "ErrorResponse";

    // Descrição dos parâmetros que se repetem em vários endpoints (o que é específico de um endpoint vai no `@Parameter` dele).
    private static final Map<String, String> PARAMETER_DESCRIPTIONS = Map.of(
        "userId", "Id do usuário dono dos dados.",
        "groupId", "Id do grupo da compra parcelada (o `installmentGroupId` das parcelas).",
        "year", "Ano do resumo (a partir de 2000).",
        "month", "Mês do resumo (1 a 12).",
        "name", "Trecho do nome do cartão (busca parcial, sem diferenciar maiúsculas de minúsculas); omitido, devolve todos.",
        "startDate", "Primeiro dia do período, inclusive (AAAA-MM-DD).",
        "endDate", "Último dia do período, inclusive (AAAA-MM-DD).",
        "status", "Novo status de pagamento: PENDING ou PAID.",
        "paidAt", "Data do pagamento (AAAA-MM-DD). Só vale com PAID e não pode ser futura; omitida com PAID, vale hoje."
    );

    // O `id` da URL é sempre o do recurso do controller: o substantivo sai do início do caminho.
    private static final Map<String, String> ID_OWNERS = Map.of(
        "/expenses", "da despesa",
        "/users", "do usuário",
        "/card-accounts", "do cartão",
        "/categories", "da categoria",
        "/recurring-expenses", "da despesa recorrente",
        "/salary-history", "da vigência de salário",
        "/extra-incomes", "do ganho extra"
    );

    @Bean
    public OpenAPI finc2uOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Finc2U-App API")
                .version("v1")
                .description("API REST do Finc2U-App — gestão de finanças pessoais com sugestões de educação " +
                    "financeira por motor de regras.")
            );
    }

    /*
     * Completa o que se repete em toda a API, para não repetir à mão em 45 endpoints: o schema do `ErrorResponse`, o 400 (toda
     * entrada inválida) e o 500, o corpo JSON dos erros e das respostas, e a descrição dos parâmetros comuns. Os erros de regra
     * (404, 409, 422) NÃO são gerais: cada endpoint declara só os que o código lança de fato.
     */
    @Bean
    public OpenApiCustomizer commonDocumentationCustomizer() {
        return openApi -> {
            var errorSchema = registerErrorSchema(openApi);

            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                describeParameters(path, operation);
                useJsonInSuccessResponses(operation);
                addCommonErrors(operation);
                useErrorSchemaInErrorResponses(operation, errorSchema);
            }));
        };
    }

    private Schema<?> registerErrorSchema(OpenAPI openApi) {
        ModelConverters.getInstance(true).readAll(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
        return new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
    }

    private void describeParameters(String path, Operation operation) {
        if (operation.getParameters() == null) return;

        for (var parameter : operation.getParameters()) {
            if (parameter.getDescription() != null && !parameter.getDescription().isBlank()) continue;

            parameter.setDescription(descriptionOf(path, parameter));
        }
    }

    private String descriptionOf(String path, Parameter parameter) {
        if ("id".equals(parameter.getName())) {
            var owner = ID_OWNERS.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("do recurso");
            return "Id " + owner + ".";
        }

        return PARAMETER_DESCRIPTIONS.get(parameter.getName());
    }

    private void useJsonInSuccessResponses(Operation operation) {
        operation.getResponses().forEach((code, response) -> {
            if (code.startsWith("2") && "OK".equals(response.getDescription())) response.setDescription("Sucesso.");

            var content = response.getContent();
            if (content == null || !content.containsKey("*/*")) return;

            content.addMediaType(JSON, content.remove("*/*"));
        });
    }

    private void addCommonErrors(Operation operation) {
        var receivesInput = (operation.getParameters() != null && !operation.getParameters().isEmpty()) || operation.getRequestBody() != null;
        if (receivesInput) {
            operation.getResponses().putIfAbsent("400", new ApiResponse().description(
                "Entrada inválida: campo ou parâmetro ausente, malformado ou fora das regras de validação."));
        }

        operation.getResponses().putIfAbsent("500", new ApiResponse().description("Erro inesperado no servidor."));
    }

    private void useErrorSchemaInErrorResponses(Operation operation, Schema<?> errorSchema) {
        operation.getResponses().forEach((code, response) -> {
            if (!code.matches("[45]\\d\\d")) return;

            response.setContent(new Content().addMediaType(JSON, new MediaType().schema(errorSchema)));
        });
    }

}
