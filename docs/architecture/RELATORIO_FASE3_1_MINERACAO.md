# LotoLab — Relatório de fechamento da Fase 3.1

**Data:** 12/11/2026
**Escopo:** correções e comprovações da mineração de padrões `research`; sem iniciar a Fase 4 e sem refatorar módulos vizinhos.

## Diagnóstico e decisões

| Apontamento | Resultado | Evidência |
|---|---|---|
| POST associava job global mais recente | Corrigido | `PatternResearchController.mine()` consulta o job pelo `runId` produzido; adapter JDBC filtra `agent_interpretation.agent_run_id`. |
| Resposta idempotente podia associar job antigo | Descartado como comportamento novo | `ALREADY_COMPLETED` continua com zero insights, ID nulo e `NOT_QUEUED`, preservando a semântica existente. |
| Alteração de histórico por cadastro/importação | Não suportada; conflitos são explícitos | Mesmo conteúdo é no-op. Número existente com data/dezenas diferentes gera `ContestConflictException`; teste confere que data e dezenas originais continuam no banco temporário. |
| Alteração manual direta no banco | Limite residual documentado | SQL privilegiado pode editar dados e escapar das garantias da aplicação; nenhum checksum foi introduzido porque correção não é um fluxo suportado nesta etapa. Ver ADR-0008. |
| Varreduras repetidas na mineração | Otimização segura aplicada | Coocorrências de pares/trincas são pré-contadas em uma passagem pelo conjunto de treino. Hash determinístico da lista completa de evidências permaneceu idêntico. |

## Mudanças

- `GetResearchJobForRunUseCase` e `GetResearchJobForRunService` expõem a consulta por run na camada de aplicação.
- `ResearchInterpretationQueries` e `ResearchInterpretationQueriesAdapter` fornecem a porta/adaptação para a consulta; `AgentInterpretationRepository` e `JdbcAgentInterpretationRepository` executam a busca SQL por run e tipo de job.
- `PatternResearchController` deixa de usar a consulta global `latestJob` para montar a resposta de mineração. O shape de `MiningRunResponse`, rota, status HTTP e mensagens permanecem iguais.
- `PatternMiningAlgorithm.addCoOccurrenceEvidence` conta pares e trincas a partir dos 15 números de cada concurso de treino e ordena por contagem decrescente e máscara crescente. A versão permanece `walk-forward-2` porque não mudou a semântica estatística.
- Testes ampliados em `PatternMiningPostgresIntegrationTests`, `PatternMiningAlgorithmTest` e `LotoLabApplicationTests` cobrem associação do job, concorrência HTTP, imutabilidade do histórico e equivalência numérica.
- Nenhuma migration, CSV, dado histórico, configuração do banco local ou frontend foi alterado.

## Fluxos e evidências Testcontainers

1. **Mineração HTTP:** `POST /api/agentes/pesquisador/minerar` em histórico sintético de 30 concursos criou um `agent_run` `COMPLETED`, persistiu 360 linhas em `pattern_insight`, criou uma interpretação `PENDING` e um job `PENDING`. O teste consulta as tabelas e confere a relação pelo `agent_run_id`.
2. **Job anterior de outro run:** o teste cria outro run/job com `created_at` no futuro, isto é, globalmente mais recente. O POST ainda retorna o ID do job ligado ao run recém-criado; consulta SQL confirma a associação e separação dos IDs.
3. **Concorrência:** duas chamadas HTTP simultâneas para a mesma faixa retornam uma execução concluída com job e uma resposta idempotente sem job. No banco temporário permanece exatamente um run, uma interpretação e um job.
4. **Idempotência:** repetir o POST mantém os mesmos 360 insights e uma única execução/job.
5. **Atomicidade:** uma constraint de teste rejeita a inserção do job; run, insights e interpretação são revertidos juntos. O teste remove apenas a constraint temporária ao terminar.
6. **Walk-forward:** consultas de asserção SQL encontram zero evidências com `training_last_contest >= validation_first_contest`, e conferem cinco folds no dataset menor e seis no dataset de 3.802 concursos.
7. **Equivalência:** SHA-256 do `toString()` ordenado de 72 `PatternInsight` de fixture determinística igual ao valor de referência obtido antes da otimização: `b71d4af2b3d3b65e5e1586f358235a879bf686437a5d6530e98ba28ba3c4c05d`.
8. **Cadastro/importação:** testes de integração existentes mantêm cadastro idempotente e gatilho de pesquisa; o teste de conflito de importação verifica que a data e as dezenas persistidas não foram sobrescritas.

