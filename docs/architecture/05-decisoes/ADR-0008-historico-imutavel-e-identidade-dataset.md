# ADR-0008 — Imutabilidade do histórico e identidade do dataset

- **Status:** Aceita para os fluxos de aplicação atuais.
- **Contexto:** a chave de idempotência da mineração identifica versão do algoritmo e limites do histórico. Ela não detecta alteração de um concurso mantendo os mesmos limites.
- **Decisão:** cadastro e importação via aplicação não corrigem resultados existentes. Regravar conteúdo idêntico é idempotente; regravar o mesmo número com data ou dezenas diferentes gera conflito. A chave de run permanece `pattern-miner:<versão>:<primeiro>:<último>` e não se adiciona checksum neste estágio.
- **Evidência:** `JdbcContestRepository.saveIfAbsent` relê e compara o concurso existente; os testes de cadastro e importação confirmam conflito e preservação da data/dezenas armazenadas. `Contest` é imutável como record e não oferece operação de atualização.
- **Limite:** a política não impede alterações feitas diretamente por SQL com privilégios suficientes. Tais alterações são operação administrativa fora dos fluxos suportados e podem tornar a identidade por faixa insuficiente. Antes de introduzir correções oficiais, será necessário aprovar uma política de versionamento/reprocessamento e considerar checksum canônico do conteúdo, sem sobrescrever insights históricos.
- **Consequências:** nenhuma migration ou mudança de dados é necessária agora. Runs e evidências permanecem auditáveis e não são reescritos silenciosamente pelos fluxos normais.
