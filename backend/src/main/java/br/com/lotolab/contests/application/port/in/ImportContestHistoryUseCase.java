package br.com.lotolab.contests.application.port.in;

import br.com.lotolab.contests.application.ContestImportReport;
import java.io.IOException;
import java.nio.file.Path;

public interface ImportContestHistoryUseCase {
    ContestImportReport importFile(Path path) throws IOException;
    ContestImportReport importFile(byte[] content) throws IOException;
}
