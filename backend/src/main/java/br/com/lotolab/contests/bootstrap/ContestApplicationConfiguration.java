package br.com.lotolab.contests.bootstrap;

import br.com.lotolab.application.RegisterContestService;
import br.com.lotolab.application.GetContestService;
import br.com.lotolab.application.GetLatestContestService;
import br.com.lotolab.application.ListContestsService;
import br.com.lotolab.application.importing.ContestFileReader;
import br.com.lotolab.application.importing.HistoricalContestImportService;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.contests.application.port.in.RegisterContestUseCase;
import br.com.lotolab.contests.application.port.in.GetContestUseCase;
import br.com.lotolab.contests.application.port.in.GetLatestContestUseCase;
import br.com.lotolab.contests.application.port.in.ListContestsUseCase;
import br.com.lotolab.contests.application.port.out.ContestRepository;
import br.com.lotolab.contests.application.port.out.ContestResearchTrigger;
import br.com.lotolab.contests.application.port.out.ContestResultEventPublisher;
import br.com.lotolab.contests.application.port.out.ContestUnitOfWork;
import br.com.lotolab.contests.application.port.out.PendingPredictionEvaluator;
import br.com.lotolab.contests.application.service.GetContestHandler;
import br.com.lotolab.contests.application.service.GetLatestContestHandler;
import br.com.lotolab.contests.application.service.ListContestsHandler;
import br.com.lotolab.contests.application.service.PersistContestService;
import br.com.lotolab.contests.application.service.RegisterContestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ContestApplicationConfiguration {
    @Bean
    PersistContestUseCase persistContest(ContestRepository contests,
                                         PendingPredictionEvaluator predictionEvaluator,
                                         ContestResultEventPublisher eventPublisher,
                                         ContestUnitOfWork unitOfWork) {
        return new PersistContestService(contests, predictionEvaluator, eventPublisher, unitOfWork);
    }

    @Bean RegisterContestUseCase registerContest(PersistContestUseCase persistence,
                                                  ContestResearchTrigger researchTrigger) {
        return new RegisterContestHandler(persistence, researchTrigger);
    }

    @Bean GetContestUseCase getContest(ContestRepository contests) {
        return new GetContestHandler(contests);
    }

    @Bean GetLatestContestUseCase getLatestContest(ContestRepository contests) {
        return new GetLatestContestHandler(contests);
    }

    @Bean ListContestsUseCase listContests(ContestRepository contests) {
        return new ListContestsHandler(contests);
    }

    @Bean RegisterContestService legacyRegisterContest(RegisterContestUseCase registerContest) {
        return new RegisterContestService(registerContest);
    }

    @Bean GetContestService legacyGetContest(GetContestUseCase getContest) {
        return new GetContestService(getContest);
    }

    @Bean GetLatestContestService legacyGetLatestContest(
            GetLatestContestUseCase getLatestContest) {
        return new GetLatestContestService(getLatestContest);
    }

    @Bean ListContestsService legacyListContests(
            ListContestsUseCase listContests) {
        return new ListContestsService(listContests);
    }

    @Bean
    HistoricalContestImportService historicalContestImportService(ContestFileReader fileReader,
                                                                    ContestRepository contests,
                                                                    PersistContestUseCase persistence,
                                                                    ContestResearchTrigger researchTrigger) {
        return new HistoricalContestImportService(fileReader, contests, persistence, researchTrigger);
    }
}
