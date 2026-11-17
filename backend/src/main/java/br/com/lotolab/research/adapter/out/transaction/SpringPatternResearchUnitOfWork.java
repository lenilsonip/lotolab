package br.com.lotolab.research.adapter.out.transaction;

import br.com.lotolab.research.application.port.out.PatternResearchUnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

@Component
public final class SpringPatternResearchUnitOfWork implements PatternResearchUnitOfWork {
    private final TransactionTemplate transaction;
    public SpringPatternResearchUnitOfWork(PlatformTransactionManager manager) {
        this.transaction = new TransactionTemplate(Objects.requireNonNull(manager));
    }
    @Override public <T> T execute(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }
}
