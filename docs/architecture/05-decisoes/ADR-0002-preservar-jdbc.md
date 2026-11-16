# ADR-0002 — Preservar JDBC/JdbcTemplate

- **Status:** Proposta compatível com a implementação atual.
- **Contexto:** persistência explícita, SQL PostgreSQL e agregados pequenos; projeto e documentação vedam ORM nesta etapa.
- **Decisão proposta:** continuar com Spring JDBC/JdbcTemplate; separar SQL/row mapping nos adaptadores e usar Testcontainers para comportamento real do PostgreSQL.
- **Consequências:** controle de SQL e cardinalidade; mais código de mapeamento e responsabilidade explícita de transação. Não introduzir JPA/Hibernate.
- **Observação:** cardinalidade de exatamente 15 depende de domínio, transações e controle de quem pode escrever. Schema atual não impõe cardinalidade agregada por constraint simples.
