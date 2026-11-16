# Documentação de arquitetura — LotoLab

Esta pasta contém auditoria, arquitetura alvo e decisões implementadas. A arquitetura alvo continua sendo uma proposta para capacidades ainda não migradas; a Fase 2.1 está registrada no relatório e ADRs próprios.

1. [Inventário](00-inventario.md)
2. [Diagnóstico](01-diagnostico.md)
3. [Arquitetura alvo proposta](02-arquitetura-alvo.md)
4. [Plano e gates](03-plano-refatoracao.md)
5. [Fluxos após Fase 3](04-fluxos.md)
6. [Decisões arquiteturais (ADRs)](05-decisoes/)
7. [Guia de estudo](06-guia-de-estudo.md)
8. [Diagrama da arquitetura atual](diagrams/current-architecture.mmd)
9. [Diagrama da arquitetura proposta](diagrams/target-architecture.mmd)
10. [Relatório da Fase 2.1](RELATORIO_FASE2_1_CORRECOES.md)
11. [Relatório da Fase 3 — mineração](RELATORIO_FASE3_MINERACAO.md)
12. [ADR-0007 — mineração hexagonal](05-decisoes/ADR-0007-mineracao-hexagonal.md)
13. [Relatório da Fase 3.1 — fechamento da mineração](RELATORIO_FASE3_1_MINERACAO.md)
14. [ADR-0008 — histórico imutável e identidade do dataset](05-decisoes/ADR-0008-historico-imutavel-e-identidade-dataset.md)

Os arquivos são atualizados por etapa e cada relatório delimita seu escopo. O código Java está em `Projetos/java`. A Fase 3 migrou somente a capacidade de mineração de padrões; não aplicou migrations no banco local, não alterou dados/volumes e não modificou o frontend.
