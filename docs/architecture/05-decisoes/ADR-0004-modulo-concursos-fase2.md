# ADR-0004 — Fronteiras hexagonais para concursos

- **Status:** Implementada e corrigida na Fase 2.1.
- **Contexto:** controllers de concurso conheciam serviços/repositórios concretos; o repositório JDBC executava persistência, avaliação e publicação de evento; cadastro e importação acionavam pesquisa/transação com fronteiras pouco explícitas.
- **Decisão:** usar `br.com.lotolab.contests.application.port.in` para os casos de uso, `port.out` para capacidades externas, handlers/services específicos em `contests.application.service`, controllers/DTOs em `contests.adapter.in.web`, composição em `contests.bootstrap.ContestApplicationConfiguration` e adapters Spring/JDBC em `infrastructure`.
- **Persistência:** manter `JdbcContestRepository` como implementação única da porta de repositório; a aplicação não conhece JdbcTemplate/SQL. `Contest` e `LotofacilGame` permanecem no domínio compartilhado por serem consumidos por módulos protegidos.
- **Compatibilidade:** rotas, JSON, status, erros, idempotência, conflito, importação, V1–V9 e frontend permanecem. `application.port.ContestRepository` e as fachadas antigas são transição, documentada em ADR-0006.
- **Consequências:** API não acessa diretamente porta de saída; gravação é orquestrada por caso de uso; adapters são separados por capacidade; ArchUnit verifica as fronteiras do módulo. A aplicação como um todo ainda não é hexagonal.
- **Pendência:** migração do domínio compartilhado e dos módulos de agentes/ML exige fases próprias; não está autorizada nesta etapa.
