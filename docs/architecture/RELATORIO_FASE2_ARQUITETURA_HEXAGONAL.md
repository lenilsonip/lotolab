# LotoLab — Relatório da Fase 2: Arquitetura Hexagonal

**Data:** 12/11/2026
**Escopo:** refatoração incremental do módulo de concursos e importação histórica.
**Backend:** `<caminho-local-redigido>
**Frontend:** `<caminho-local-redigido>

## Resultado

A Fase 2 foi implementada no módulo de concursos. O domínio compartilhado passou a ter regras ArchUnit; cadastro, consulta e importação usam portas de entrada e saída; controllers de concursos não acessam diretamente repositórios.

Os demais módulos — agentes, ML, frequência, jogos e Gemini — permaneceram fora da refatoração, conforme solicitado. A aplicação inteira ainda não é hexagonal: a proteção automatizada cobre o domínio compartilhado e as fronteiras de concursos/importação, mas não os módulos legados de agentes, ML e outras APIs.

## Estrutura nova do módulo

```text
br.com.lotolab
├── contests
│   ├── application
│   │   ├── ContestApplicationService
│   │   ├── ContestNotFoundException
│   │   ├── ContestPage
│   │   └── port
│   │       ├── in
│   │       │   ├── RegisterContestUseCase / RegisterContestCommand
│   │       │   ├── GetContestUseCase
│   │       │   ├── GetLatestContestUseCase
│   │       │   ├── ListContestsUseCase
│   │       │   ├── PreviewContestImportUseCase
│   │       │   └── ImportContestHistoryUseCase
│   │       └── out
│   │           ├── ContestRepository
│   │           ├── ContestResearchTrigger
│   │           └── ContestUnitOfWork
│   ├── adapter/in/web
│   │   ├── ContestController
│   │   ├── ContestImportController
│   │   └── DTOs de entrada e saída
│   └── bootstrap
│       └── ContestApplicationConfiguration
├── domain
│   └── Contest e outros tipos compartilhados
└── infrastructure
    ├── agent/ContestResearchTriggerAdapter
    └── persistence/SpringContestUnitOfWork e adaptador JDBC existente
```

`Contest` permaneceu em `br.com.lotolab.domain` para não exigir alterações nos módulos protegidos. A implementação JDBC também continua única. A interface legada `application.port.ContestRepository` estende a nova porta de saída, preservando seus consumidores existentes. Fachadas antigas de cadastro e consulta foram mantidas temporariamente por compatibilidade.

## Fluxo do cadastro até o PostgreSQL

1. `ContestController` valida o DTO e converte a entrada em `RegisterContestCommand`.
2. O controller chama `RegisterContestUseCase`, implementado por `ContestApplicationService`.
3. O serviço cria/valida `Contest` e chama a porta de saída `contests.application.port.out.ContestRepository`.
4. O adaptador `JdbcContestRepository` executa a gravação idempotente, persiste as dezenas e mantém as operações relacionadas à avaliação de previsões na transação já existente.
5. O resultado retorna ao controller, que preserva os contratos HTTP. Um cadastro novo retorna `201`; repetição idêntica mantém o comportamento idempotente; conflitos, entrada inválida e recurso inexistente mantêm seus códigos HTTP existentes.
6. Para concurso novo, `ContestResearchTrigger` aciona o adaptador de mineração. O acionamento continua síncrono, como antes.

Na importação, `HistoricalContestImportService` consome as portas de leitura de arquivo, repositório e unidade de trabalho. `SpringContestUnitOfWork` mantém a transação por concurso, e a mineração é acionada uma vez após uma importação bem-sucedida com novos registros.

## Arquivos e responsabilidades

Foram adicionados o serviço de aplicação, as portas de entrada e saída, DTOs e controllers no novo pacote, configuração de composição Spring, adaptador de trigger e unidade transacional. `HistoricalContestImportService` foi ajustado para não depender diretamente de `TransactionTemplate`, `PlatformTransactionManager` ou `LogContext`. `RegisterContestService` e alguns serviços de consulta ficaram como fachadas de compatibilidade. `ApiExceptionHandler` também reconhece a exceção usada pelo novo módulo.

