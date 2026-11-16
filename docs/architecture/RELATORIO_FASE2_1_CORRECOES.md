# LotoLab — Relatório da Fase 2.1: correções arquiteturais de concursos

**Data:** 12/11/2026
**Backend:** `<caminho-local-redigido>
**Escopo:** módulo de concursos e importação histórica; nenhum avanço para a Fase 3.

## Resultado

A revisão dos fontes confirmou que `JdbcContestRepository.saveIfAbsent` fazia mais que persistir: gravava dezenas, avaliava previsões pendentes e publicava evento Spring. `ContestApplicationService` concentrava cadastro/consultas e expunha atalhos de registro sem pesquisa/mineração. A mineração era síncrona após o cadastro. A hipótese sobre construtor JDBC que injeta publisher no-op também foi confirmada.

Essas responsabilidades foram separadas sem trocar JDBC, alterar migrations, modificar contratos HTTP ou migrar módulos de agentes/ML. A gravação e efeitos de aprendizagem continuam atômicos. O teste de falha de mineração confirmou que a falha de pesquisa não desfaz o resultado; um POST idempotente seguinte pede retry. O importador agora não dispara pesquisa quando a persistência do lote foi abortada, mesmo que linhas anteriores já tenham sido commitadas individualmente.

## Arquitetura e fluxo resultantes

```text
contests.adapter.in.web
  ContestController / ContestImportController + DTOs
       ↓ portas de entrada
contests.application.port.in
  RegisterContestUseCase, PersistContestUseCase,
  GetContestUseCase, GetLatestContestUseCase, ListContestsUseCase,
  PreviewContestImportUseCase, ImportContestHistoryUseCase
       ↓ handlers/casos de uso
contests.application.service
  RegisterContestHandler, PersistContestService,
  GetContestHandler, GetLatestContestHandler, ListContestsHandler
       ↓ portas de saída
contests.application.port.out
  ContestRepository, ContestUnitOfWork, PendingPredictionEvaluator,
  ContestResultEventPublisher, ContestResearchTrigger
       ↓ adapters
infrastructure.persistence / infrastructure.agent
  JdbcContestRepository, JdbcPendingPredictionEvaluator,
  SpringContestResultEventPublisher, SpringContestUnitOfWork,
  ContestResearchTriggerAdapter
       ↓
