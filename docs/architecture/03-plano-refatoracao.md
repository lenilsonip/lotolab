# 03 — Plano incremental de refatoração e validação

## Estado das fases

As Fases 0/1 (inventário e arquitetura), 2 (fronteiras hexagonais iniciais), 2.1 (correções do módulo de concursos) e 3 (mineração de padrões) estão concluídas dentro dos limites descritos nos relatórios. A aplicação inteira não foi convertida: avaliação de jogos, persistência de previsões, aprendizado, ML e Gemini permanecem fora desta refatoração.

### Fase 2.1 — Concursos e importação

1. Contratos HTTP de concursos/importação preservados e exercitados por testes de contrato.
2. `JdbcContestRepository` limitado a persistir/reconstruir o concurso e suas dezenas. `JdbcPendingPredictionEvaluator` e `SpringContestResultEventPublisher` são adaptadores distintos.
3. `PersistContestService` orquestra a porta `PersistContestUseCase` dentro de `ContestUnitOfWork`; transação envolve concurso, dezenas, avaliações e o listener de aprendizagem `BEFORE_COMMIT`.
4. Handlers distintos implementam registrar, obter por número, último e listagem. Importação usa `PersistContestUseCase` para cada linha e não chama métodos auxiliares do serviço de cadastro.
5. Trigger de pesquisa continua síncrono. Cadastro manual repetido solicita nova tentativa de pesquisa; a idempotência versionada/faixa do miner evita duplicar execução completa. Importação dispara uma vez após lote bem-sucedido com concurso novo.
6. Fachadas legadas são mantidas por compatibilidade e têm critérios de remoção registrados em ADR-0006.
7. ArchUnit protege as fronteiras efetivamente migradas; as regras não afirmam cobertura de todos os módulos.

### Fase 3 — Mineração de padrões

1. A capacidade `research` agora separa cálculo puro, casos de uso/portas, adapters REST e scheduler, adapters JDBC/transação/evento e bootstrap.
2. A versão estatística `walk-forward-2`, as métricas, o baseline aleatório, as janelas, a semente determinística, a chave de idempotência, as rotas e o JSON existentes foram preservados.
3. A mineração é uma unidade de trabalho transacional. A conclusão publica o evento Spring consumido em `BEFORE_COMMIT`; a interpretação/job persistidos participam da transação e falhas revertem o run e seus insights.
4. As oito regras ArchUnit da fase 2.1 foram mantidas e seis regras passaram a proteger domínio/aplicação/adapters/ciclos de `research`.
5. Testcontainers comprovou a rota manual e consultas, reexecução idempotente, concorrência, rollback e escala de 3.802 concursos sintéticos. O relatório registra tempos e resultados; nenhuma migration/dado local foi tocado.
6. A interpretação Gemini segue desativada nos testes e não foi chamada. Scores e evidências permanecem retrospectivos/experimentais, não probabilidades futuras.

Consulte [Relatório da Fase 3](RELATORIO_FASE3_MINERACAO.md) e [ADR-0007](05-decisoes/ADR-0007-mineracao-hexagonal.md).

## Próximas fases propostas

### Fase 4 — Avaliação, tentativas, política, jobs, Gemini e ML

Isolar polling/lease SQL e orquestração de jobs, gateway Gemini, treinamento/checksum/armazenamento/inferência ML. Manter testes com mocks e Testcontainers. Não promover o modelo experimental à política principal sem avaliação temporal suficiente.

### Fase 5 — Angular e encerramento

Organizar rotas e serviços por feature somente quando reduzir dependências, preservando contratos. Revalidar testes/build do Angular e Maven. Revisar Git sem staging/commit automático.

## Gates de cada fase

- Registrar estado Git sem descartar mudanças.
- Fixar comportamento com contratos/testes antes de trocar adapters.
- Testar migrations e efeitos de persistência somente em PostgreSQL descartável; não migrar o banco local automaticamente.
- Executar testes existentes e novos, `git diff --check` e documentação de resultados reais.
- Preservar dados, volumes, migrations aplicadas e staging.

## Validação

Os relatórios por fase são históricos e não devem ser tratados como estado atual. Consulte [Relatório da Fase 2.1](RELATORIO_FASE2_1_CORRECOES.md) e [Relatório da Fase 3](RELATORIO_FASE3_MINERACAO.md) para escopo e resultados reais. A validação Angular da Fase 3 foi somente regressiva; nenhuma migration foi alterada/aplicada no PostgreSQL local.

## Limite arquitetural

A organização hexagonal e as regras automatizadas cobrem `contests`, importação e mineração `research`, não a aplicação completa. `application.port.ContestRepository` e as fachadas de cadastro/consulta continuam compatibilidade transitória; remover após não haver consumidor de produção/teste além do adapter de compatibilidade e depois de migrar os clientes às portas de entrada. `PatternInsightRepository` e o evento `PatternMiningCompletedEvent` também permanecem interfaces/tipos compartilhados para compatibilidade com o avaliador e o serviço de interpretação; a persistência destes outros módulos não foi refatorada.