## Desempenho

Ensaio usa PostgreSQL 17 Alpine em Testcontainers descartável, Docker Desktop 29.9.2, host Windows e Java 21.0.4. A escala é sintética; o tempo não é SLA nem mede qualidade preditiva.

| Execução | Concursos | Folds | Insights | Tempo medido da mineração |
|---|---:|---:|---:|---:|
| Antes da otimização, teste isolado | 3.802 | 6 | 882 | 14.703 ms |
| Depois, teste isolado | 3.802 | 6 | 882 | 9.446 ms |
| Depois, dentro de `clean verify` | 3.802 | 6 | 882 | 10.528 ms |

A comparação isolada mediu redução observada de aproximadamente 35,7%. O resultado integrado variou com a carga da suíte, por isso não se afirma ganho garantido em produção. Antes, as 2.600 combinações possíveis de pares/trincas varriam os tamanhos de treino dos seis folds, somando cerca de 44,48 milhões de verificações de concurso. A nova contagem faz 560 atualizações por concurso de treino (105 pares + 455 trincas), cerca de 9,58 milhões no mesmo tamanho de treino, sem alterar a ordenação dos 16 itens selecionados.

## Testes e comandos

- `.\mvnw.cmd '-Dtest=PatternMiningPostgresIntegrationTests#fullScaleSyntheticHistoryRunsSixTemporalFoldsAndPersistsExpectedEvidence' test` — passou; referência 14.703 ms.
- `.\mvnw.cmd '-Dtest=PatternMiningAlgorithmTest' test` — passou; teste do hash de equivalência.
- `.\mvnw.cmd '-Dtest=PatternMiningAlgorithmTest,PatternMiningPostgresIntegrationTests' test` — 7 testes passaram após a otimização; medição de escala 9.446 ms.
- `.\mvnw.cmd '-Dtest=LotoLabApplicationTests#rejectsReimportWithDifferentContestData' test` — passou após ajustar o teste ao tipo `SMALLINT[]` (`Short[]`) retornado pelo PostgreSQL.
- `.\mvnw.cmd clean verify` — **BUILD SUCCESS**, 162 testes, 0 falhas, 0 erros, incluindo Testcontainers e 14 regras ArchUnit.

O build registrou avisos existentes de API obsoleta/unsafe, attach dinâmico do agente Mockito e tarefas agendadas que tentaram usar conexões após containers de outros contextos de teste serem encerrados. Não houve falha de teste. Essa condição do harness não foi alterada nesta fase para evitar expandir o escopo de scheduler/worker.

## Preservação e riscos

- Testcontainers criou bancos PostgreSQL temporários e aplicou V1–V9 apenas nesses bancos. Não houve migração ou escrita no PostgreSQL local, alteração de volumes, arquivos históricos ou Gemini.
- Nenhuma migration foi alterada. O Git já tinha mudanças staged, unstaged e untracked antes da tarefa; elas foram preservadas, sem `git add`, commit, reset ou clean de Git.
- Risco residual: alterações administrativas diretas de histórico podem manter os mesmos limites e não são detectadas pela chave por faixa. Se isso virar uma capacidade suportada, é preciso decidir versionamento/invalidação e identidade canônica do dataset antes de alterar a chave.
- Mineração continua síncrona como antes; o job de interpretação mantém o evento `BEFORE_COMMIT` e a gravação atômica.

## Encerramento

A Fase 3.1 está fechada no escopo autorizado: associação run/job corrigida e coberta por concorrência, política de histórico formalizada para os fluxos suportados, equivalência estatística verificada, otimização medida e suite completa aprovada. Próximo passo: retomar uma prioridade funcional do LotoLab, sem iniciar refatorações preventivas adicionais.
