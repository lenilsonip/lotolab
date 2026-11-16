package br.com.lotolab.api;

import br.com.lotolab.application.CalculateDrawnNumberFrequencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analises")
public class DrawnNumberFrequencyController {

    private final CalculateDrawnNumberFrequencyService frequencyService;

    public DrawnNumberFrequencyController(CalculateDrawnNumberFrequencyService frequencyService) {
        this.frequencyService = frequencyService;
    }

    @GetMapping("/frequencia-dezenas")
    public DrawnNumberFrequencyResponse frequencies() {
        return DrawnNumberFrequencyResponse.from(frequencyService.calculate());
    }
}
