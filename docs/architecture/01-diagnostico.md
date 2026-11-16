# 01 — Diagnóstico arquitetural

## Síntese

O LotoLab permanece um **monólito Spring em transição de organização por camadas para módulos hexagonais**. A Fase 2 introduziu fronteiras explícitas e regras automatizadas no módulo de concursos. Os demais módulos mantêm as dependências legadas listadas abaixo; portanto a aplicação inteira ainda não constitui uma arquitetura hexagonal estrita nem Clean Architecture.

As práticas positivas são concretas: entidades/valores de domínio sem imports Spring/Jakarta/JPA; contratos REST próprios; validação de invariantes em `Contest`/`LotofacilGame`; `JdbcTemplate` sem ORM; migrations incrementais; Testcontainers; importação por concurso transacional; jobs persistidos com lease/retry; comparação temporal e baseline explicitada; Gemini opcional e fora dos cálculos.

## Achados priorizados

### P1 — Direção de dependências entre application, infrastructure e API (diagnóstico pré-Fase 2)

- `RegisterContestService`, `GameBacktestingService`, `MineContestPatternsService`, `OptimizedGameGeneratorService`, `HistoricalContestImportService` e `AgentInterpretationService` importam `infrastructure.logging.LogContext`. A regra de dependência é invertida: política de aplicação depende de mecanismo de logging.
- `HistoricalContestImportService` referencia `PlatformTransactionManager`/`TransactionTemplate`, fazendo detalhe Spring de transação parte do caso de uso.
- `AgentInterpretationService` contém `@Value`, `@Autowired`, `@Transactional`, `@TransactionalEventListener` e `Propagation` dentro de application. `@Service`/`@Component` também são comuns nesta camada. Isso não torna o código inválido, mas reduz isolamento e testes sem Spring.
- `ContestController` constrói `domain.Contest`; outros controllers acessam portas diretamente (`AgentJobController`, `PatternResearchController`, `PolicyGameController`) e alguns importam `infrastructure.logging.LogContext`. A API conhece detalhes internos além do contrato HTTP.
- DTOs de resposta frequentemente convertem domínio com método `from(domain)`. Isso é aceitável em projetos pequenos, mas cria acoplamento entre representação HTTP e modelo interno.

**Estado após a Fase 2.1:** `ContestController` e `ContestImportController` usam portas de entrada. `contests.application` depende das portas de saída, não de Spring/API/infra/bootstrap; a importação não depende de `PlatformTransactionManager`, `TransactionTemplate` ou `LogContext`. As fachadas antigas continuam como adaptadores de compatibilidade, com critério de remoção em ADR-0006. Os achados sobre agentes, ML e outros controllers continuam válidos e não foram refatorados.

### P1 — Orquestração de domínio e efeitos laterais acoplados (diagnóstico pré-Fase 2)

- `RegisterContestService.register()` escreve o concurso e depois chama concretamente `MineContestPatternsService.mine()` de forma síncrona. Uma inserção pode pagar o custo de mineração no caminho da requisição. A falha é capturada e registrada, o que protege o cadastro, mas mistura duas responsabilidades e produz comportamento de latência variável.
- `RegisterContestService` tem construtor alternativo em que `miningService` fica nulo e o caso de uso vira no-op para mineração; `OptimizedGameGeneratorService` também possui construtores de teste que deixam repositórios opcionais. São sinais de dependências opcionais escondidas e cenários de produção/teste diferentes.
- `JdbcContestRepository` publica `ContestResultPersistedEvent` por `ApplicationEventPublisher`, cujo tipo vive em application. `LearningPolicyService` e `AgentInterpretationService` escutam eventos transacionais na camada application. A fronteira está funcional, mas Spring events viraram mecanismo de integração e o adaptador de persistência tem responsabilidade de orquestração.

**Estado após a Fase 2.1:** `RegisterContestHandler` usa `ContestResearchTrigger`; o trigger continua síncrono. `PersistContestService` orquestra a gravação via `ContestUnitOfWork`, avaliação via `PendingPredictionEvaluator` e publicação via `ContestResultEventPublisher`. Os adapters `JdbcPendingPredictionEvaluator` e `SpringContestResultEventPublisher` são separados do `JdbcContestRepository`. O evento é publicado dentro da transação e tratado em `BEFORE_COMMIT` para que concurso, avaliação e learning event confirmem ou revertam juntos.

