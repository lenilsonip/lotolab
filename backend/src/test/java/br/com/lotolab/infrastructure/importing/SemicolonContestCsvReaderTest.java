package br.com.lotolab.infrastructure.importing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SemicolonContestCsvReaderTest {

    private final SemicolonContestCsvReader reader = new SemicolonContestCsvReader();

    @Test
    void parsesExplicitCsvFormatAndRejectsOnlyInvalidRows(@TempDir Path directory) throws IOException {
        Path csv = directory.resolve("incremental.csv");
        String valid = "50001;12/03/2026;" + IntStream.rangeClosed(1, 15)
                .mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(";"));
        String invalidDuplicate = "50002;13/03/2026;1;1;3;4;5;6;7;8;9;10;11;12;13;14;15";
        Files.writeString(csv, header() + System.lineSeparator() + valid + System.lineSeparator()
                + invalidDuplicate + System.lineSeparator(), StandardCharsets.UTF_8);

        var parsed = reader.read(csv);

        assertThat(parsed.structureValid()).isTrue();
        assertThat(parsed.linesRead()).isEqualTo(2);
        assertThat(parsed.contests()).hasSize(1);
        assertThat(parsed.contests().getFirst().contest().drawDate().toString()).isEqualTo("2026-03-12");
        assertThat(parsed.rejectedRows()).singleElement()
                .satisfies(issue -> assertThat(issue.lineNumber()).isEqualTo(3));
    }

    private static String header() {
        String drawColumns = IntStream.rangeClosed(1, 15).mapToObj(number -> "bola " + number)
                .collect(java.util.stream.Collectors.joining(";"));
        return "Concurso;Data;" + drawColumns;
    }
}
