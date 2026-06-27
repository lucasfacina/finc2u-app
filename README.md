# Finc2U App
Aplicação de gestão financeira pessoal, desenvolvida como atividade de fixação de estágio e pré-projeto de TCC.

## Stack Tecnológica
* Java 21
* Spring Boot 3.3.13 (Web, Data JPA, Validation)
* H2 Database (desenvolvimento) / PostgreSQL (produção/TCC)
* Maven & Lombok
  
## Como Executar Localmente
1. Certifique-se de ter o Java 21 instalado.
2. Clone o repositório.
3. Execute o projeto via IDE (IntelliJ/Eclipse) ou via terminal com o Maven Wrapper:
   `./mvnw spring-boot:run`
4. A aplicação estará disponível em `http://localhost:8080`.
5. O console do banco de dados H2 pode ser acessado em `http://localhost:8080/h2-console` (URL: `jdbc:h2:file:./dados/finc2udb`, User: `sa`, Senha em branco).

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

| Método | Endpoint                   | Descrição                    | Parâmetros                                                                               |
|--------|----------------------------|------------------------------|------------------------------------------------------------------------------------------|
| POST   | `/expenses`                | Criar despesa                | `userId` (query), Body: `ExpenseRequest`                                                 |
| GET    | `/expenses`                | Listar despesas do usuário   | `userId`* (query)                                                                        |
| GET    | `/expenses/filters`        | Listar despesas com filtros  | `userId`*, `month`, `year`, `status`, `type`, `cardAccountId`, `minPrice`, `maxPrice` (query) |
| GET    | `/expenses/{id}`           | Buscar despesa por ID        | `id` (path)                                                                              |
| GET    | `/expenses/projections`    | Projeção de parcelas futuras | `userId`* (query)                                                                        |
| PUT    | `/expenses/{id}`           | Atualizar despesa            | `id` (path), Body: `ExpenseRequest`                                                      |
| PATCH  | `/expenses/{id}/status`    | Atualizar status da despesa  | `id` (path), `status` (query)                                                            |
| DELETE | `/expenses/{id}`           | Deletar despesa              | `id` (path)                                                                              |

### Ganhos Extras — `/extras`

| Método | Endpoint       | Descrição                       | Parâmetros                         |
|--------|----------------|---------------------------------|------------------------------------|
| POST   | `/extras`      | Registrar ganho extra           | `userId` (query), Body: `ExtraRequest` |
| GET    | `/extras`      | Listar ganhos extras do usuário | `userId`* (query)                  |
| GET    | `/extras/{id}` | Buscar ganho extra por ID       | `id` (path)                        |
| PUT    | `/extras/{id}` | Atualizar ganho extra           | `id` (path), Body: `ExtraRequest`  |
| DELETE | `/extras/{id}` | Deletar ganho extra             | `id` (path)                        |

### Resumo Mensal — `/monthly-summary`

| Método | Endpoint                                        | Descrição                              | Parâmetros                               |
|--------|-------------------------------------------------|----------------------------------------|------------------------------------------|
| POST   | `/monthly-summary`                              | (Re)calcular ou atualizar resumo       | Body: `MonthlySummaryRequest`            |
| GET    | `/monthly-summary/history`                      | Listar histórico de resumos do usuário | `userId`* (query)                        |
| GET    | `/monthly-summary/{userId}/{year}/{month}`      | Buscar resumo por período              | `userId`, `year`, `month` (path)         |
| DELETE | `/monthly-summary/{userId}/{year}/{month}`      | Deletar resumo de um período           | `userId`, `year`, `month` (path)         |
