package br.com.finc2u.server.features.spreadsheetimport.scripts;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.spreadsheetimport.reader.CategoryRulesReader;
import br.com.finc2u.server.features.spreadsheetimport.reader.SpreadsheetReader;
import br.com.finc2u.server.features.spreadsheetimport.service.SpreadsheetImportService;
import br.com.finc2u.server.features.spreadsheetimport.vo.ImportOptions;
import br.com.finc2u.server.features.spreadsheetimport.vo.ImportResult;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetData;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpreadsheetImportRunnerTest {

    private static final Path SHEET = Path.of("planilha.xlsx");
    private static final Path RULES = Path.of("regras.txt");

    @Mock
    private UserService userService;
    @Mock
    private SpreadsheetReader reader;
    @Mock
    private CategoryRulesReader rulesReader;
    @Mock
    private SpreadsheetImportService importService;

    private final SpreadsheetData data = SpreadsheetData.of(List.of(), List.of(), List.of());
    private final UUID userId = UUID.randomUUID();

    private SpreadsheetImportRunner runner(SpreadsheetImportProperties properties) {
        return new SpreadsheetImportRunner(properties, userService, reader, rulesReader, importService);
    }

    private User user(String email) {
        var user = User.builder().name("Alguém").email(email).build();
        user.setId(userId);
        return user;
    }

    @Test
    void run_shouldImportForTheUserFoundByEmailIgnoringCase_withTheLocalOptions() {
        var properties = new SpreadsheetImportProperties(SHEET, " Ana@Exemplo.com ", YearMonth.of(2025, 6), 10, RULES, "Diversos");
        when(userService.getAll()).thenReturn(List.of(user("outro@exemplo.com"), user("ana@exemplo.com")));
        when(rulesReader.read(RULES)).thenReturn(Map.of("uber", "Transporte"));
        when(reader.read(SHEET, YearMonth.of(2025, 6))).thenReturn(data);
        when(importService.importFor(any(), any(), any())).thenReturn(new ImportResult(1, 2, 3, 4, 5));

        runner(properties).run();

        var options = ArgumentCaptor.forClass(ImportOptions.class);
        verify(importService).importFor(eq(userId), eq(data), options.capture());
        assertEquals("Transporte", options.getValue().categoryFor("Uber trip"));
        assertEquals("Diversos", options.getValue().categoryFor("qualquer coisa"));
        assertEquals(10, options.getValue().dueDay());
    }

    @Test
    void run_shouldUseTheDefaultOptions_whenThereIsNoRulesFileNorDueDay() {
        var properties = new SpreadsheetImportProperties(SHEET, "ana@exemplo.com", null, null, null, null);
        when(userService.getAll()).thenReturn(List.of(user("ana@exemplo.com")));
        when(reader.read(SHEET, YearMonth.of(2025, 4))).thenReturn(data);
        when(importService.importFor(any(), any(), any())).thenReturn(new ImportResult(0, 0, 0, 0, 0));

        runner(properties).run();

        var options = ArgumentCaptor.forClass(ImportOptions.class);
        verify(importService).importFor(eq(userId), eq(data), options.capture());
        assertEquals("Outros", options.getValue().categoryFor("qualquer coisa"));
        assertNull(options.getValue().dueDay());
        verifyNoInteractions(rulesReader);
    }

    @Test
    void run_shouldFail_whenNoUserHasTheEmail_withoutReadingTheSpreadsheet() {
        var properties = new SpreadsheetImportProperties(SHEET, "ninguem@exemplo.com", null, null, null, null);
        when(userService.getAll()).thenReturn(List.of(user("ana@exemplo.com")));

        assertThrows(ResourceNotFoundException.class, () -> runner(properties).run());

        verifyNoInteractions(reader, importService);
    }

    @Test
    void run_shouldFail_whenTheEmailIsMissing() {
        var withoutEmail = new SpreadsheetImportProperties(SHEET, null, null, null, null, null);
        var blankEmail = new SpreadsheetImportProperties(SHEET, "  ", null, null, null, null);

        assertThrows(BusinessException.class, () -> runner(withoutEmail).run());
        assertThrows(BusinessException.class, () -> runner(blankEmail).run());

        verifyNoInteractions(userService, reader, importService);
    }

    @Test
    void run_shouldNotImportAnything_whenTheRulesFileIsInvalid() {
        var properties = new SpreadsheetImportProperties(SHEET, "ana@exemplo.com", null, null, RULES, null);
        when(userService.getAll()).thenReturn(List.of(user("ana@exemplo.com")));
        when(rulesReader.read(RULES)).thenThrow(new BusinessException("linha 3"));

        assertThrows(BusinessException.class, () -> runner(properties).run());

        verifyNoInteractions(reader, importService);
    }

}
