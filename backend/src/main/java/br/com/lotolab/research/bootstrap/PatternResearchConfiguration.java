package br.com.lotolab.research.bootstrap;

import br.com.lotolab.research.application.port.in.GetLatestPatternInsightsUseCase;
import br.com.lotolab.research.application.port.in.GetLatestResearchInterpretationUseCase;
import br.com.lotolab.research.application.port.in.GetLatestResearchJobUseCase;
import br.com.lotolab.research.application.port.in.GetResearchJobForRunUseCase;
import br.com.lotolab.research.application.port.in.GetPatternRunInsightsUseCase;
import br.com.lotolab.research.application.port.in.MinePatternsUseCase;
import br.com.lotolab.research.application.port.out.PatternMiningCompletionPublisher;
import br.com.lotolab.research.application.port.out.PatternResearchRepository;
import br.com.lotolab.research.application.port.out.PatternResearchUnitOfWork;
import br.com.lotolab.research.application.port.out.ResearchContestHistory;
import br.com.lotolab.research.application.port.out.ResearchInterpretationQueries;
import br.com.lotolab.research.application.service.GetLatestPatternInsightsService;
import br.com.lotolab.research.application.service.GetLatestResearchInterpretationService;
import br.com.lotolab.research.application.service.GetLatestResearchJobService;
import br.com.lotolab.research.application.service.GetResearchJobForRunService;
import br.com.lotolab.research.application.service.GetPatternRunInsightsService;
import br.com.lotolab.research.application.service.RunPatternMiningService;
import br.com.lotolab.research.domain.PatternMiningAlgorithm;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PatternResearchConfiguration {
    @Bean PatternMiningAlgorithm patternMiningAlgorithm() { return new PatternMiningAlgorithm(); }

    @Bean MinePatternsUseCase minePatterns(ResearchContestHistory history, PatternResearchRepository runs,
                                           PatternResearchUnitOfWork unitOfWork,
                                           PatternMiningCompletionPublisher publisher,
                                           PatternMiningAlgorithm algorithm) {
        return new RunPatternMiningService(history, runs, unitOfWork, publisher, algorithm);
    }

    @Bean GetLatestPatternInsightsUseCase getLatestPatternInsights(PatternResearchRepository repository) {
        return new GetLatestPatternInsightsService(repository);
    }

    @Bean GetPatternRunInsightsUseCase getPatternRunInsights(PatternResearchRepository repository) {
        return new GetPatternRunInsightsService(repository);
    }

    @Bean GetLatestResearchInterpretationUseCase getLatestResearchInterpretation(ResearchInterpretationQueries queries) {
        return new GetLatestResearchInterpretationService(queries);
    }

    @Bean GetLatestResearchJobUseCase getLatestResearchJob(ResearchInterpretationQueries queries) {
        return new GetLatestResearchJobService(queries);
    }

    @Bean GetResearchJobForRunUseCase getResearchJobForRun(ResearchInterpretationQueries queries) {
        return new GetResearchJobForRunService(queries);
    }
}
