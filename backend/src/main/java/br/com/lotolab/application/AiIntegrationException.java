package br.com.lotolab.application;

public class AiIntegrationException extends RuntimeException {
    private final boolean rateLimited;
    private final boolean temporarilyUnavailable;

    public AiIntegrationException(String message) { this(message, null, false, false); }
    public AiIntegrationException(String message, Throwable cause) { this(message, cause, false, false); }

    private AiIntegrationException(String message, Throwable cause, boolean rateLimited, boolean temporarilyUnavailable) {
        super(message, cause);
        this.rateLimited = rateLimited;
        this.temporarilyUnavailable = temporarilyUnavailable;
    }

    public static AiIntegrationException rateLimited(Throwable cause) {
        return new AiIntegrationException("O limite de solicitações Gemini foi atingido", cause, true, false);
    }

    public static AiIntegrationException temporarilyUnavailable(Throwable cause) {
        return new AiIntegrationException("O serviço Gemini está temporariamente indisponível", cause, false, true);
    }

    public boolean rateLimited() { return rateLimited; }
    public boolean temporarilyUnavailable() { return temporarilyUnavailable; }
}