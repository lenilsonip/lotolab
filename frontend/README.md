# LotoLab Frontend

Frontend Angular standalone, com TypeScript, para consultar estatísticas e gerar combinações. Requer Node.js 22.12 ou superior na linha 22 e npm 10.9.x. No ambiente desta tarefa, npm 11.5 apresentou uma falha interna no resolvedor; por isso, os comandos abaixo fixam npm 10.9.3.

## Iniciar localmente

1. Inicie o PostgreSQL e a API Spring Boot na porta `8080` (veja o README da raiz).
2. Instale as dependências na primeira execução e inicie o Angular:

```powershell
cd frontend
npx --yes npm@10.9.3 install --no-audit --no-fund
npm start
```

Abra `http://localhost:4200`. O servidor Angular encaminha `/api` para `http://127.0.0.1:8080` por meio de `proxy.conf.json`, evitando exigir alteração de CORS no backend.

## Configurar a URL da API

Todos os serviços usam o `apiBaseUrl` central em `src/environments/environment.ts` (produção) e `src/environments/environment.development.ts` (desenvolvimento). Em desenvolvimento, mantenha-o como `/api` e ajuste o campo `target` em `proxy.conf.json` se a API mudar de host/porta. Para produção, use `/api` com um reverse proxy no mesmo host ou substitua `apiBaseUrl` pela origem pública da API na configuração de build apropriada. Os componentes não contêm URLs de backend.

## Verificar

```powershell
npx --yes npm@10.9.3 test
npx --yes npm@10.9.3 run build
```

## Funcionalidades

- Painel com o total de concursos e as 25 frequências históricas.
- Comparação percentual recente/histórico nas janelas de 50, 100, 200 e 500 concursos.
- Gerador com quantidade de jogos (1–100), filtro de pares/ímpares e semente opcional; cada resultado pode ser copiado.
- Página experimental `/agentes` com frequência por janelas, coocorrências, faixas, sequências, comparação walk-forward e histórico de previsões/avaliações.
- Justificativas textuais do Gemini quando a interpretação estiver habilitada no backend; indisponibilidade da IA não impede a leitura das métricas calculadas.

O gerador apenas amostra combinações equiprováveis dentro dos filtros; frequência passada não indica maior chance de ganhar.

## Atualizar o histórico

A tela de importação CSV fica em `http://localhost:4200/importacao`. Para habilitá-la, defina `LOTOLAB_IMPORT_TOKEN` em `.env` na raiz do backend antes de iniciar o Spring Boot. Use um token local aleatório com pelo menos 32 caracteres; o frontend o mantém apenas em memória e o envia como cabeçalho de cada solicitação administrativa. Sem token configurado ou com token menor, o backend mantém a importação desabilitada.

Selecione arquivo `.csv` de até 1 MB e informe o token. Primeiro clique em **Validar e gerar prévia**. A tela separa os concursos novos, idênticos já persistidos, conflitos, linhas inválidas e avisos. Revise essa lista; depois use **Revisar e confirmar importação** e a confirmação final para gravar. O backend revalida o arquivo antes de persistir; grava cada concurso em transação própria e não sobrescreve concursos existentes. O relatório final detalha inseridos, existentes, conflitos, linhas rejeitadas e linhas não processadas após eventual falha.

O formato é o mesmo importador já usado pelo backend: UTF-8, ponto e vírgula, cabeçalho `Concurso;Data;bola 1;...;bola 15`, datas `dd/MM/yyyy`. A tela envia ao backend e reaproveita a validação Java existente. O endpoint administrativo aceita apenas conexões locais e exige token; não o exponha diretamente à internet. Verifique a procedência e as condições de redistribuição do CSV antes de importar.

O cadastro manual unitário continua disponível em `http://localhost:4200/cadastro`.

## Pesquisador e avaliador experimental

A página `http://localhost:4200/agentes` mostra os insights da última execução do pesquisador e o histórico de previsões pendentes e avaliadas. Use **Executar pesquisa** para disparar a mineração agora; a rotina também roda no agendamento configurado no backend.

O Gemini é opcional e fica desabilitado por padrão. No `.env` do backend, defina `LOTOLAB_AGENTS_GEMINI_ENABLED=true` e uma chave `GEMINI_API_KEY` válida para permitir interpretações textuais. Sem isso, o backend grava `SKIPPED_DISABLED` ou `SKIPPED_NO_API_KEY`, sem chamadas externas. O texto gerado é explicativo: os cálculos, as métricas, os estados e a avaliação temporal são produzidos por Java. O painel sempre identifica scores como retrospectivos e não como probabilidade de prêmio.

No painel inicial, a geração otimizada aceita um concurso-alvo futuro opcional. Deixe vazio para registrar no próximo número após o concurso mais alto conhecido. A API rejeita destinos já realizados. A resposta compara score de validação e baseline aleatória com o mesmo filtro de paridade e orçamento, e a combinação gerada fica registrada automaticamente. Depois do cadastro/importação do resultado real desse concurso, a página atualiza a previsão para `EVALUATED` e mostra os acertos. O score e o status comparativo são experimentais, não probabilidades de sorteio.
