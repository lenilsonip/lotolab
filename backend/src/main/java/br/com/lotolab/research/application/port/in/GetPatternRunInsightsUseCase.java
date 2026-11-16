package br.com.lotolab.research.application.port.in;

import br.com.lotolab.domain.PatternInsight;
import java.util.List;

public interface GetPatternRunInsightsUseCase { List<PatternInsight> execute(long runId); }
