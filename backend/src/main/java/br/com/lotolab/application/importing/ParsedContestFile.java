package br.com.lotolab.application.importing;

import br.com.lotolab.contests.application.ContestImportIssue;
import java.util.List;

public record ParsedContestFile(
        int linesRead,
        boolean structureValid,
        List<ParsedContestRow> contests,
        List<ContestImportIssue> rejectedRows,
        List<ContestImportIssue> warnings) {

    public ParsedContestFile {
        contests = List.copyOf(contests);
        rejectedRows = List.copyOf(rejectedRows);
        warnings = List.copyOf(warnings);
    }
}
