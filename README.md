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

### Usuário — `/usuario`

| Método   | Endpoint         | Descrição                | Parâmetros                          | 
|----------|------------------|--------------------------|-------------------------------------|
| POST     | `/usuarios`      | Criar usuário            | Body: `UserRequestDTO`              |
| GET      | `/usuarios`      | Listar todos os usuários | —                                   |
| GET      | `/usuarios/{id}` | Buscar usuário por ID    | `id` (path)                         |
| PUT      | `/usuarios/{id}` | Atualizar usuário        | `id` (path), Body: `UserRequestDTO` |
| DELETE   | `/usuarios/{id}` | Deletar usuário          | `id` (path)                         |

### Contas/Cartões — `/contas-cartao`

| Método   | Endpoint              | Descrição                 | Parâmetros                                      |
|----------|-----------------------|---------------------------|-------------------------------------------------|
| POST     | `/contas-cartao`      | Criar conta/cartão        | `userId` (query), Body: `CardAccountRequestDTO` |
| GET      | `/contas-cartao`      | Listar cartões do usuário | `userId` (query)                                |
| GET      | `/contas-cartao/{id}` | Buscar cartão por ID      | `id` (path)                                     |
| PUT      | `/contas-cartao/{id}` | Atualizar cartão          | `id` (path), Body: `CardAccountRequestDTO`      |
| DELETE   | `/contas-cartao/{id}` | Deletar cartão            | `id` (path)                                     |

### Tags — `/tags`

| Método   | Endpoint     | Descrição            | Parâmetros                         |
|----------|--------------|----------------------|------------------------------------|
| POST     | `/tags`      | Criar tag            | Body: `TagRequestDTO`              |
| GET      | `/tags`      | Listar todas as tags | —                                  |
| GET      | `/tags/{id}` | Buscar tag por ID    | `id` (path)                        |
| PUT      | `/tags/{id}` | Atualizar tag        | `id` (path), Body: `TagRequestDTO` |
| DELETE   | `/tags/{id}` | Deletar tag          | `id` (path)                        |

### Despesas — `/despesas`

| Método   | Endpoint                | Descrição                    | Parâmetros                                                                                    |
|----------|-------------------------|------------------------------|-----------------------------------------------------------------------------------------------|
| POST     | `/despesas`             | Criar despesa                | Body: `ExpenseRequestDTO`                                                                     |
| GET      | `/despesas`             | Listar despesas com filtros  | `userId`*, `month`, `year`, `status`, `type`, `cardAccountId`, `minPrice`, `maxPrice` (query) |
| GET      | `/despesas/{id}`        | Buscar despesa por ID        | `id` (path)                                                                                   |
| PUT      | `/despesas/{id}`        | Atualizar despesa            | `id` (path), Body: `ExpenseRequestDTO`                                                        |
| PATCH    | `/despesas/{id}/status` | Atualizar status da despesa  | `id` (path), `status` (query)                                                                 |
| DELETE   | `/despesas/{id}`        | Deletar despesa              | `id` (path)                                                                                   |
| GET      | `/despesas/projecao`    | Projeção de parcelas futuras | `userId`* (query)                                                                             |

### Ganhos Extras — `/extras`

| Método | Endpoint       | Descrição                       | Parâmetros                          |
|--------|----------------|---------------------------------|-------------------------------------|
| POST   | `/extras`      | Registrar ganho extra           | Body: `ExtraRequestDTO`             |
| GET    | `/extras`      | Listar ganhos extras do usuário | `userId`* (query)                   |
| GET    | `/extras/{id}` | Buscar ganho extra por ID       | `id` (path)                         |
| PUT    | `/extras/{id}` | Atualizar ganho extra           | `id` (path), Body: `ExtraRequestDTO`|
| DELETE | `/extras/{id}` | Deletar ganho extra             | `id` (path)                         |

### Resumo Mensal — `/resumo-mensal`

| Método   | Endpoint                                 | Descrição                              | Parâmetros                           |
|----------|------------------------------------------|----------------------------------------|--------------------------------------|
| POST     | `/resumo-mensal`                         | Calcular ou atualizar resumo           | Body: `MonthlySummaryRequestDTO`     |
| GET      | `/resumo-mensal`                         | Calcular ou retornar resumo do período | `userId`*, `month`*, `year`* (query) |
| GET      | `/resumo-mensal/historico`               | Listar histórico de resumos do usuário | `userId`* (query)                    |
| GET      | `/resumo-mensal/{userId}/{year}/{month}` | Buscar resumo por período              | `userId`, `year`, `month` (path)     |
| DELETE   | `/resumo-mensal/{userId}/{year}/{month}` | Deletar resumo de um período           | `userId`, `year`, `month` (path)     |
