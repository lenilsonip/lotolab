package br.com.lotolab.application.importing;

import br.com.lotolab.domain.Contest;

public record ParsedContestRow(int lineNumber, Contest contest) {
}
