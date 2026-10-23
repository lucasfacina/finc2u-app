package br.com.finc2u.server.features.spreadsheetimport.scripts;

import br.com.finc2u.server.features.spreadsheetimport.reader.CategoryRulesReader;
import br.com.finc2u.server.features.spreadsheetimport.reader.SpreadsheetReader;
import br.com.finc2u.server.features.spreadsheetimport.service.SpreadsheetImportService;
import br.com.finc2u.server.features.spreadsheetimport.vo.ImportResult;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetData;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpreadsheetImportRunnerWiringTest {

    private static final Path SHEET = Path.of("/tmp/planilha.xlsx");

    private final UserService userService = mock(UserService.class);
    private final SpreadsheetReader reader = mock(SpreadsheetReader.class);
    private final SpreadsheetImportService importService = mock(SpreadsheetImportService.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SpreadsheetImportRunner.class)
        .withBean(UserService.class, () -> userService)
        .withBean(SpreadsheetReader.class, () -> reader)
        .withBean(CategoryRulesReader.class, () -> mock(CategoryRulesReader.class))
        .withBean(SpreadsheetImportService.class, () -> importService);

    @Test
    void shouldNotCreateTheRunner_withoutTheSpreadsheetProperty() {
        contextRunner.withPropertyValues("app.import.email=ana@exemplo.com").run(context -> {
            assertThat(context).doesNotHaveBean(SpreadsheetImportRunner.class);
            assertThat(context).doesNotHaveBean(SpreadsheetImportProperties.class);
        });
    }

    @Test
    void shouldBindThePropertiesAndImportWhenTheApplicationIsReady() {
        var user = User.builder().name("Ana").email("ana@exemplo.com").build();
        user.setId(UUID.randomUUID());
        when(userService.getAll()).thenReturn(List.of(user));
        when(reader.read(any(), any())).thenReturn(SpreadsheetData.of(List.of(), List.of(), List.of()));
        when(importService.importFor(any(), any(), any())).thenReturn(new ImportResult(0, 0, 0, 0, 0));

        contextRunner.withPropertyValues(
            "app.import.planilha=/tmp/planilha.xlsx",
            "app.import.email=ana@exemplo.com",
            "app.import.primeiro-mes=2025-07",
            "app.import.dia-vencimento=5",
            "app.import.categoria-padrao=Diversos"
        ).run(context -> {
            assertThat(context).hasSingleBean(SpreadsheetImportRunner.class);
            var properties = context.getBean(SpreadsheetImportProperties.class);
            assertEquals(SHEET, properties.spreadsheet());
            assertEquals(YearMonth.of(2025, 7), properties.firstMonth());
            assertEquals(5, properties.dueDay());
            assertEquals("Diversos", properties.defaultCategory());
            verifyNoInteractions(reader, importService);

            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

            verify(reader).read(SHEET, YearMonth.of(2025, 7));
            verify(importService).importFor(eq(user.getId()), any(), any());
        });
    }

    @Test
    void shouldDefaultTheFirstMonthToApril2025() {
        contextRunner.withPropertyValues("app.import.planilha=/tmp/planilha.xlsx", "app.import.email=ana@exemplo.com").run(context ->
            assertEquals(YearMonth.of(2025, 4), context.getBean(SpreadsheetImportProperties.class).firstMonth()));
    }

}
