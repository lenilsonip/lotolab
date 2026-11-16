# LotoLab — Instruções de engenharia

## Objetivos do projeto

O LotoLab é uma plataforma de análise estatística dos resultados da Lotofácil e também um projeto de formação prática em engenharia de software com IA. Seus objetivos são:

- Importar e validar resultados históricos oficiais da Lotofácil.
- Armazenar concursos e dezenas em PostgreSQL.
- Calcular estatísticas e executar simulações.
- Avaliar estratégias com backtesting temporal e comparação com referências aleatórias.
- Futuramente, integrar um modelo de linguagem por meio de ferramentas Java e desenvolver um agente de análise supervisionado.

## Stack inicial

- Java 21.
- Spring Boot em versão estável compatível com Java 21.
- Maven.
- Spring JDBC com `JdbcTemplate` para persistência PostgreSQL.
- PostgreSQL.
- Flyway para migrações do banco.
- JUnit 5 e Spring Boot Test.
- Testcontainers quando apropriado.
- Docker Compose para o ambiente local.
- Git para versionamento.

Não introduza outras tecnologias sem justificativa técnica. Evite adicionar dependências sem necessidade justificada.

## Ambiente de desenvolvimento

- O Eclipse IDE é a IDE utilizada neste projeto.
- Oriente a importação e o uso do projeto no Eclipse, incluindo o suporte Maven (m2e) quando pertinente.
- Não recomende IntelliJ IDEA como ambiente de desenvolvimento para este projeto.

## Princípios arquiteturais

- Comece com um monólito modular; não adote microsserviços.
- Separe domínio, aplicação, infraestrutura e interfaces externas de forma proporcional à complexidade do projeto.
- Aplique SOLID e mantenha baixo acoplamento, evitando abstrações desnecessárias.
- Mantenha as regras de negócio independentes de detalhes de infraestrutura sempre que possível.
- Não adote programação reativa sem uma necessidade concreta.
- Mantenha a configuração externa ao código e nunca versione segredos.

## Qualidade

- Escreva testes automatizados para regras de negócio e funcionalidades importantes.
- Valide entradas e trate erros explicitamente.
- Não considere uma tarefa concluída sem verificar a compilação e os testes aplicáveis.
- Documente decisões arquiteturais relevantes.
- Faça alterações pequenas, revisáveis e incrementais.

## Integridade estatística

- Trate resultados oficiais como dados observados e preserve sua origem.
- Garanta que importações possam ser repetidas sem duplicar concursos.
- Separe cálculos determinísticos de explicações produzidas por modelos de linguagem.
- Não apresente padrões históricos como evidência automática de previsão de sorteios futuros.
- Previna vazamento de informação em experimentos e backtests.
- Compare estratégias com referências adequadas e contabilize custos quando houver avaliação econômica.
- Registre parâmetros, períodos e métricas dos experimentos.

## Segurança e autonomia da IA

- Não permita que um modelo de linguagem altere diretamente resultados oficiais ou métricas calculadas.
- Disponibilize ao futuro agente apenas ferramentas explicitamente autorizadas.
- Não implemente apostas automáticas nem transações financeiras.
- Solicite aprovação humana para ações destrutivas ou operações de risco.
- Não execute comandos destrutivos nem sobrescreva arquivos existentes sem autorização.

## Forma de trabalho

Antes de cada alteração relevante:

1. Explique brevemente o objetivo e a abordagem.
2. Identifique os arquivos que serão criados ou modificados.
3. Implemente somente o escopo solicitado.
4. Execute verificações e testes pertinentes.
5. Informe o que foi alterado, os resultados dos testes e eventuais limitações.
