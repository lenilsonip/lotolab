package br.com.lotolab.api;

import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.LotofacilGame;

import java.util.List;

public record GameGenerationResponse(List<List<Integer>> games, Criteria criteria,
                                    String probabilityNotice) {
    public GameGenerationResponse {
        games = games.stream().map(game -> List.copyOf(game)).toList();
    }

    static GameGenerationResponse from(GameGenerationCriteria criteria, List<LotofacilGame> games) {
        return new GameGenerationResponse(games.stream().map(LotofacilGame::numbers).toList(),
                new Criteria(criteria.quantity(), criteria.evenNumbers(), criteria.oddNumbers(), criteria.seed()),
                "A geração não indica maior probabilidade real de ganhar; todas as combinações válidas são equiprováveis.");
    }

    public record Criteria(int quantity, Integer evenNumbers, Integer oddNumbers, Long seed) {
    }

}
