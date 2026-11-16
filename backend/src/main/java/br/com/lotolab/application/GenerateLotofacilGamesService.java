package br.com.lotolab.application;

import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.LotofacilGame;
import br.com.lotolab.domain.UniformLotofacilGameGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GenerateLotofacilGamesService {
    private final UniformLotofacilGameGenerator generator = new UniformLotofacilGameGenerator();

    public List<LotofacilGame> generate(GameGenerationCriteria criteria) {
        return generator.generate(criteria);
    }
}
