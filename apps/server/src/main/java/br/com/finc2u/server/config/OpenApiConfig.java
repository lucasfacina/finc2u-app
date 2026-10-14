package br.com.finc2u.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Metadados da documentação OpenAPI. Swagger UI em /swagger-ui.html e especificação em /v3/api-docs.
@Configuration
public class OpenApiConfig {

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

}
