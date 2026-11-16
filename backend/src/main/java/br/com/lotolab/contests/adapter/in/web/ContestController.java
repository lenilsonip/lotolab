package br.com.lotolab.contests.adapter.in.web;

import br.com.lotolab.contests.application.port.in.GetContestUseCase;
import br.com.lotolab.contests.application.port.in.GetLatestContestUseCase;
import br.com.lotolab.contests.application.port.in.ListContestsUseCase;
import br.com.lotolab.contests.application.port.in.RegisterContestUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/concursos")
public class ContestController {
    private final RegisterContestUseCase registerContest;
    private final GetContestUseCase getContest;
    private final GetLatestContestUseCase getLatestContest;
    private final ListContestsUseCase listContests;

    public ContestController(RegisterContestUseCase registerContest, GetContestUseCase getContest,
                             GetLatestContestUseCase getLatestContest, ListContestsUseCase listContests) {
        this.registerContest = registerContest;
        this.getContest = getContest;
        this.getLatestContest = getLatestContest;
        this.listContests = listContests;
    }

    @PostMapping
    public ResponseEntity<ContestResponse> register(@Valid @RequestBody ContestRequest request) {
        var result = registerContest.register(request.toCommand());
        ContestResponse response = ContestResponse.from(result.contest());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/concursos/" + result.contest().number())).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{number}")
    public ContestResponse get(@PathVariable int number) {
        return ContestResponse.from(getContest.get(number));
    }

    @GetMapping("/ultimo")
    public ContestResponse latest() {
        return ContestResponse.from(getLatestContest.get());
    }

    @GetMapping
    public ContestPageResponse list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return ContestPageResponse.from(listContests.list(page, size));
    }
}
