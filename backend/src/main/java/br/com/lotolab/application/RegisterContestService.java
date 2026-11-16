package br.com.lotolab.application;

import br.com.lotolab.contests.application.port.in.RegisterContestCommand;
import br.com.lotolab.contests.application.port.in.RegisterContestUseCase;
import br.com.lotolab.domain.Contest;

/** Compatibility facade for older Java callers; adapters use the explicit input ports directly. */
public class RegisterContestService {
    private final RegisterContestUseCase registerContest;

    public RegisterContestService(RegisterContestUseCase registerContest) {
        this.registerContest = java.util.Objects.requireNonNull(registerContest);
    }

    public ContestRegistrationStatus register(Contest contest) {
        var result = registerContest.register(new RegisterContestCommand(
                contest.number(), contest.drawDate(), contest.drawnNumbers()));
        return result.created() ? ContestRegistrationStatus.CREATED : ContestRegistrationStatus.ALREADY_IMPORTED;
    }
}
