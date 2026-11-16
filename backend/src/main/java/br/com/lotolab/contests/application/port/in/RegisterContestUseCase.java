package br.com.lotolab.contests.application.port.in;

public interface RegisterContestUseCase {
    ContestRegistrationResult register(RegisterContestCommand command);
}