O `pom.xml` recebeu ArchUnit. [ArchitectureRulesTest.java](C:/Users/lenil/Documents/Projetos/java/src/test/java/br/com/lotolab/ArchitectureRulesTest.java) verifica cinco regras:

- O domínio não depende de Spring, JDBC, HTTP ou infraestrutura.
- A aplicação de concursos não depende de Spring, API, infraestrutura, adapters ou bootstrap.
- A aplicação de importação não depende de Spring, API ou infraestrutura.
- O adapter web não depende diretamente de infraestrutura nem de portas de saída.
- Os slices de `contests` não têm dependências circulares.

Os contratos REST foram registrados em testes antes da troca do controller e exercitados novamente após a mudança. Os testes cobrem cadastro, idempotência, validação, conflito, consulta, paginação, último concurso e prévia/confirmação da importação.

## Decisões arquiteturais

- O código foi migrado incrementalmente, sem reescrever a aplicação.
- O módulo de concursos possui portas explícitas de entrada e saída.
- O domínio compartilhado foi mantido onde está para preservar os consumidores de agentes e ML.
- O adapter JDBC continua único; a interface antiga de repositório é uma extensão de compatibilidade.
- Transações são delimitadas por concurso durante a importação.
- O acionamento síncrono da mineração foi preservado para não alterar o comportamento existente.
- Os demais módulos não foram reorganizados nesta etapa.

As decisões e os fluxos foram documentados em [ADR-0003](C:/Users/lenil/Documents/Projetos/lotolab/docs/architecture/05-decisoes/ADR-0003-portas-por-caso-de-uso.md), [ADR-0004](C:/Users/lenil/Documents/Projetos/lotolab/docs/architecture/05-decisoes/ADR-0004-modulo-concursos-fase2.md), [plano de refatoração](C:/Users/lenil/Documents/Projetos/lotolab/docs/architecture/03-plano-refatoracao.md) e [fluxos](C:/Users/lenil/Documents/Projetos/lotolab/docs/architecture/04-fluxos.md).

## Validação executada

| Comando | Resultado |
|---|---|
| `\.mvnw.cmd clean verify` | **BUILD SUCCESS** — 141 testes, 0 falhas, 0 erros e 0 ignorados. Incluiu Testcontainers e validação das migrations em PostgreSQL temporário. |
| `npm test -- --watch=false` | **Sucesso** — 34 testes em 8 arquivos. |
| `npm run build` | **Sucesso** — build de produção Angular concluído. |
| `git diff --check` | **Exit 0** — apenas avisos de conversão LF/CRLF. |

Não foi executado Flyway contra o PostgreSQL local, nem foram alterados dados históricos ou volumes Docker. Não houve chamadas reais ao Gemini.

## Estado do Git

Não foram executados `git add`, commit, reset, clean ou descarte de alterações. O repositório já continha mudanças staged, unstaged e untracked; o novo controller e DTOs aparecem como arquivos novos no pacote `contests`, enquanto versões antigas aparecem com estado de remoção no working tree. Há também migrations staged ou untracked no estado observado; elas não foram editadas nesta tarefa.

## Riscos e pendências

- A proteção ArchUnit ainda não cobre os módulos legados de agentes, ML, frequência, jogos e Gemini.
- `Contest` segue no domínio compartilhado até que os consumidores protegidos possam ser migrados em outra etapa.
- A interface antiga `application.port.ContestRepository` e fachadas antigas são compatibilidade transitória.
- O trigger de mineração continua síncrono; alterar isso para job/outbox fica para uma fase futura, pois mudaria o comportamento atual.
- Não houve alteração ou aplicação de migrations nesta etapa.

## Próxima etapa

Migrar outra capacidade por vez somente após autorização, começando por definir seu contrato, fixar o comportamento com testes e criar regras arquiteturais específicas. A próxima fase pode tratar agentes/ML, sem assumir que a conclusão do módulo de concursos converteu automaticamente o restante da aplicação.
