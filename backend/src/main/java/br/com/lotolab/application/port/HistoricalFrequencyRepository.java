package br.com.lotolab.application.port;

import br.com.lotolab.domain.FrequencySnapshot;
import br.com.lotolab.domain.RecentFrequencySnapshot;

public interface HistoricalFrequencyRepository {

    FrequencySnapshot calculateDrawnNumberFrequencies();

    RecentFrequencySnapshot calculateRecentDrawnNumberFrequencies(int requestedRecentContests);
}
