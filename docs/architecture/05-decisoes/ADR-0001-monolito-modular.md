# ADR-0001 — Manter monólito modular

- **Status:** Proposta — requer aprovação antes de implementação estrutural.
- **Contexto:** API, agentes, importação e ML usam o mesmo histórico PostgreSQL e possuem transações/regras compartilhadas. Separar serviços ampliaria operação e consistência distribuída sem necessidade demonstrada.
- **Decisão proposta:** manter um deploy Spring Boot e modularizar por capacidade, reforçando limites de dependência por pacote/teste.
- **Consequências:** implantações simples e transações locais; exige disciplina para impedir dependências cruzadas. Não implica criar dezenas de módulos Maven ou camadas para cada classe.
- **Alternativas rejeitadas por ora:** microsserviços e reescrita completa. Nenhuma dor operacional atual justifica esse custo.
