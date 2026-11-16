package br.com.lotolab.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContestTest {

    private static final LocalDate DRAW_DATE = LocalDate.of(2026, 10, 24);

    @Test
    void createsContestWithSortedImmutableNumbers() {
        List<Integer> inputNumbers = new ArrayList<>(numbersFrom(1, 15).reversed());

        Contest contest = new Contest(1234, DRAW_DATE, inputNumbers);
        inputNumbers.clear();

        assertThat(contest.drawnNumbers()).containsExactlyElementsOf(numbersFrom(1, 15));
        assertThatThrownBy(() -> contest.drawnNumbers().add(16))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonPositiveContestNumber() {
        assertThatThrownBy(() -> new Contest(0, DRAW_DATE, numbersFrom(1, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingDate() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Contest(1234, null, numbersFrom(1, 15)));
    }

    @Test
    void rejectsWrongNumberOfDrawnNumbers() {
        assertThatThrownBy(() -> new Contest(1234, DRAW_DATE, numbersFrom(1, 14)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDrawnNumbersOutsideLotofacilRange() {
        List<Integer> includesZero = new ArrayList<>(numbersFrom(1, 15));
        includesZero.set(0, 0);
        List<Integer> includesTwentySix = new ArrayList<>(numbersFrom(1, 15));
        includesTwentySix.set(0, 26);

        assertThatThrownBy(() -> new Contest(1234, DRAW_DATE, includesZero))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contest(1234, DRAW_DATE, includesTwentySix))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRepeatedDrawnNumber() {
        List<Integer> repeated = new ArrayList<>(numbersFrom(1, 15));
        repeated.set(14, 1);

        assertThatThrownBy(() -> new Contest(1234, DRAW_DATE, repeated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Integer> numbersFrom(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }
}
