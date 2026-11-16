package br.com.lotolab.contests.application;

import java.util.List;

/** Read-only classification of a CSV against the contests currently persisted. */
public record ContestImportPreview(
        int linesRead,
        int validLines,
        int wouldInsert,
        int alreadyExisting,
        int conflictLines,
        int rejectedLines,
        boolean fileStructureRejected,
        List<ContestImportIssue> rejections,
        List<ContestImportIssue> conflicts,
        List<ContestImportIssue> warnings) {

    public ContestImportPreview {
        rejections = List.copyOf(rejections);
        conflicts = List.copyOf(conflicts);
        warnings = List.copyOf(warnings);
    }

    public String summary() {
        return "Prévia somente leitura — linhas lidas: %d; válidas: %d; seriam inseridos: %d; "
                .formatted(linesRead, validLines, wouldInsert)
                + "já existentes e idênticos: %d; conflitos: %d; rejeitadas: %d; avisos: %d; "
                .formatted(alreadyExisting, conflictLines, rejectedLines, warnings.size())
                + "estrutura rejeitada: %s".formatted(fileStructureRejected);
    }
}
