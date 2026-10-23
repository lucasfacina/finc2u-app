package br.com.finc2u.server.features.spreadsheetimport.reader;

import br.com.finc2u.server.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Lê o arquivo local com a regra "trecho da descrição = categoria", uma por linha (UTF-8, linhas em branco e iniciadas por `#`
 * são ignoradas). A ordem do arquivo é a ordem de prioridade: a primeira regra que casar vence. O arquivo é do usuário e fica
 * fora do Git, porque as descrições são da vida pessoal dele.
 */
@Component
public class CategoryRulesReader {

    private static final long MAX_BYTES = 1024 * 1024;

    public Map<String, String> read(Path file) {
        var isRegularFile = Files.isRegularFile(file);
        if (!isRegularFile)
            throw new BusinessException("O arquivo de regras de categoria não existe: " + file.getFileName());

        var lines = linesOf(file);
        var rules = new LinkedHashMap<String, String>();

        for (var number = 1; number <= lines.size(); number++) {
            var line = lines.get(number - 1).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            var parts = line.split("=", 2);
            var valid = parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank();
            if (!valid)
                throw new BusinessException("Regra inválida na linha " + number + " do arquivo de categorias: use `trecho = Categoria`.");

            rules.put(parts[0].trim(), parts[1].trim());
        }

        return rules;
    }

    private List<String> linesOf(Path file) {
        try {
            var size = Files.size(file);
            if (size > MAX_BYTES)
                throw new BusinessException("O arquivo de regras de categoria passa do tamanho máximo de 1 MB.");

            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException("Não foi possível ler o arquivo de regras de categoria.", exception);
        }
    }

}
