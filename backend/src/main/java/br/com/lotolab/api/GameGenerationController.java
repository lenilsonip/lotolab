package br.com.lotolab.api;

import br.com.lotolab.application.GenerateLotofacilGamesService;
import br.com.lotolab.domain.GameGenerationCriteria;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jogos")
public class GameGenerationController {
    private final GenerateLotofacilGamesService service;

    public GameGenerationController(GenerateLotofacilGamesService service) {
        this.service = service;
    }

    @PostMapping("/geracao")
    public GameGenerationResponse generate(@Valid @RequestBody GameGenerationRequest request) {
        GameGenerationCriteria criteria = new GameGenerationCriteria(request.quantity(), request.evenNumbers(),
                request.oddNumbers(), request.seed());
        return GameGenerationResponse.from(criteria, service.generate(criteria));
    }
}
