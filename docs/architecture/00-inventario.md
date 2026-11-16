# 00 — Inventário técnico do LotoLab

**Escopo e fotografia:** 12/11/2026, após a Fase 2 do módulo de concursos. O código backend vive em `<caminho-local-redigido>, o frontend em `<caminho-local-redigido>, e estes documentos em `<caminho-local-redigido> A Fase 2 alterou o código do módulo de concursos e seus testes; não alterou banco local, migrations ou histórico.

## Tecnologias observadas

- Backend: Java 21, Spring Boot 4.1.1, Maven Wrapper, Spring MVC, Bean Validation, Spring JDBC/JdbcTemplate e PostgreSQL.
- Migrações: Flyway V1–V9. Não há JPA/Hibernate na estratégia de persistência.
- Testes: JUnit 5, Spring Test e Testcontainers PostgreSQL, além de testes de unidade.
- Frontend: Angular standalone com TypeScript, Angular Router, HttpClient e RxJS; o serviço central usa `environment.apiBaseUrl`. Testes executam pelo Angular CLI/Vitest.
- Integração textual opcional: Gemini via adaptador HTTP `RestClient`; estatísticas e decisões são calculadas no backend.
- IDE documentada: Eclipse.

## Estrutura de código

### Backend

Antes desta fase havia 129 fontes Java de produção e 31 testes. Após a refatoração de concursos, há estes grupos no pacote `br.com.lotolab`:

```text
br.com.lotolab
├── LotoLabApplication
├── contests/                    # cadastro, consulta e importação refatorados
│   ├── application/             # serviços de aplicação e portas in/out
│   ├── adapter/in/web/          # controllers REST, DTOs e mapeadores
│   └── bootstrap/               # wiring Spring explícito
├── api/                         # APIs das outras capacidades e error handler
├── application/                 # casos de uso de agentes/ML e fachadas compatíveis
│   ├── importing/               # tipos CSV e serviço de importação
│   └── port/                    # portas compartilhadas com outros módulos
├── domain/                      # domínio compartilhado, invariantes independentes
└── infrastructure/
    ├── agent/                   # workers e adapter de trigger de pesquisa
    ├── gemini/                  # integração externa
    ├── importing/               # parser CSV e runner
    ├── logging/                 # MDC/correlação/log contextual
    └── persistence/             # adaptadores JDBC
```

O domínio `br.com.lotolab.domain` continua compartilhado pelos agentes e pelo ML. A porta nova de repositório de concursos fica em `contests.application.port.out`; `application.port.ContestRepository` estende essa porta para manter os consumidores atuais no mesmo adapter JDBC.

Os nomes de classes centrais incluem `Contest`, `LotofacilGame`, `ContestApplicationService`, `RegisterContestService` (fachada de compatibilidade), `HistoricalContestImportService`, `JdbcContestRepository`, `MineContestPatternsService`, `OptimizedGameGeneratorService`, `GameBacktestingService`, `LearningPolicyService`, `AgentInterpretationService` e `MachineLearningService`.

### Frontend

O frontend tem rotas standalone em `src/app/app.routes.ts`: Home, `/cadastro`, `/importacao`, `/agentes` e `/ml`. Os componentes principais são `HomeComponent`, `RegisterContestComponent`, `ContestImportComponent`, `AgentDashboardComponent` e `MlLabComponent`; `ExperimentAnalysisComponent` fornece o fluxo manual de comparação/feedback. `LotolabApiService` concentra contratos e chamadas HTTP.

## Persistência identificada

Migrações encontradas: `V1__create_migration_probe.sql`, `V2__create_lotofacil_contest.sql`, `V3__create_prediction_attempt.sql`, `V4__create_pattern_insights.sql`, `V5__audit_cooperative_agents.sql`, `V6__persist_agent_interpretations.sql`, `V7__allow_disabled_agent_interpretation.sql`, `V8__durable_agent_jobs_and_learning.sql` e `V9__add_experimental_logistic_ml.sql`.

Conjuntos de tabelas por finalidade: probe temporário; concursos e dezenas oficiais; previsões e dezenas previstas; insights/versionamento de execução/folds/evidências; interpretações e jobs dos agentes; políticas/versionamento/eventos de aprendizagem; treinamento, artefatos, avaliações e previsões experimentais de ML. A V1 permanece histórica e não foi editada.

`JdbcContestRepository` usa `JdbcTemplate`, transação, escrita idempotente por número, lote de dezenas e reconstrução da entidade a partir de JOINs. O domínio `Contest` impõe 15 dezenas únicas de 1 a 25, ordenadas e imutáveis. O schema restringe número/data, intervalo e unicidade da dezena por concurso, mas não expressa sozinho a cardinalidade agregada exatamente 15.

## Superfície HTTP encontrada

| Método | Rota | Função |
|---|---|---|
| GET/POST | `/api/concursos` | Lista paginada / cadastra concurso |
| GET | `/api/concursos/{number}`, `/api/concursos/ultimo` | Consulta concurso |
| POST | `/api/admin/importacao-concursos/preview`, `/api/admin/importacao-concursos` | Prévia e importação CSV |
| GET | `/api/analises/frequencia-dezenas`, `/api/analises/frequencia-recente` | Frequências |
| POST | `/api/jogos/geracao` | Geração uniforme com critérios |
| POST | `/api/experimentos/analise` | Comparação e feedback Gemini opcional |
| POST | `/api/experimentos/gerar-otimizado`, `/api/experimentos/gerar-politica` | Avaliação e geração experimental |
| POST/GET | `/api/previsoes` | Registra/lista previsões |
| POST/GET | `/api/agentes/pesquisador/minerar`, `/insights`, `/interpretacao` | Pesquisa e evidências |
| GET | `/api/agentes/jobs/{id}`, `/latest` | Acompanha trabalho de agente |
| GET | `/api/agentes/aprendizagem` | Estado de política e feedback |
| POST/GET | `/api/ml/treinamentos`, `/treinamentos/{id}`, `/modelos`, `/modelos/{id}`, `/previsoes-experimentais` | ML experimental |

## Git e validação de referência

O backend estava deliberadamente sujo antes desta auditoria: há mudanças staged e unstaged, novos arquivos, documentos e dados CSV/XLSX não rastreados. `git diff --check` não apontou erro de whitespace; mostrou apenas avisos de conversão LF/CRLF. Nenhuma operação Git de escrita foi feita.

Resultados completos da validação desta fase estão em `03-plano-refatoracao.md`. Builds/testes não aplicam migrações ao PostgreSQL local; os testes de integração usam PostgreSQL Testcontainers.