### P2 — Limites de agregado e integridade

- `Contest` garante exatamente 15 dezenas ao reconstruir/validar no Java; SQL garante faixa 1–25 e unicidade por concurso, mas a constraint relacional não consegue impor facilmente 15 linhas por pai. Escritas fora da aplicação podem deixar concurso incompleto; o repositório detecta isso ao ler e lança inconsistência persistida.
- A importação possui parser e prévia, mas é um caso de uso de arquivo; caminho, formatos e transação por linha devem permanecer adaptadores de entrada, sem colocar detalhes de arquivo/infra no domínio.
- `migration_probe` persiste desde V1 como artefato temporário documentado. Preservar V1 é correto; limpeza exige decisão/migração aditiva futura.

### P2 — Coesão por capacidade de negócio

Pacotes estão agrupados por camada (`api`, `application`, `domain`, `infrastructure`) em vez de feature. Com cerca de 129 classes Java, isto dificulta navegar verticalmente por previsão, pesquisa, importação e ML e permite que dependências indevidas passem despercebidas. Não recomenda-se quebrar o monólito em microsserviços; o custo de distribuição não tem justificativa aqui.

### P2 — Fronteiras distintas de aprendizado

`MineContestPatternsService` produz insights versionados e o `OptimizedGameGeneratorService` consulta evidências elegíveis e executa avaliação/backtesting temporal com baseline aleatória. Já `MachineLearningService` treina regressão logística, persiste artefatos, avalia temporalmente e gera previsões experimentais. O ML está exposto em `/api/ml` e **não é chamado pelo `OptimizedGameGeneratorService` nem substitui a política champion**. `LearningPolicyService` atualiza métricas prospectivas da estratégia; não retreina automaticamente o classificador ML. São três ciclos relacionados, porém não equivalentes — a UI e a linguagem de “agente” devem preservar essa distinção.

### P3 — Regras arquiteturais automatizadas

`ArchitectureRulesTest` aplica oito regras ArchUnit ao domínio compartilhado, application/ports de concursos/importação, adapters e dependências entre slices de `contests`. Elas não abrangem módulos protegidos de agentes/ML nem todas as APIs legadas; a proteção global continua pendente.

## Leitura SOLID / DDD / Clean

- **SRP:** várias responsabilidades são reconhecíveis (domínio, persistência, API); os acoplamentos de cadastro/mineração e eventos reduzem a separação em alguns casos.
- **OCP/DIP:** interfaces de saída existem, sobretudo repositórios, mas não são usadas de modo consistente; algumas APIs chamam portas diretamente e serviços apontam para infraestrutura concreta.
- **ISP:** as portas parecem específicas por capacidade, bom sinal; confirmar interfaces muito amplas na próxima etapa com seus consumidores.
- **DDD:** records e invariantes são usados, mas a organização é por camada. `Contest` e `LotofacilGame` são modelos ricos em validação; “agente”, “estratégia”, “modelo ML” e “previsão” são conceitos diferentes e precisam limites semânticos claros.
- **Clean/Hexagonal:** há adaptadores de saída e domínio isolado, mas faltam portas de entrada consistentes e a camada application não é independente do framework. Chamar a estrutura de “hexagonal completa” seria exagero.

## Riscos que não devem virar refatoração automática

1. Mover classes pode mudar component scanning, proxy transacional, listeners, ordenação de migrations/tests e convenções Eclipse.
2. Trocar eventos ou transações sem testes de integração pode alterar atomicidade de avaliação de previsões e aprendizado.
3. A constraint de exatamente 15 dezenas no banco requer mecanismo e política de escrita; não se deve introduzir trigger ou reescrever migration aplicada sem decisão.
4. Os scores retrospectivos são critérios experimentais, não probabilidades de prêmio; eventual integração entre ML e agente avaliador requer protocolo e evidência walk-forward, não apenas nova dependência.
5. Dados históricos e procedência/redistribuição de CSV não são decisões arquiteturais e não devem ser alterados durante refatoração.
