package br.com.lotolab.contests.application.port.in;

import br.com.lotolab.contests.application.ContestImportPreview;
import java.io.IOException;
import java.nio.file.Path;

public interface PreviewContestImportUseCase {
    ContestImportPreview previewFile(Path path) throws IOException;
    ContestImportPreview previewFile(byte[] content) throws IOException;
}
