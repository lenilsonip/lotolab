package br.com.lotolab.contests.application.port.in;

import br.com.lotolab.domain.Contest;

public record ContestRegistrationResult(Contest contest, boolean created, boolean researchTriggered) {
}
