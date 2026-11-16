package br.com.lotolab.research.application.port.in;

import br.com.lotolab.domain.AgentInterpretation;
import java.util.Optional;

public interface GetLatestResearchInterpretationUseCase { Optional<AgentInterpretation> execute(); }
