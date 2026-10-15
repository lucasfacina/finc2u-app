# Finc2U App

Aplicação de gestão financeira pessoal, nascida como atividade de fixação de estágio (já entregue) e hoje base do
backend do TCC: um módulo de sugestões de educação financeira por motor de regras (Drools). O plano de evolução está
em `docs/Finc2UApp_Roadmap_TCC.md`.

## Stack Tecnológica

* Java 21
* Spring Boot 4.1 (Web MVC, Data JPA, Validation)
* H2 Database (desenvolvimento) / PostgreSQL (produção/TCC)
* Documentação da API: OpenAPI + Swagger UI (springdoc)
* Maven & Lombok
* Automação: Nx + pnpm (scripts em `scripts/`) · PostgreSQL 18 em contêiner (`docker-compose.yml`) · Flyway (CLI)

## Como Executar Localmente

1. Certifique-se de ter o Java 21 instalado.
2. Clone o repositório.
3. Execute o projeto via IDE (IntelliJ/Eclipse) ou via terminal com o Maven Wrapper:
   `./mvnw spring-boot:run`
4. A aplicação estará disponível em `http://localhost:8080`.
5. A documentação interativa da API (Swagger UI) fica em `http://localhost:8080/swagger-ui.html` — dá para testar
   todos os endpoints por ela. A especificação OpenAPI está em `http://localhost:8080/v3/api-docs`.
6. O console do banco de dados H2 pode ser acessado em `http://localhost:8080/h2-console` (URL:
   `jdbc:h2:file:./data/finc2udb`, User: `sa`, Senha em branco).

> O banco de desenvolvimento é um arquivo em `./data/` (relativo ao diretório de execução). Para recriá-lo do zero
> (os seeders repopulam tudo), apague a pasta `data/` e suba a aplicação novamente.

## Executar Testes

```bash
./mvnw test
```

## Automação de Desenvolvimento (Nx)

Os comandos do dia a dia (rodar, testar, banco de dados, auditoria) ficam como alvos do Nx, para ter um único ponto
de entrada. Eles só chamam o Maven Wrapper e os scripts de `scripts/` — o projeto continua rodando sem o Nx
(`./mvnw spring-boot:run`, `./mvnw test`).

**Pré-requisitos:** Node 22+ e pnpm. Para o PostgreSQL local, Docker ou o Apple `container` (com `docker compose`).

```bash
pnpm install                              # uma vez, na raiz
pnpm exec nx run server:<alvo>            # executa um alvo
pnpm exec nx show project server          # lista todos os alvos
pnpm exec nx run server:<alvo> -- <args>  # repassa argumentos ao script (use sempre o `--`)
```

> O `--` é necessário: sem ele o Nx interpreta opções como `-c` (alias de `--configuration`) por conta própria.

### Servidor

Os alvos aparecem agrupados no Nx Console (Application, Build, Verification, Help, Quality, bancos de dados etc.).

