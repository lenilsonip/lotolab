package br.com.lotolab.application.importing;

import java.io.IOException;
import java.nio.file.Path;

public interface ContestFileReader {

    ParsedContestFile read(Path path) throws IOException;

    ParsedContestFile read(byte[] content) throws IOException;
}