PostgreSQL
```

### Cadastro

1. `ContestController` valida/mapeia JSON em `RegisterContestCommand` e chama `RegisterContestUseCase`.
2. `RegisterContestHandler` instancia `domain.Contest`, com invariantes do domínio, e chama `PersistContestUseCase`.
3. `PersistContestService` envolve `ContestRepository.saveIfAbsent` em `ContestUnitOfWork`.
4. Concurso novo: `JdbcContestRepository` insere concurso e dezenas; `JdbcPendingPredictionEvaluator` grava avaliações; `SpringContestResultEventPublisher` publica `ContestResultPersistedEvent` dentro da transação.
5. `LearningPolicyService.onRealContestPersisted` consome o evento em `BEFORE_COMMIT` e grava política/evento de aprendizagem na mesma transação. Falha faz rollback de concurso, dezenas, avaliação e aprendizagem.
6. Concurso idêntico: o repositório retorna `false`; não há avaliação nem evento. Mesmo número com conteúdo diferente mantém conflito. Concorrência idêntica continua produzindo uma única inserção.
7. Após a transação, `RegisterContestHandler` invoca `ContestResearchTrigger`; a pesquisa segue síncrona. A resposta e os status HTTP não foram alterados.

### Importação CSV

`HistoricalContestImportService` usa o parser existente e `PersistContestUseCase` para cada linha, com transação própria por concurso. Concursos existentes/idênticos são idempotentes, divergências permanecem conflitos e linhas inválidas/falhas são reportadas. O restante de lote é interrompido após falha de persistência. A pesquisa é solicitada uma única vez depois de um lote completo com novas inserções; lote abortado não dispara pesquisa. A política de transação não deixa uma transação gigante envolver o arquivo inteiro.

## Decisões sobre eventos e mineração

- **Evento de aprendizagem em `BEFORE_COMMIT`:** `LearningPolicyService` grava no mesmo PostgreSQL, e a regra existente exige consistência com o concurso. `AFTER_COMMIT` criaria a possibilidade de concurso confirmado sem aprendizagem durável. Não foi criada outbox porque não há consumidor externo/assíncrono atual; ADR-0005 registra quando reavaliar.
- **Mineração após commit, síncrona:** `ContestResearchTriggerAdapter` captura `RuntimeException`, loga a falha e retorna `false`; a gravação do concurso permanece confirmada e o contrato HTTP segue 201 para novo/200 idêntico. `RegisterContestHandler` pede pesquisa também em retry idempotente; `MineContestPatternsService` evita duplicar run concluído via chave idempotente de versão/faixa. Importação só solicita pesquisa uma vez ao concluir com inserções novas.
- **Risco residual:** não há timeout específico para a chamada síncrona de mineração; um processamento lento pode aumentar a latência da requisição. Um job/outbox durável alteraria o comportamento e fica para decisão futura.

## Classes e arquivos alterados

### Novos casos de uso/portas/adapters

- `src/main/java/br/com/lotolab/contests/application/port/in/PersistContestUseCase.java`.
- `src/main/java/br/com/lotolab/contests/application/port/out/PendingPredictionEvaluator.java` e `ContestResultEventPublisher.java`.
- `src/main/java/br/com/lotolab/contests/application/service/PersistContestService.java`, `RegisterContestHandler.java`, `GetContestHandler.java`, `GetLatestContestHandler.java` e `ListContestsHandler.java`.
- `src/main/java/br/com/lotolab/infrastructure/persistence/JdbcPendingPredictionEvaluator.java` e `SpringContestResultEventPublisher.java`.
- `ContestResultPersistedEvent` foi transferido para `br.com.lotolab.contests.application`, junto com tipos da aplicação de concursos/importação.

### Modificados/removidos

- `ContestApplicationConfiguration` faz composição das novas portas e handlers.
- `JdbcContestRepository` agora só persiste/reconstrói o concurso; foram removidos do adapter a avaliação, publicação do evento, dependência de `ApplicationEventPublisher` e construtor no-op. O caminho de produção chama o use case dentro da unidade de trabalho.
- `HistoricalContestImportService` grava pelo use case de persistência e só dispara pesquisa após lote não abortado com novos concursos.
- `RegisterContestService`, `GetContestService`, `GetLatestContestService` e `ListContestsService` continuam como fachadas adaptadoras temporárias; remover conforme critérios do ADR-0006.
- Removidos `ContestApplicationService` e seu teste específico; as responsabilidades agora têm handlers próprios.
- Tipos de importação `ContestImportIssue`, `ContestImportReport` e `ContestImportPreview` foram movidos para `br.com.lotolab.contests.application`, removendo dependência da porta de entrada em pacote legado.
- `LearningPolicyService` usa o evento no novo pacote.
- `ArchitectureRulesTest` ampliado a oito regras; `ContestUseCasesTest` cobre persistência, duplicidade, retry da pesquisa, consultas/paginação e comportamento da pesquisa no importador.
- `LotoLabApplicationTests` adicionou integração Testcontainers para rollback, transação do evento, idempotência e concorrência. Testes de API e integração existentes foram adaptados para a porta de persistência; `ContestApiContractTest` verifica contrato de cadastro quando a pesquisa falha.

O estado inicial do Git já tinha mudanças staged, unstaged e arquivos untracked, incluindo migrations/dados/documentação de entregas anteriores. O índice não foi alterado. Nenhuma migration foi editada nesta tarefa.

## Regras de arquitetura

`ArchitectureRulesTest` contém oito regras ArchUnit. As regras verificam domínio sem Spring/JDBC/HTTP/application/infra; aplicação de concursos sem Spring/API/infra/adapter/bootstrap/pacote legado; importação sem dependências de Spring/API/infra; portas sem framework/adapters; web sem infraestrutura/portas de saída; direção entre adapters; e ausência de ciclos nas fatias de concursos. O limite foi deliberado: proteção não declara os módulos legados de agentes, ML, jogos ou Gemini como hexagonais.

## Validação executada

| Comando/ambiente | Resultado |
|---|---|
| Baseline anterior às mudanças: `mvnw.cmd clean verify` | **BUILD SUCCESS**, 141 testes. |
| Primeiro build após refatoração | 146 testes, com uma falha no novo teste de integração: fixture criado como previsão manual, que corretamente não é elegível para aprendizagem. |
| Teste corrigido `-Dtest=LotoLabApplicationTests#contestPersistenceEvaluationAndLearningEventShareCommitAndDuplicateEmitsNothing` | **BUILD SUCCESS**, 1 teste. Fixture passou a criar tentativa otimizada elegível; comprovou rollback e evento no commit. |
| `-Dtest=ContestUseCasesTest test` | **BUILD SUCCESS**, 6 testes, incluindo importação interrompida sem trigger e lote completo com trigger único. |
| `mvnw.cmd clean verify` final | **BUILD SUCCESS**, 148 testes, 0 falhas, 0 erros, 0 ignorados. Inclui contrato HTTP, ArchUnit, integração Testcontainers e empacotamento Spring Boot. |
| PostgreSQL/Testcontainers | Docker disponível; testes executados em PostgreSQL 17 temporário. V1–V9 validadas/aplicadas somente nos bancos descartáveis dos testes. |
| Banco local/volumes/migrations locais | Sem acesso de escrita; nenhuma migration local foi executada e nenhum volume/dado local foi alterado. |
| `git diff --check` e `git diff --cached --check` | Exit 0; somente avisos de normalização LF/CRLF em arquivos que já estavam modificados. O índice não foi alterado. |

O log de testes inclui warnings de Mockito/ByteBuddy por carregamento dinâmico de agente, avisos de API deprecated em testes anteriores e avisos do Hikari ao encerrar pools ligados a containers já fechados; não são falhas do build.

## Pendências e riscos

1. Mineração segue síncrona e sem timeout dedicado; monitorar duração e decidir se vira job/outbox numa fase autorizada.
2. Fachadas antigas e `application.port.ContestRepository` ainda existem. Critério de remoção: nenhuma referência de produção/teste fora da compatibilidade; consumidores migrados para as portas de entrada; integração verificada. Ver ADR-0006.
3. `Contest` permanece no domínio compartilhado porque agentes/ML ainda o consomem.
4. ArchUnit ainda não governa globalmente agentes, ML, Gemini e outros controllers.
5. Permanecem warnings não bloqueantes de unchecked cast no teste de use cases e setup dinâmico de Mockito/ByteBuddy; revisar em etapa própria, sem misturar com mudança funcional.

**Segurança e Git:** não houve `git add`, commit, reset, clean ou descarte de trabalho. Nenhum banco/volume persistente foi alterado. Não foram feitas chamadas externas pagas.
