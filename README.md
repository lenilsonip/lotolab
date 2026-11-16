# LotoLab

Laboratório pessoal para análise retrospectiva dos resultados da Lotofácil. O sistema oferece estatísticas descritivas, geração experimental de combinações, backtesting e pesquisa versionada de padrões.

**Limitação científica:** sorteios são eventos aleatórios independentes. Frequências, backtests e modelos experimentais não demonstram capacidade de prever concursos futuros nem aumentam comprovadamente a probabilidade de prêmio.

## Estado e tecnologias

- Backend: Java 21, Spring Boot 4.1.1, Maven.
- Persistência: PostgreSQL e Spring JDBC/JdbcTemplate; não usa JPA/Hibernate.
- Migrações: Flyway V1–V9.
- Frontend: Angular 21, TypeScript, RxJS e Vitest.
- Pesquisa: módulo research para mineração temporal; avaliação de modelos e estratégias é experimental.
- Gemini opcional para interpretação textual; cálculos estatísticos permanecem no backend.

## Arquitetura

Monólito modular. O backend contém adaptadores REST e JDBC, lógica de aplicação e domínio Java. Os módulos contests e research aplicam fronteiras hexagonais em diferentes graus; partes mais antigas ainda usam pacotes legados br.com.lotolab. Persistência SQL/JDBC.

    backend/
      src/main/java/                   API, aplicação, domínio, infraestrutura e research
      src/main/resources/db/migration/ V1–V9
    frontend/
      src/app/                         UI Angular e integração REST
    infrastructure/
      compose.yaml                     PostgreSQL isolado desta cópia
    docs/architecture/                 ADRs, fluxos e diagramas selecionados

## Pré-requisitos

- JDK 21.
- Node.js 22.12+ e npm compatível com package-lock.json.
- Docker Desktop com Docker Compose para PostgreSQL local e testes Testcontainers.
- Internet na primeira execução para baixar dependências.
- Gemini é opcional.

## Executar localmente no Windows

Na raiz, crie o arquivo local de ambiente:

    Copy-Item .env.example .env

Edite .env e substitua os valores de exemplo. Mantenha GEMINI_API_KEY vazia se não for usar a integração. Nunca versione .env.

Inicie a instância isolada desta cópia:

    docker compose --env-file .env -f infrastructure/compose.yaml up -d

O Compose publica PostgreSQL somente em 127.0.0.1:55432 e usa o volume lotolab-publicacao-postgres-data, distinto do projeto original. Não use down -v. Para parar sem apagar dados:

    docker compose --env-file .env -f infrastructure/compose.yaml stop

Em outro terminal, inicie o backend:

    Set-Location backend
    .\mvnw.cmd spring-boot:run

O backend importa o .env da raiz por ../.env, relativo ao diretório backend. O Flyway aplica migrações pendentes na base configurada. Execute somente contra a instância isolada desta cópia.

Em outro terminal, inicie o frontend:

    Set-Location frontend
    npm ci
    npm start

Abra http://localhost:4200. O proxy Angular envia /api para http://127.0.0.1:8080. No Eclipse, configure backend como diretório de trabalho.

## Testes e builds

Backend, incluindo integração PostgreSQL com Testcontainers:

    Set-Location backend
    .\mvnw.cmd clean verify

Testes Angular e build de produção:

    Set-Location frontend
    npm ci
    npm test
    npm run build

Testcontainers requer Docker. Os testes usam containers temporários e não devem apontar ao PostgreSQL local persistente.

## API disponível

Base local: http://127.0.0.1:8080.

| Método | Endpoint | Uso |
|---|---|---|
| POST | /api/concursos | Registrar concurso |
| GET | /api/concursos/{number} | Consultar concurso |
| GET | /api/concursos/ultimo | Consultar concurso mais recente |
| GET | /api/concursos?page=0&size=20 | Listar concursos |
| POST | /api/admin/importacao-concursos/preview | Pré-validar CSV multipart |
| POST | /api/admin/importacao-concursos | Importar CSV multipart |
| GET | /api/analises/frequencia-dezenas | Frequência histórica |
| GET | /api/analises/frequencia-recente?concursos=100 | Frequência recente e histórica |
| POST | /api/jogos/geracao | Geração uniforme com critérios |
| POST | /api/experimentos/gerar-otimizado | Geração experimental com backtesting |
| POST | /api/experimentos/gerar-politica | Geração pela política de aprendizagem |
| POST | /api/experimentos/analise | Comparação e interpretação opcional |
| POST, GET | /api/previsoes | Registrar/listar tentativas |
| POST, GET | /api/ml/treinamentos | Solicitar treino/consultar job |
| GET | /api/ml/modelos | Consultar modelos |
| POST | /api/ml/previsoes-experimentais | Inferência experimental |
| POST | /api/agentes/pesquisador/minerar | Solicitar mineração |
| GET | /api/agentes/pesquisador/insights | Consultar evidências |
| GET | /api/agentes/pesquisador/interpretacao | Consultar interpretação |
| GET | /api/agentes/jobs/{id} | Consultar job |
| GET | /api/agentes/jobs/latest?agentKey=PATTERN_RESEARCHER | Consultar job recente |
| GET | /api/agentes/aprendizagem | Consultar política e eventos |

Consulte os controllers para contratos, validação e status HTTP.

## Migrações Flyway

| Versão | Escopo |
|---|---|
| V1 | migration_probe, artefato temporário |
| V2 | Concursos e dezenas |
| V3 | Tentativas de previsão |
| V4 | Insights de padrões |
| V5 | Auditoria dos agentes cooperativos |
| V6 | Interpretações |
| V7 | Interpretação ausente/desabilitada |
| V8 | Jobs duráveis e aprendizagem |
| V9 | Modelos logísticos experimentais |

V1–V9 são histórico imutável. Mudanças futuras devem usar migrações incrementais novas.

## Dados históricos

CSV/XLSX não são distribuídos enquanto procedência e direitos de redistribuição não forem confirmados. Obtenha dados de fonte com permissão adequada, mantenha-os localmente fora do Git e siga o contrato CSV da aplicação. Não corrija datas ou resultados suspeitos automaticamente. Testes devem usar fixtures sintéticas pequenas.

## Gemini e privacidade

A integração opcional depende de GEMINI_API_KEY. Análise manual e interpretação dos agentes podem enviar métricas e contexto de previsão ao Google, consumindo quota ou gerando cobrança conforme a conta. O padrão dos agentes é desabilitado. Nunca grave chaves em código, logs, documentação ou Git.

## Segurança e limites de uso

Este laboratório não está preparado como serviço público. A importação administrativa usa token, mas não foi identificada autenticação geral nos demais endpoints de escrita e processamento. Não exponha o backend à internet sem autenticação, autorização, limites de requisição, monitoramento e revisão de segurança.

Scores retrospectivos e métricas experimentais não são probabilidades de prêmio nem evidência suficiente de vantagem futura.

## Licença

Nenhuma licença foi escolhida. Até que o proprietário escolha e adicione uma licença, publicar o código não concede autorização geral para reutilização, modificação ou redistribuição.