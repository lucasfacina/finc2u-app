package br.com.finc2u.server.features.spreadsheetimport.reader;

import br.com.finc2u.server.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryRulesReaderTest {

    @TempDir
    Path dir;

    private final CategoryRulesReader reader = new CategoryRulesReader();

    private Path file(String content) throws IOException {
        return Files.writeString(dir.resolve("regras.txt"), content, StandardCharsets.UTF_8);
    }

    @Test
    void read_shouldKeepTheFileOrderTrimAndIgnoreCommentsAndBlankLines() throws IOException {
        var rules = reader.read(file("""
            # trecho = categoria

            uber = Transporte
              Farmácia   =   Saúde\s\s
            #mercado = Ignorada
            mercado = Alimentação
            """));

        assertEquals(List.of("uber", "Farmácia", "mercado"), List.copyOf(rules.keySet()));
        assertEquals("Transporte", rules.get("uber"));
        assertEquals("Saúde", rules.get("Farmácia"));
        assertEquals("Alimentação", rules.get("mercado"));
    }

    @Test
    void read_shouldSplitOnlyOnTheFirstEqualsSign() throws IOException {
        var rules = reader.read(file("a=b = c\n"));

        assertEquals("b = c", rules.get("a"));
    }

    @Test
    void read_shouldRejectALineWithoutKeywordOrCategory_namingTheLine() {
        var withoutCategory = assertThrows(BusinessException.class, () -> reader.read(file("uber = Transporte\nifood =\n")));
        var withoutSeparator = assertThrows(BusinessException.class, () -> reader.read(file("só um texto\n")));
        var withoutKeyword = assertThrows(BusinessException.class, () -> reader.read(file("= Lazer\n")));

        assertTrue(withoutCategory.getMessage().contains("linha 2"));
        assertTrue(withoutSeparator.getMessage().contains("linha 1"));
        assertTrue(withoutKeyword.getMessage().contains("linha 1"));
    }

    @Test
    void read_shouldRejectAMissingFile() {
        assertThrows(BusinessException.class, () -> reader.read(dir.resolve("nao-existe.txt")));
    }

    @Test
    void read_shouldRejectAFileOverTheSizeLimit() throws IOException {
        var big = dir.resolve("grande.txt");
        Files.write(big, new byte[1024 * 1024 + 1]);

        assertThrows(BusinessException.class, () -> reader.read(big));
    }

    @Test
    void read_shouldWrapAnUnreadableFileKeepingTheCause() throws IOException {
        var latin1 = dir.resolve("latin1.txt");
        Files.write(latin1, new byte[]{(byte) 0xE7, (byte) 0xE3, '=', 'x'});

        var exception = assertThrows(BusinessException.class, () -> reader.read(latin1));

        assertInstanceOf(IOException.class, exception.getCause());
    }

}
