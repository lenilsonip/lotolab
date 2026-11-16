package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.contests.application.port.out.ContestUnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringContestUnitOfWork implements ContestUnitOfWork {
    private final TransactionTemplate transactionTemplate;

    public SpringContestUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> operation) {
        return transactionTemplate.execute(status -> operation.get());
    }
}