| Grupo        | Alvo          | O que faz                                                                                                                |
|--------------|---------------|--------------------------------------------------------------------------------------------------------------------------|
| Application  | `dev`         | Sobe a aplicação (`spring-boot:run`) com a raiz do repositório como diretório de trabalho                                |
| Application  | `devTest`     | Sobe a aplicação com o classpath de teste (H2 em memória, sem seeders); usa a porta 8080, então pare o `dev` antes       |
| Build        | `build`       | Compila e empacota o JAR executável sem rodar os testes (`./mvnw package -DskipTests`)                                   |
| Build        | `clean`       | `./mvnw clean`                                                                                                           |
| Build        | `classes`     | Compila o código principal e processa os recursos (`./mvnw compile`)                                                     |
| Build        | `testClasses` | Compila também o código de teste (`./mvnw test-compile`)                                                                 |
| Verification | `test`        | Roda a suíte de testes (`./mvnw test`)                                                                                   |
| Verification | `verify`      | `./mvnw verify`                                                                          |
| Quality      | `depsUpdates` | Lista atualizações disponíveis do parent, das dependências diretas e das propriedades de versão                          |
| Quality      | `depsAudit`   | Audita as dependências (diretas e transitivas) na base [OSV](https://osv.dev); sai com código 1 se achar vulnerabilidade |
| API          | `apiExport`   | Exporta o `/v3/api-docs` para `apps/server/openapi.json` (a aplicação precisa estar rodando)                             |

O `dev` usa a raiz como diretório de trabalho para que o banco H2 fique no mesmo `data/` usado pela IDE.

Passos isolados do build, no grupo **Build Steps**: `compileJava`, `compileTestJava`, `processResources` e
`processTestResources`. O `compileTestJava` exige o código principal já compilado.

### Diagnóstico, documentação e setup

| Grupo         | Alvo                   | O que faz                                                                                             |
|---------------|------------------------|-------------------------------------------------------------------------------------------------------|
| Help          | `dependencies`         | Árvore de dependências (diretas e transitivas); filtre com `-- -Dincludes=grupo:artefato`             |
| Help          | `dependencyInsight`    | Árvore detalhada: mostra duplicatas omitidas e de onde veio cada versão (ex.: `version managed from`) |
| Help          | `dependencyManagement` | POM efetivo, com o `dependencyManagement` e os plugins herdados do Spring Boot                        |
| Help          | `buildEnvironment`     | Plugins do build e as dependências de cada um                                                         |
| Help          | `javaToolchains`       | Versões de Maven e Java (e do sistema) usadas pelo build                                              |
| Help          | `properties`           | Propriedades do projeto, inclusive as versões herdadas do BOM do Spring Boot                          |
| Build Setup   | `wrapper`              | Regenera o Maven Wrapper; para trocar a versão: `-- -Dmaven=3.9.x` (altera `mvnw` e `.mvn/`)          |
| Documentation | `javadoc`              | Gera o Javadoc em `apps/server/target/reports/apidocs`                                                |

### Relatórios

| Grupo     | Alvo            | O que faz                                                                                                                                                                |
|-----------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Reporting | `historyExport` | Exporta o histórico de commits (`AAAA-MM-DD \| mensagem`, do mais antigo ao mais recente) para `docs/historico_commits.txt`; destino alterável com `-- --output arquivo` |

### Banco H2 (desenvolvimento)

| Alvo      | O que faz                                                                                                 |
|-----------|-----------------------------------------------------------------------------------------------------------|
| `h2Reset` | Apaga os arquivos `finc2udb*` do H2. Pede confirmação; recusa se a aplicação estiver rodando (porta 8080) |
| `h2Fresh` | Apaga o banco **sem confirmação** e já sobe a aplicação (os seeders repopulam tudo)                       |

Opções do script: `-- --dry-run` (só lista o que seria apagado) e `-- --yes` (não pergunta). Fora de um terminal
interativo a confirmação é obrigatória (`--yes`).

### PostgreSQL local (contêiner)

Prepara o terreno para a Fase 14: a aplicação ainda usa o H2, e o PostgreSQL já pode ser subido, populado e salvo.

1. `cp .env.example .env` e ajuste os valores (o `.env` é ignorado pelo Git). Troque a senha. Se a porta `5432` já
   estiver em uso (por exemplo, por outro projeto), mude o `DB_PORT`.
2. `pnpm exec nx run server:pgStart`

| Alvo        | O que faz                                                                                                                          |
|-------------|------------------------------------------------------------------------------------------------------------------------------------|
| `pgStart`   | Sobe o PostgreSQL 18 (`docker compose up -d`); escuta apenas em `127.0.0.1`                                                        |
| `pgStop`    | Para o contêiner (os dados ficam no volume)                                                                                        |
| `pgStatus`  | Mostra o estado do contêiner                                                                                                       |
| `pgLogs`    | Acompanha os logs (`-f`)                                                                                                           |
| `pgPsql`    | Abre o `psql`; aceita argumentos: `-- -c "select version()"`                                                                       |
| `pgReset`   | **Apaga o contêiner e o volume de dados** e sobe um banco vazio. Pede confirmação (`-- --yes` para pular)                          |
| `pgBackup`  | Gera `backups/finc2u-AAAAMMDD-HHMMSS.dump` (`pg_dump`, formato custom; a pasta é ignorada pelo Git)                                |
| `pgRestore` | Restaura um backup, **recriando o banco**: `-- <arquivo.dump \| latest> [--yes]`. Antes, gera um `pre-restore-*.dump` de segurança |
| `pgSchema`  | Gera o rascunho da migração `V1__baseline.sql` a partir do schema atual (só estrutura; não sobrescreve sem `--force`)              |

> Com o Apple `container`, o `healthcheck` e o `restart` do `docker-compose.yml` são ignorados: o `pgStatus` não
> mostra "healthy" e o contêiner não sobe sozinho após reiniciar o Mac. Use `pgStart` e, para saber se o banco já
> aceita conexões, `pgPsql -- -c "select 1"`.

### Flyway (migrações)

O Flyway roda como CLI em contêiner (mesma versão que o Spring Boot gerencia) contra o PostgreSQL local, lendo as
migrações de `apps/server/src/main/resources/db/migration`. Ainda não há dependência do Flyway no `pom.xml` — ela
entra na Fase 14, quando a aplicação passa a migrar sozinha ao subir.

| Alvo             | O que faz                                                                                                   |
|------------------|-------------------------------------------------------------------------------------------------------------|
| `flywayBaseline` | Marca um banco já existente como baseline (cria o histórico sem aplicar migrações): `-- -baselineVersion=1` |
| `flywayClean`    | **Apaga todos os objetos dos schemas** (tabelas e dados). Pede confirmação (`-- --yes` para pular)          |
| `flywayInfo`     | Mostra o histórico e o estado das migrações                                                                 |
| `flywayMigrate`  | Aplica as migrações pendentes                                                                               |
| `flywayRepair`   | Repara a tabela de histórico (remove registros de migrações falhas e realinha checksums)                    |
| `flywayValidate` | Confere se as migrações aplicadas batem com os arquivos (checksum)                                          |

## Integração Contínua

O workflow `.github/workflows/ci.yml` roda no GitHub Actions a cada push na `main`, em pull requests e uma vez por
semana (novas vulnerabilidades surgem mesmo sem mudança no código):

| Job      | O que faz                                                                                                          |
|----------|--------------------------------------------------------------------------------------------------------------------|
| `server` | Testes, build e verificações com Java 21 (`./mvnw -B -ntp verify`, em `apps/server`); não roda na execução semanal |
| `audit`  | Audita as dependências Maven na base OSV (`scripts/deps-audit.sh`); falha se houver vulnerabilidade conhecida      |

Os dois jobs são os mesmos comandos que você roda localmente (`nx run server:verify` e `nx run server:depsAudit`).

## Autenticação

Esta versão não possui autenticação real. O `userId` é passado como `@RequestParam` nos endpoints que precisam
identificar o usuário, simulando o contexto de autenticação que será implementado no TCC.

---

## Endpoints Disponíveis

### Usuários — `/users`

| Método | Endpoint      | Descrição                | Parâmetros                       |
|--------|---------------|--------------------------|----------------------------------|
| POST   | `/users`      | Criar usuário            | Body: `UserRequest`              |
| GET    | `/users`      | Listar todos os usuários | —                                |
| GET    | `/users/{id}` | Buscar usuário por ID    | `id` (path)                      |
| PUT    | `/users/{id}` | Atualizar usuário        | `id` (path), Body: `UserRequest` |
| DELETE | `/users/{id}` | Deletar usuário          | `id` (path)                      |

### Contas/Cartões — `/card-accounts`

| Método | Endpoint              | Descrição                 | Parâmetros                                   |
|--------|-----------------------|---------------------------|----------------------------------------------|
| POST   | `/card-accounts`      | Criar conta/cartão        | `userId` (query), Body: `CardAccountRequest` |
| GET    | `/card-accounts`      | Listar cartões do usuário | `userId` (query)                             |
| GET    | `/card-accounts/{id}` | Buscar cartão por ID      | `id` (path)                                  |
| PUT    | `/card-accounts/{id}` | Atualizar cartão          | `id` (path), Body: `CardAccountRequest`      |
| DELETE | `/card-accounts/{id}` | Deletar cartão            | `id` (path)                                  |

### Tags — `/tags`

| Método | Endpoint     | Descrição            | Parâmetros                      |
|--------|--------------|----------------------|---------------------------------|
| POST   | `/tags`      | Criar tag            | Body: `TagRequest`              |
| GET    | `/tags`      | Listar todas as tags | —                               |
| GET    | `/tags/{id}` | Buscar tag por ID    | `id` (path)                     |
| PUT    | `/tags/{id}` | Atualizar tag        | `id` (path), Body: `TagRequest` |
| DELETE | `/tags/{id}` | Deletar tag          | `id` (path)                     |

### Despesas — `/expenses`

| Método | Endpoint                | Descrição                    | Parâmetros                                                                                    |
|--------|-------------------------|------------------------------|-----------------------------------------------------------------------------------------------|
| POST   | `/expenses`             | Criar despesa                | `userId` (query), Body: `ExpenseRequest`                                                      |
| GET    | `/expenses`             | Listar despesas do usuário   | `userId`* (query)                                                                             |
| GET    | `/expenses/filters`     | Listar despesas com filtros  | `userId`*, `month`, `year`, `status`, `type`, `cardAccountId`, `minPrice`, `maxPrice` (query) |
| GET    | `/expenses/{id}`        | Buscar despesa por ID        | `id` (path)                                                                                   |
| GET    | `/expenses/projections` | Projeção de parcelas futuras | `userId`* (query)                                                                             |
| PUT    | `/expenses/{id}`        | Atualizar despesa            | `id` (path), Body: `ExpenseRequest`                                                           |
| PATCH  | `/expenses/{id}/status` | Atualizar status da despesa  | `id` (path), `status` (query)                                                                 |
| DELETE | `/expenses/{id}`        | Deletar despesa              | `id` (path)                                                                                   |

### Ganhos Extras — `/extra-incomes`

| Método | Endpoint                | Descrição                        | Parâmetros                                                    |
|--------|-------------------------|----------------------------------|---------------------------------------------------------------|
| POST   | `/extra-incomes`        | Registrar ganho extra            | `userId` (query), Body: `ExtraIncomeRequest`                  |
| GET    | `/extra-incomes`        | Listar ganhos extras do usuário  | `userId`* (query)                                             |
| GET    | `/extra-incomes/period` | Listar ganhos extras por período | `userId`*, `startDate`, `endDate` (query, formato YYYY-MM-DD) |
| GET    | `/extra-incomes/{id}`   | Buscar ganho extra por ID        | `id` (path)                                                   |
| PUT    | `/extra-incomes/{id}`   | Atualizar ganho extra            | `id` (path), Body: `ExtraIncomeRequest`                       |
| DELETE | `/extra-incomes/{id}`   | Deletar ganho extra              | `id` (path)                                                   |

### Resumo Mensal — `/monthly-summary`

| Método | Endpoint                                   | Descrição                              | Parâmetros                       |
|--------|--------------------------------------------|----------------------------------------|----------------------------------|
| POST   | `/monthly-summary`                         | (Re)calcular ou atualizar resumo       | Body: `{ userId, month, year }`  |
| GET    | `/monthly-summary/history`                 | Listar histórico de resumos do usuário | `userId`* (query)                |
| GET    | `/monthly-summary/{userId}/{year}/{month}` | Buscar resumo por período              | `userId`, `year`, `month` (path) |
| DELETE | `/monthly-summary/{userId}/{year}/{month}` | Deletar resumo de um período           | `userId`, `year`, `month` (path) |

### Histórico de Salários — `/salary-history`

| Método | Endpoint               | Descrição                            | Parâmetros                                                       |
|--------|------------------------|--------------------------------------|------------------------------------------------------------------|
| POST   | `/salary-history`      | Registrar novo salário com vigência  | Body: `{ userId, baseSalary, effectiveFrom }` (data: YYYY-MM-DD) |
| GET    | `/salary-history`      | Listar histórico de salários         | `userId`* (query)                                                |
| GET    | `/salary-history/{id}` | Buscar entrada por ID                | `id` (path)                                                      |
| PUT    | `/salary-history/{id}` | Corrigir salário ou data de vigência | `id` (path), Body: `{ userId, baseSalary, effectiveFrom }`       |
| DELETE | `/salary-history/{id}` | Remover entrada                      | `id` (path)                                                      |

