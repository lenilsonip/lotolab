package br.com.lotolab.application;

import br.com.lotolab.domain.LogisticModelArtifact;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class LogisticModelArtifactCodec {
    private final ObjectMapper mapper;
    public LogisticModelArtifactCodec(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public String serialize(LogisticModelArtifact artifact) {
        try { return mapper.writeValueAsString(artifact); }
        catch (Exception exception) { throw new IllegalStateException("Não foi possível serializar o artefato ML", exception); }
    }

    public String checksum(LogisticModelArtifact artifact) { return sha256(serialize(artifact)); }

    public LogisticModelArtifact deserializeAndVerify(String json, String expectedChecksum) {
        try {
            LogisticModelArtifact artifact = mapper.readValue(json, LogisticModelArtifact.class);
            if (!MessageDigest.isEqual(checksum(artifact).getBytes(StandardCharsets.US_ASCII),
                    expectedChecksum.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Checksum do artefato ML inválido");
            }
            return artifact;
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("Artefato ML inválido ou incompatível", exception); }
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 indisponível", exception); }
    }
}
