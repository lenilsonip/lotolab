# 02 — Arquitetura alvo proposta

## Princípios

Manter um **monólito modular** Java 21/Spring Boot, PostgreSQL e JDBC. Organizar por capacidades de negócio e impor direção de dependência. Não adotar JPA, microsserviços, framework de agentes ou novas bibliotecas sem requisito demonstrado. Preservar contratos externos enquanto cada capacidade é migrada.

```text
br.com.lotolab
├── contests/                   # estrutura aplicada nesta fase
│   ├── application/             # use cases e portas de entrada/saída
│   ├── adapter/in/web/          # controllers REST e DTOs
│   └── bootstrap/               # configuração Spring explícita
├── domain/                     # domínio compartilhado, livre de framework
├── infrastructure/persistence/ # adapter JDBC compartilhado
├── infrastructure/agent/       # adapter do trigger de mineração
├── bootstrap/                  # wiring transversal futuro
├── importing/                  # CSV, preview e import use cases
├── analytics/                  # frequência e métricas descritivas
├── research/                   # hipótese, mineração, folds e insights
├── gameevaluation/             # geração, baseline, backtesting
├── predictions/                # tentativas e resultado observado
├── learning/                   # política/versionamento/decisões
├── machinelearning/            # treino, artefato, inferência, avaliação
├── agentjobs/                  # fila/worker e coordenação assíncrona
└── adapters/
    ├── in/web/                 # controllers e DTOs
    ├── out/postgres/           # JDBC, mappers e transações
    ├── out/gemini/             # cliente externo e sanitização
    └── observability/          # MDC, correlation, logging
```

O pacote `domain` é compartilhado para preservar os consumidores de agentes e ML, e o adapter JDBC continua único. A interface antiga `application.port.ContestRepository` estende a nova porta de saída de concursos até que uma fase autorizada migre esses consumidores. Não foram criados diretórios vazios.

## Regras de dependência

```text
adapter de entrada → porta de entrada/application → domain
application → portas de saída (interfaces próprias)
adapter de saída → portas de saída + domain
bootstrap → configura todos os adaptadores
domain → Java standard library apenas
```

- Controllers traduzem HTTP → command/query, invocam caso de uso e traduzem resultado → DTO/status. Não montam entidades de domínio nem chamam repositório.
- Aplicação coordena casos de uso e regras de transação por fronteira explícita; não importa `infrastructure.*`, JDBC, HTTP externo ou MDC.
- Portas de saída representam necessidades reais (`ContestStore`, `Clock`, `JobQueue`, `InterpretationGateway`, `TransactionBoundary` se necessário), não interfaces para cada classe por formalidade.
- Adaptadores JDBC mantêm SQL, row mapping, `JdbcTemplate` e mapeamento de integridade. Adaptador Gemini mantém `RestClient`, timeout/status e serialização externa.
- Logging recebe contexto por um serviço/porta de observabilidade ou decorador de execução, sem fazer o caso de uso importar MDC.
- Erros de domínio/aplicação são traduzidos por adaptadores web; API não conhece exceções JDBC.

## Limites funcionais

- **Contest:** agregado/valor canônico e operações de cadastro/consulta; invariantes Java seguem como autoridade. JDBC mantém escrita atômica e detecção de corrupção.
- **Importing:** parser é adaptador CSV; validação comum e preview compartilham o mesmo caso de uso/normalizador. Cada concurso mantém unidade transacional e relatório parcial explícito.
- **Research:** evidências observacionais versionadas por algoritmo, dataset, janelas/folds e baseline.
- **Game evaluation:** candidatos são comparados em janelas temporais sem acesso a resultados futuros; orçamento e baseline equivalentes registrados.
- **Predictions/Learning:** a tentativa guarda o modelo/estratégia e cutoff no instante de geração; evento de resultado aplica-se uma vez, com trilha auditável. Não promover com amostra insuficiente ou métrica sem baseline apropriada.
- **ML:** treinamento, checksum, artefato e avaliação são separados da pesquisa de padrões; inferência experimental permanece claramente identificada. A integração no gerador de jogos só ocorre após avaliação prospectiva/controlada.
- **Agent jobs/AI:** trabalho externo é assíncrono e durável; Gemini interpreta estatísticas fornecidas. Não calcula resultado oficial, score nem chance de sorteio.

## Transações e eventos (estado atual e direção futura)

No módulo de concursos atual, `PersistContestService` usa `ContestUnitOfWork`; concurso + dezenas + avaliações + `learning_event` são atômicos. O publisher emite antes do commit e `LearningPolicyService` grava via listener `BEFORE_COMMIT`, conforme ADR-0005. A mineração segue síncrona após commit e é reprocessável por retry idempotente, conforme ADR-0006. Para uma futura mudança para execução realmente assíncrona ou integração externa, avaliar job/outbox persistido na mesma transação e worker idempotente. Não confundir esta direção futura com o comportamento atual.

Não introduzir uma abstração genérica de transação se a configuração Spring e um adaptador fino resolverem; a solução deve permitir que testes provem os limites efetivos.

## Contratos e composição

Manter rotas e JSON atuais enquanto refatoração interna ocorre. DTOs REST não são modelos de domínio. Configurações vêm de `bootstrap`; segredos nunca entram em DTO, logs, commit ou artefato. Angular mantém `LotolabApiService`/environment e pode evoluir para feature dirs sem exigir mudança simultânea no backend.
