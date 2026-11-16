package br.com.lotolab.infrastructure.importing;

import br.com.lotolab.application.importing.ContestFileReader;
import br.com.lotolab.contests.application.ContestImportIssue;
import br.com.lotolab.application.importing.ParsedContestFile;
import br.com.lotolab.application.importing.ParsedContestRow;
import br.com.lotolab.domain.Contest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class SemicolonContestCsvReader implements ContestFileReader {

    private static final List<String> EXPECTED_HEADER = expectedHeader();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public ParsedContestFile read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    @Override
    public ParsedContestFile read(byte[] content) throws IOException {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return new ParsedContestFile(0, false, List.of(),
                    List.of(new ContestImportIssue(1, "Arquivo não está codificado em UTF-8 válido")), List.of());
        }
        List<String> lines = text.lines().toList();
        if (lines.isEmpty()) {
            return new ParsedContestFile(0, false, List.of(),
                    List.of(new ContestImportIssue(1, "Arquivo vazio: cabeçalho obrigatório ausente")), List.of());
        }

        String[] header = lines.getFirst().split(";", -1);
        if (!EXPECTED_HEADER.equals(List.of(header))) {
            return new ParsedContestFile(0, false, List.of(),
                    List.of(new ContestImportIssue(1, "Cabeçalho inválido; esperado: " + String.join(";", EXPECTED_HEADER))),
                    List.of());
        }

        List<ParsedContestRow> validRows = new ArrayList<>();
        List<ContestImportIssue> rejectedRows = new ArrayList<>();
        int dataLinesRead = lines.size() - 1;
        for (int index = 1; index < lines.size(); index++) {
            parseRow(lines.get(index), index + 1, validRows, rejectedRows);
        }

        List<ParsedContestRow> deduplicatedRows = resolveDuplicates(validRows, rejectedRows);
        List<ContestImportIssue> warnings = findDateAnomalies(deduplicatedRows);
        return new ParsedContestFile(dataLinesRead, true, deduplicatedRows, rejectedRows, warnings);
    }

    private void parseRow(String line, int lineNumber, List<ParsedContestRow> validRows,
                          List<ContestImportIssue> rejectedRows) {
        if (line.isBlank()) {
            rejectedRows.add(new ContestImportIssue(lineNumber, "Linha vazia"));
            return;
        }
        if (line.indexOf('"') >= 0) {
            rejectedRows.add(new ContestImportIssue(lineNumber,
                    "Aspas não são usadas no formato observado e não são aceitas neste CSV"));
            return;
        }

        String[] fields = line.split(";", -1);
        if (fields.length != EXPECTED_HEADER.size()) {
            rejectedRows.add(new ContestImportIssue(lineNumber,
                    "Quantidade de colunas inválida: esperado 17, recebido " + fields.length));
            return;
        }

        try {
            int contestNumber = parseInteger(fields[0], "Concurso");
            LocalDate drawDate = parseDate(fields[1]);
            List<Integer> drawnNumbers = IntStream.range(2, 17)
                    .mapToObj(column -> parseInteger(fields[column], EXPECTED_HEADER.get(column)))
                    .toList();
            validRows.add(new ParsedContestRow(lineNumber, new Contest(contestNumber, drawDate, drawnNumbers)));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            rejectedRows.add(new ContestImportIssue(lineNumber, exception.getMessage()));
        }
    }

    private int parseInteger(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " deve ser um inteiro", exception);
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new DateTimeParseException("Data deve seguir dd/MM/yyyy", value, exception.getErrorIndex(), exception);
        }
    }

    private List<ParsedContestRow> resolveDuplicates(List<ParsedContestRow> rows,
                                                     List<ContestImportIssue> rejectedRows) {
        Map<Integer, List<ParsedContestRow>> byContestNumber = new LinkedHashMap<>();
        rows.forEach(row -> byContestNumber.computeIfAbsent(row.contest().number(), ignored -> new ArrayList<>())
                .add(row));

        List<ParsedContestRow> accepted = new ArrayList<>();
        for (List<ParsedContestRow> duplicates : byContestNumber.values()) {
            if (duplicates.size() == 1) {
                accepted.add(duplicates.getFirst());
            } else if (duplicates.stream().map(ParsedContestRow::contest).distinct().count() == 1) {
                accepted.add(duplicates.getFirst());
                duplicates.stream().skip(1).forEach(row -> rejectedRows.add(new ContestImportIssue(row.lineNumber(),
                        "Concurso duplicado no arquivo com conteúdo idêntico à linha "
                                + duplicates.getFirst().lineNumber())));
            } else {
                String duplicateLines = duplicates.stream().map(row -> Integer.toString(row.lineNumber()))
                        .reduce((left, right) -> left + ", " + right).orElse("");
                duplicates.forEach(row -> rejectedRows.add(new ContestImportIssue(row.lineNumber(),
                        "Número de concurso duplicado com dados divergentes nas linhas " + duplicateLines)));
            }
        }
        return accepted.stream().sorted(Comparator.comparingInt(ParsedContestRow::lineNumber)).toList();
    }

    private List<ContestImportIssue> findDateAnomalies(List<ParsedContestRow> rows) {
        List<ContestImportIssue> warnings = new ArrayList<>();
        List<ParsedContestRow> byNumber = rows.stream()
                .sorted(Comparator.comparingInt(row -> row.contest().number()))
                .toList();

        Map<LocalDate, List<ParsedContestRow>> byDate = new HashMap<>();
        byNumber.forEach(row -> byDate.computeIfAbsent(row.contest().drawDate(), ignored -> new ArrayList<>()).add(row));
        byDate.values().stream().filter(group -> group.size() > 1).forEach(group -> {
            String numbers = group.stream().map(row -> Integer.toString(row.contest().number()))
                    .reduce((left, right) -> left + ", " + right).orElse("");
            group.forEach(row -> warnings.add(new ContestImportIssue(row.lineNumber(),
                    "Data do sorteio compartilhada pelos concursos " + numbers + "; valor preservado sem alteração")));
        });

        Map<List<Integer>, List<ParsedContestRow>> byDrawnNumbers = new HashMap<>();
        byNumber.forEach(row -> byDrawnNumbers.computeIfAbsent(row.contest().drawnNumbers(),
                ignored -> new ArrayList<>()).add(row));
        byDrawnNumbers.values().stream().filter(group -> group.size() > 1).forEach(group -> {
            String numbers = group.stream().map(row -> Integer.toString(row.contest().number()))
                    .reduce((left, right) -> left + ", " + right).orElse("");
            group.forEach(row -> warnings.add(new ContestImportIssue(row.lineNumber(),
                    "Resultado repetido nos concursos " + numbers + "; dezenas preservadas sem alteração")));
        });

        for (int index = 1; index < byNumber.size(); index++) {
            ParsedContestRow previous = byNumber.get(index - 1);
            ParsedContestRow current = byNumber.get(index);
            if (current.contest().drawDate().isBefore(previous.contest().drawDate())) {
                warnings.add(new ContestImportIssue(current.lineNumber(),
                        "Data anterior à do concurso imediatamente anterior ("
                                + previous.contest().number() + "); valor preservado sem alteração"));
            }
        }
        return warnings.stream().distinct().sorted(Comparator.comparingInt(ContestImportIssue::lineNumber)).toList();
    }

    private static List<String> expectedHeader() {
        List<String> columns = new ArrayList<>(List.of("Concurso", "Data"));
        IntStream.rangeClosed(1, Contest.DRAWN_NUMBER_COUNT).mapToObj(number -> "bola " + number)
                .forEach(columns::add);
        return List.copyOf(columns);
    }
}
