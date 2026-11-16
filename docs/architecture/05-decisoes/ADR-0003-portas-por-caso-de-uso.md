# ADR-0003 — Portas de entrada e saída por capacidade

- **Status:** Implementada para o módulo de concursos nesta Fase 2.1; adoção global ainda pendente.
- **Contexto:** portas de saída existem em `application.port`, mas controllers e application ainda atravessam fronteiras em alguns fluxos.
- **Decisão:** declarar portas de entrada para cadastro, leitura, último, lista e importação; controllers e runner as consomem. Portas de saída de repositório, unidade de trabalho, avaliação pendente, publisher de resultado e trigger de mineração pertencem à application de concursos; adaptadores implementam JDBC, Spring transaction/event e mineração.
- **Consequências:** casos de uso testáveis sem container Spring e controllers sem repositório/adapter. A interface antiga `application.port.ContestRepository` estende a nova porta para preservar os módulos de agentes/ML sem duplicar adapter.
