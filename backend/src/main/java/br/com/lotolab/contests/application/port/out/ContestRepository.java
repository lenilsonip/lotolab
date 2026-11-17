package br.com.lotolab.contests.application.port.out;

import br.com.lotolab.domain.Contest;
import java.util.List;
import java.util.Optional;

/** Persistence boundary required by the contests use cases. */
public interface ContestRepository {
    boolean saveIfAbsent(Contest contest);
    Optional<Contest> findByNumber(int number);
    Optional<Contest> findLatest();
    List<Contest> findAll();
    long count();
    List<Contest> findPage(long offset, int limit);
}
