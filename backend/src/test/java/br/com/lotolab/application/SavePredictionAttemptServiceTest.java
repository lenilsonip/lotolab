package br.com.lotolab.application;

import br.com.lotolab.application.port.PredictionAttemptRepository;
import br.com.lotolab.domain.PredictionAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavePredictionAttemptServiceTest {
    @Mock
    private PredictionAttemptRepository repository;
    @InjectMocks
    private SavePredictionAttemptService service;

    @Test
    void validatesAndDelegatesNewAttemptToRepository() {
        var numbers = IntStream.rangeClosed(1, 15).boxed().toList();
        var saved = new PredictionAttempt(42L, 3803, LocalDateTime.of(2026, 10, 31, 12, 0), numbers);
        when(repository.save(any(PredictionAttempt.class))).thenReturn(saved);

        assertThat(service.save(3803, numbers)).isEqualTo(saved);
        verify(repository).save(PredictionAttempt.newAttempt(3803, numbers));
    }

    @Test
    void rejectsNullContestNumberAndInvalidGame() {
        assertThatThrownBy(() -> service.save(null, numbers()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(3803, List.of(1, 2, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Integer> numbers() {
        return IntStream.rangeClosed(1, 15).boxed().toList();
    }
}
