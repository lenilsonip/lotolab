# ADR-0005 — Unidade de trabalho e evento de resultado

- **Status:** Implementada.
- **Contexto:** registrar um resultado precisa persistir concurso e dezenas, avaliar tentativas futuras vencidas e atualizar a política prospectiva. A operação não pode deixar esses dados parcialmente confirmados.
- **Decisão:** `PersistContestService.persist` chama `ContestUnitOfWork.execute`; dentro da unidade de trabalho executa, nesta ordem, `ContestRepository.saveIfAbsent`, `PendingPredictionEvaluator.evaluateForContest` e `ContestResultEventPublisher.publish`. Evaluator e publisher só são chamados quando o concurso é novo.
- **Adapters:** `SpringContestUnitOfWork` delega a transação ao Spring; `JdbcPendingPredictionEvaluator` executa a avaliação em SQL; `SpringContestResultEventPublisher` traduz a porta ao `ApplicationEventPublisher`. `LearningPolicyService.onRealContestPersisted` consome o evento em `TransactionPhase.BEFORE_COMMIT` e grava a aprendizagem usando a transação atual.
- **Duplicidade e rollback:** concurso idêntico não avalia nem publica evento; conflito lança erro; rollback depois da publicação reverte concurso, dezenas, avaliações e learning event. Testes Testcontainers cobrem rollback, duplicidade e concorrência.
- **Por que não após commit:** o listener grava estado no mesmo banco e a regra exige atomicidade com o resultado. `AFTER_COMMIT` permitiria sucesso do concurso sem persistência de aprendizagem, exigindo retry/outbox para recuperar.
- **Por que não outbox agora:** o consumidor é interno, síncrono e transacional; não existe requisito atual de entrega a broker/serviço externo. Se consumo passar a assíncrono ou fora do banco, adotar outbox com chave idempotente e política de retry em uma nova decisão.
- **Risco:** uma falha inesperada do listener antes do commit aborta toda a gravação; isso é deliberado para consistência e deve ser monitorado.
