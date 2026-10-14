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

## Como Executar Localmente
1. Certifique-se de ter o Java 21 instalado.
2. Clone o repositório.
3. Execute o projeto via IDE (IntelliJ/Eclipse) ou via terminal com o Maven Wrapper:
   `./mvnw spring-boot:run`
4. A aplicação estará disponível em `http://localhost:8080`.
5. A documentação interativa da API (Swagger UI) fica em `http://localhost:8080/swagger-ui.html` — dá para testar
   todos os endpoints por ela. A especificação OpenAPI está em `http://localhost:8080/v3/api-docs`.
6. O console do banco de dados H2 pode ser acessado em `http://localhost:8080/h2-console` (URL: `jdbc:h2:file:./data/finc2udb`, User: `sa`, Senha em branco).

> O banco de desenvolvimento é um arquivo em `./data/` (relativo ao diretório de execução). Para recriá-lo do zero
> (os seeders repopulam tudo), apague a pasta `data/` e suba a aplicação novamente.

## Executar Testes

```bash
./mvnw test
```

## Autenticação

Esta versão não possui autenticação real. O `userId` é passado como `@RequestParam` nos endpoints que precisam identificar o usuário, simulando o contexto de autenticação que será implementado no TCC.

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

| Método | Endpoint               | Descrição                 | Parâmetros                                    |
|--------|------------------------|---------------------------|-----------------------------------------------|
| POST   | `/card-accounts`       | Criar conta/cartão        | `userId` (query), Body: `CardAccountRequest`  |
| GET    | `/card-accounts`       | Listar cartões do usuário | `userId` (query)                              |
| GET    | `/card-accounts/{id}`  | Buscar cartão por ID      | `id` (path)                                   |
| PUT    | `/card-accounts/{id}`  | Atualizar cartão          | `id` (path), Body: `CardAccountRequest`       |
| DELETE | `/card-accounts/{id}`  | Deletar cartão            | `id` (path)                                   |

### Tags — `/tags`

| Método | Endpoint     | Descrição            | Parâmetros                      |
|--------|--------------|----------------------|---------------------------------|
| POST   | `/tags`      | Criar tag            | Body: `TagRequest`              |
| GET    | `/tags`      | Listar todas as tags | —                               |
| GET    | `/tags/{id}` | Buscar tag por ID    | `id` (path)                     |
| PUT    | `/tags/{id}` | Atualizar tag        | `id` (path), Body: `TagRequest` |
| DELETE | `/tags/{id}` | Deletar tag          | `id` (path)                     |

### Despesas — `/expenses`

| Método | Endpoint                   | Descrição                    | Parâmetros                                                                                    |
|--------|----------------------------|------------------------------|-----------------------------------------------------------------------------------------------|
| POST   | `/expenses`                | Criar despesa                | `userId` (query), Body: `ExpenseRequest`                                                      |
| GET    | `/expenses`                | Listar despesas do usuário   | `userId`* (query)                                                                             |
| GET    | `/expenses/filters`        | Listar despesas com filtros  | `userId`*, `month`, `year`, `status`, `type`, `cardAccountId`, `minPrice`, `maxPrice` (query) |
| GET    | `/expenses/{id}`           | Buscar despesa por ID        | `id` (path)                                                                                   |
| GET    | `/expenses/projections`    | Projeção de parcelas futuras | `userId`* (query)                                                                             |
| PUT    | `/expenses/{id}`           | Atualizar despesa            | `id` (path), Body: `ExpenseRequest`                                                           |
| PATCH  | `/expenses/{id}/status`    | Atualizar status da despesa  | `id` (path), `status` (query)                                                                 |
| DELETE | `/expenses/{id}`           | Deletar despesa              | `id` (path)                                                                                   |

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

| Método | Endpoint                                        | Descrição                              | Parâmetros                               |
|--------|-------------------------------------------------|----------------------------------------|------------------------------------------|
| POST   | `/monthly-summary`                              | (Re)calcular ou atualizar resumo       | Body: `{ userId, month, year }`          |
| GET    | `/monthly-summary/history`                      | Listar histórico de resumos do usuário | `userId`* (query)                        |
| GET    | `/monthly-summary/{userId}/{year}/{month}`      | Buscar resumo por período              | `userId`, `year`, `month` (path)         |
| DELETE | `/monthly-summary/{userId}/{year}/{month}`      | Deletar resumo de um período           | `userId`, `year`, `month` (path)         |

### Histórico de Salários — `/salary-history`

| Método | Endpoint           | Descrição                             | Parâmetros                                                          |
|--------|--------------------|---------------------------------------|---------------------------------------------------------------------|
| POST   | `/salary-history`       | Registrar novo salário com vigência   | Body: `{ userId, baseSalary, effectiveFrom }` (data: YYYY-MM-DD)  |
| GET    | `/salary-history`       | Listar histórico de salários          | `userId`* (query)                                                  |
| GET    | `/salary-history/{id}`  | Buscar entrada por ID                 | `id` (path)                                                        |
| PUT    | `/salary-history/{id}`  | Corrigir salário ou data de vigência  | `id` (path), Body: `{ userId, baseSalary, effectiveFrom }`         |
| DELETE | `/salary-history/{id}`  | Remover entrada                       | `id` (path)                                                        |

