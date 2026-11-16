# 06 — Guia de estudo e navegação no LotoLab

## Como seguir uma funcionalidade no código atual

1. Comece pela rota em `api/*Controller` e anote DTO, validação e status HTTP.
2. Siga o serviço em `application/` até a regra e identificadores de transação.
3. Abra o tipo `domain/` usado pelo fluxo e leia seu construtor/invariantes.
4. Localize a porta em `application/port/` e o adaptador `infrastructure/persistence/`.
5. Leia migrations V1–V9 e testes `src/test/java` que exercitam o fluxo; para persistência prefira os testes Testcontainers.
6. No Angular, siga `app.routes.ts` → componente → `LotolabApiService` → environment/proxy.

## Exemplo: registrar concurso

`ContestController.register` valida `ContestRequest`, cria `Contest` e chama `RegisterContestService`; esse serviço delega para `ContestRepository`, implementado por `JdbcContestRepository`. O repositório persiste concurso e dezenas, avalia previsões pendentes na transação e publica `ContestResultPersistedEvent`. Depois o serviço atual chama mineração de padrões sincronamente. Esta última aresta é um ponto de melhoria arquitetural documentado, não um defeito de domínio.

## Exemplo: adicionar uma consulta estatística

Para a estrutura atual, identificar contrato desejado, criar/ajustar DTO/controller, caso de uso, porta de leitura, consulta JDBC e testes unitários + Testcontainers. Em arquitetura alvo, controller chamaria input port e deixaria construção/mapa de domínio no application adapter, mantendo SQL no adaptador PostgreSQL.

## Vocabulário para entrevista/revisão

- **Porta de saída:** interface que application precisa para obter dado ou executar efeito.
- **Adaptador:** implementação concreta que fala HTTP, PostgreSQL, filesystem ou scheduler.
- **Idempotência:** repetir a operação não duplica efeito; cadastro usa número do concurso e compara conteúdo.
- **Walk-forward:** treinar/selecionar com passado e avaliar em concursos posteriores, sem usar o futuro na escolha.
- **Score retrospectivo:** medida experimental sobre dados históricos; não é probabilidade de prêmio.
- **Modelo ML:** regressão logística por dezena com features temporais; deve ser distinguido de insight descritivo e política de jogo.

## Sequência recomendada para aprender a arquitetura

1. `Contest` e `LotofacilGame` (invariantes puras).
2. `ContestRepository` e `JdbcContestRepository` (porta, SQL, transações).
3. `ContestController` / `RegisterContestService` (contrato e orquestração).
4. `HistoricalContestImportService` (parsing, preview, parcialidade e transação por linha).
5. `MineContestPatternsService` e `OptimizedGameGeneratorService` (evidência e avaliação temporal).
6. `MachineLearningService`, `NumberFeatureExtractor`, `LogisticRegressionTrainer`, `TemporalMlEvaluationService` e codec (treino/artefato/inferência).
7. Testes de integração em PostgreSQL e componentes Angular.
