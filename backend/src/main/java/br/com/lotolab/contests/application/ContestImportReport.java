package br.com.lotolab.contests.application;

import java.util.List;

public record ContestImportReport(
        int linesRead,
        int newContests,
        int alreadyExisting,
        int rejectedLines,
        int conflictLines,
        int notProcessedLines,
        boolean fileStructureRejected,
        boolean persistenceAborted,
        List<ContestImportIssue> rejections,
        List<ContestImportIssue> conflicts,
        List<ContestImportIssue> warnings) {

    public ContestImportReport {
        rejections = List.copyOf(rejections);
        conflicts = List.copyOf(conflicts);
        warnings = List.copyOf(warnings);
    }

    public String summary() {
        return "Linhas lidas: %d; válidas: %d; concursos inseridos: %d; já existentes: %d; "
                .formatted(linesRead, Math.max(0, linesRead - rejectedLines), newContests, alreadyExisting)
                + "linhas rejeitadas: %d; conflitos: %d; avisos: %d; "
                .formatted(rejectedLines, conflictLines, warnings.size())
                + "linhas válidas não processadas: %d; estrutura do arquivo rejeitada: %s; "
                .formatted(notProcessedLines, fileStructureRejected)
                + "interrupção por persistência: %s".formatted(persistenceAborted);
    }
}
