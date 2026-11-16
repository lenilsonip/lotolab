package br.com.lotolab.contests.application.port.in;

import br.com.lotolab.contests.application.ContestPage;

public interface ListContestsUseCase {
    ContestPage list(int page, int size);
}
