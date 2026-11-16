package br.com.lotolab.application;

import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.LotofacilGame;
import br.com.lotolab.domain.PredictionComparison;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ComparePredictionService {

    private final ContestRepository contestRepository;

    public ComparePredictionService(ContestRepository contestRepository) {
        this.contestRepository = Objects.requireNonNull(contestRepository);
    }

    public PredictionComparison compare(List<Integer> predictedNumbers, Integer actualContestNumber) {
        if (actualContestNumber == null || actualContestNumber <= 0) {
            throw new IllegalArgumentException("O número do concurso deve ser positivo");
        }

        List<Integer> predicted = new LotofacilGame(predictedNumbers).numbers();
        Contest actualContest = contestRepository.findByNumber(actualContestNumber)
                .orElseThrow(() -> new ContestNotFoundException(actualContestNumber));
        List<Integer> actual = actualContest.drawnNumbers();

        Set<Integer> predictedSet = new HashSet<>(predicted);
        Set<Integer> actualSet = new HashSet<>(actual);

        List<Integer> matched = predicted.stream().filter(actualSet::contains).toList();
        List<Integer> missed = predicted.stream().filter(number -> !actualSet.contains(number)).toList();
        List<Integer> unexpected = actual.stream().filter(number -> !predictedSet.contains(number)).toList();

        return new PredictionComparison(predicted, actual, matched.size(), matched, missed, unexpected);
    }
}
