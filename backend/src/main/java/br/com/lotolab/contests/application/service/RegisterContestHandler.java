package br.com.lotolab.contests.application.service;

import br.com.lotolab.contests.application.port.in.ContestRegistrationResult;
import br.com.lotolab.contests.application.port.in.RegisterContestCommand;
import br.com.lotolab.contests.application.port.in.RegisterContestUseCase;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.contests.application.port.out.ContestResearchTrigger;
import br.com.lotolab.domain.Contest;

import java.util.Objects;

/** Handles manual registration; research is retried on an identical request and remains idempotent by run key. */
public final class RegisterContestHandler implements RegisterContestUseCase {
    private final PersistContestUseCase persistence;
    private final ContestResearchTrigger researchTrigger;

    public RegisterContestHandler(PersistContestUseCase persistence, ContestResearchTrigger researchTrigger) {
        this.persistence = Objects.requireNonNull(persistence);
        this.researchTrigger = Objects.requireNonNull(researchTrigger);
    }

    @Override
    public ContestRegistrationResult register(RegisterContestCommand command) {
        Objects.requireNonNull(command, "Contest command is required");
        Contest contest = new Contest(command.number(), command.drawDate(), command.drawnNumbers());
        boolean created = persistence.persist(contest);

        // Retry research after a prior post-commit failure, including on an idempotent POST.
        // The research use case uses an algorithm/range key, so completed runs are skipped safely.
        boolean researchTriggered = researchTrigger.trigger("CONTEST_REGISTERED");
        return new ContestRegistrationResult(contest, created, researchTriggered);
    }
}
