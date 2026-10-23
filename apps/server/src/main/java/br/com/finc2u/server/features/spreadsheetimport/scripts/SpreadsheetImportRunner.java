package br.com.finc2u.server.features.spreadsheetimport.scripts;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.spreadsheetimport.reader.CategoryRulesReader;
import br.com.finc2u.server.features.spreadsheetimport.reader.SpreadsheetReader;
import br.com.finc2u.server.features.spreadsheetimport.service.SpreadsheetImportService;
import br.com.finc2u.server.features.spreadsheetimport.vo.ImportOptions;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/*
 * Carrega a planilha pessoal do autor no banco de dev, para o usuário informado em `app.import.email`. Só existe com a propriedade
 * local `app.import.planilha` (sem ela o bean nem é criado: nada roda em teste nem em produção). Dispara no `ApplicationReadyEvent`,
 * depois de todos os `CommandLineRunner` (o `DatabaseSeeder` inclusive), para o usuário de dev já existir. Falhou: a subida falha, com a
 * causa no log. O importador é idempotente, então subir de novo não duplica nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SpreadsheetImportProperties.class)
@ConditionalOnProperty(name = "app.import.planilha")
public class SpreadsheetImportRunner {

    private final SpreadsheetImportProperties properties;
    private final UserService userService;
    private final SpreadsheetReader reader;
    private final CategoryRulesReader rulesReader;
    private final SpreadsheetImportService importService;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        var email = properties.email();
        if (email == null || email.isBlank())
            throw new BusinessException("Informe o e-mail do usuário em app.import.email.");

        var user = userService.getAll().stream()
            .filter(candidate -> email.trim().equalsIgnoreCase(candidate.getEmail()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com o e-mail: " + email.trim()));
        var rules = properties.categoryRules() == null
            ? Map.<String, String>of()
            : rulesReader.read(properties.categoryRules());
        var options = ImportOptions.of(rules, properties.defaultCategory(), properties.dueDay());

        log.info("📥 Importando a planilha a partir de {}...", properties.firstMonth());
        var data = reader.read(properties.spreadsheet(), properties.firstMonth());
        var result = importService.importFor(user.getId(), data, options);

        log.info("✅ Planilha importada: {} cartões, {} modelos recorrentes, {} despesas e {} rendas novos; {} já existiam.",
            result.createdCards(), result.createdRecurringExpenses(), result.createdExpenses(), result.createdExtraIncomes(), result.alreadyThere());
    }

}
