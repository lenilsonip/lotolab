package br.com.lotolab.api;

import br.com.lotolab.application.CalculateRecentNumberFrequencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analises")
public class RecentFrequencyController {

    private final CalculateRecentNumberFrequencyService frequencyService;

    public RecentFrequencyController(CalculateRecentNumberFrequencyService frequencyService) {
        this.frequencyService = frequencyService;
    }

    @GetMapping("/frequencia-recente")
    public RecentFrequencyResponse frequencies(@RequestParam(defaultValue = "100") int concursos) {
        return RecentFrequencyResponse.from(frequencyService.calculate(concursos));
    }
}
