# Atividade – GraphQL na prática: Biblioteca

**Disciplina:** Integração de Software – Aula 07 (GraphQL)
**Dupla:** _[Nome 1]_ e _[Nome 2]_

## Cenário

Uma biblioteca quer uma API para o seu acervo. O site mostra a lista de livros, o app de celular mostra só título e disponibilidade, e a página de um livro mostra também o autor e os outros livros dele.

É o mesmo problema do e-commerce visto em aula: telas diferentes precisam de combinações diferentes de dados relacionados. Com REST isso gera **várias requisições** e **dados desnecessários**. Com GraphQL, o cliente pede exatamente o que precisa numa chamada só.

Temos duas entidades relacionadas: **Autor** e **Livro**. Um autor escreve vários livros e cada livro pertence a um autor (1:N).

## Tecnologias

- Java 21
- Spring Boot 4 + **Spring for GraphQL** (implementação oficial de GraphQL do ecossistema Spring, baseada no `graphql-java`)
- Maven (via Maven Wrapper `mvnw`, sem precisar instalar o Maven)

Como visto em aula, GraphQL é uma **especificação**, não um framework. Aqui ela é implementada em Java em vez de JavaScript/Apollo.

## Estrutura do projeto

```
atividade-graphql-java/
├── src/main/resources/
│   ├── graphql/schema.graphqls          ← Schema (contrato da API)
│   └── application.properties           ← endpoint /graphql e GraphiQL
├── src/main/java/br/edu/uniacademia/biblioteca/
│   ├── BibliotecaGraphqlApplication.java  ← classe principal (Spring Boot)
│   ├── model/
│   │   ├── Autor.java                     ← entidade Autor (record)
│   │   ├── Livro.java                     ← entidade Livro (record)
│   │   └── NovoLivroInput.java            ← input da mutation
│   ├── repository/
│   │   └── BibliotecaRepository.java      ← dados em memória
│   └── controller/
│       ├── BibliotecaController.java      ← Resolvers (queries, mutations e relacionamentos)
│       └── RegraDeNegocioException.java
├── ClienteDemo.java                       ← cliente que executa todas as operações
└── saida_demo.txt                         ← saída completa do ClienteDemo
```

## Como executar

**1. Subir o servidor**

```bash
# Windows (PowerShell / CMD)
.\mvnw.cmd spring-boot:run

# Linux / macOS / Git Bash
./mvnw spring-boot:run
```
Pelo IntelliJ, também dá para abrir a pasta e rodar a classe `BibliotecaGraphqlApplication`.

- Endpoint da API: `POST http://localhost:8080/graphql`
- GraphiQL (IDE no navegador): http://localhost:8080/graphiql
- Schema em texto: http://localhost:8080/graphql/schema

**2. Executar as operações da atividade** (em outro terminal, com o servidor rodando)

```bash
java ClienteDemo.java
```
O `ClienteDemo` usa só o `HttpClient` do próprio Java. Ele envia cada query e mutation para `POST /graphql` e imprime a requisição e a resposta. A saída completa está em `saida_demo.txt`.

## Conceitos da aula aplicados

| Conceito (slides) | Onde aparece no projeto |
|---|---|
| Schema como contrato da API | `schema.graphqls` (seção 1) |
| Tipos escalares e modificadores `!` e `[ ]` | `Livro` usa `ID`, `String`, `Int`, `Float` e `Boolean` |
| Resolvers (parent, args, context, info) | `BibliotecaController` (seção 2) |
| Query simples e query complexa | Queries 1 a 6 (seção 3) |
| Mutation | `adicionarLivro` e `emprestarLivro` (seção 4) |
| Relacionamentos (grafo de dados) | `Livro.autor` e `Autor.livros` (seção 5) |
| Cliente define os dados | Queries 1 e 2 (seção 6) |
| Estrutura da response (`data`, `errors`, HTTP 200) | Seção 7 |
| Tipagem forte e validação | Query com campo inexistente é rejeitada (seção 7) |
| Auto-documentação / Introspection / GraphiQL | Descrições no schema e query `__type` (seção 8) |
| Overfetching, underfetching, RTT | Comparação com REST (seção 9) |

---

## 1. Schema – o contrato da API

Arquivo `src/main/resources/graphql/schema.graphqls`. O Spring for GraphQL carrega automaticamente os arquivos dessa pasta.

```graphql
"""Pessoa que escreveu um ou mais livros do acervo"""
type Autor {
  id: ID!
  nome: String!
  nacionalidade: String!
  """Livros deste autor (relacionamento Autor -> [Livro])"""
  livros: [Livro!]!
}

"""Livro do acervo da biblioteca"""
type Livro {
  id: ID!
  titulo: String!
  ano: Int!
  genero: String!
  nota: Float!
  disponivel: Boolean!
  """Autor do livro (relacionamento Livro -> Autor)"""
  autor: Autor!
}

type Query {
  livros(genero: String): [Livro!]!
  livro(id: ID!): Livro
  autores: [Autor!]!
  autor(id: ID!): Autor
}

input NovoLivroInput {
  titulo: String!
  ano: Int!
  genero: String!
  autorId: ID!
}

type Mutation {
  adicionarLivro(input: NovoLivroInput!): Livro!
  emprestarLivro(id: ID!): Livro!
}
```
_(Versão resumida. O arquivo completo tem descrição em todos os campos.)_

- **Types** `Autor` e `Livro`: as entidades do domínio.
- **Query**: operações de leitura. **Mutation**: operações de escrita.
- **Tipos escalares**: `ID`, `String`, `Int` (`ano`), `Float` (`nota`) e `Boolean` (`disponivel`).
- **Modificadores**: `!` torna o campo obrigatório (non-null). `[Livro!]!` é uma lista não nula de livros não nulos. `livro(id: ID!): Livro` não tem `!` no retorno porque o livro pode não existir (retorna `null`).
- **Input**: `NovoLivroInput` agrupa os dados de entrada da mutation.
- **Descrições** (`"""..."""`): viram documentação automática no GraphiQL.

No Java, cada type vira um `record` com os mesmos nomes de campo:

```java
public record Autor(String id, String nome, String nacionalidade) { }

public record Livro(String id, String titulo, int ano, String genero, double nota,
                    boolean disponivel, String autorId) { }
```

O record `Livro` guarda só o `autorId`, como uma chave estrangeira. O campo `autor` do schema é resolvido no controller (seção 2).

## 2. Resolvers – o "cérebro da API"

No Spring for GraphQL, os resolvers são métodos de um `@Controller`, ligados ao schema por anotações:

| Anotação | Resolve |
|---|---|
| `@QueryMapping` | um campo de `type Query` (com o mesmo nome do método) |
| `@MutationMapping` | um campo de `type Mutation` |
| `@SchemaMapping(typeName, field)` | um campo de outro type, como `Livro.autor` |
| `@Argument` | recebe um argumento da query |

```java
@Controller
public class BibliotecaController {

    // Resolver raiz: Query.livro(id)
    @QueryMapping
    public Livro livro(@Argument String id) {
        return repository.buscarLivro(id).orElse(null);
    }

    // Resolver de campo: Livro.autor. O parâmetro "livro" é o parent
    @SchemaMapping(typeName = "Livro", field = "autor")
    public Autor autorDoLivro(Livro livro) {
        return repository.buscarAutor(livro.autorId()).orElseThrow();
    }

    // Resolver de campo: Autor.livros
    @SchemaMapping(typeName = "Autor", field = "livros")
    public List<Livro> livrosDoAutor(Autor autor) {
        return repository.livrosDoAutor(autor.id());
    }
}
```

Correspondência com a assinatura `(parent, args, context, info)` vista em aula:

- **parent**: o objeto pai, recebido como parâmetro (`Livro livro` em `autorDoLivro`).
- **args**: os parâmetros anotados com `@Argument`.
- **info / context**: `DataFetchingEnvironment`, que pode ser recebido como parâmetro quando necessário (é usado no tratamento de erros).

Campos simples como `titulo` e `ano` não precisam de método: são lidos direto do record. Os resolvers de relacionamento **só executam se o cliente pedir o campo**. Se a query não pede `autor`, o autor nem é buscado.

Ao subir, o Spring confere se todo campo do schema tem um resolver ou um campo correspondente no record. O log mostra `Unmapped fields: {}`, ou seja, nenhum campo ficou sem resolver.

Fluxo de uma requisição (slide "Fluxo Detalhado"):

1. O cliente faz um `POST /graphql` com a query no body.
2. O servidor **valida** a query contra o schema.
3. Os **resolvers** (métodos do controller) de cada campo pedido são executados.
4. Os resultados são **agregados** numa árvore com a forma da query.
5. O cliente recebe um **JSON** com exatamente os campos pedidos.

Aqui os dados ficam em memória (`BibliotecaRepository`). Num sistema real, o repository poderia acessar PostgreSQL (JPA), MongoDB ou outra API, e o cliente não perceberia diferença nenhuma, porque o resolver isola o schema da implementação.

## 3. Queries

**Query 1 – Query simples: listar livros**
```graphql
query {
  livros {
    titulo
    ano
  }
}
```
```json
{
  "data": {
    "livros": [
      { "titulo": "Dom Casmurro", "ano": 1899 },
      { "titulo": "Memórias Póstumas de Brás Cubas", "ano": 1881 },
      { "titulo": "A Hora da Estrela", "ano": 1977 },
      { "titulo": "1984", "ano": 1949 },
      { "titulo": "A Revolução dos Bichos", "ano": 1945 }
    ]
  }
}
```

**Query 2 – A mesma query, pedindo outros campos** (ver seção 6)
```graphql
query {
  livros {
    id
    titulo
    genero
    nota
    disponivel
  }
}
```
```json
{
  "data": {
    "livros": [
      { "id": "1", "titulo": "Dom Casmurro", "genero": "Romance", "nota": 4.7, "disponivel": true },
      { "id": "2", "titulo": "Memórias Póstumas de Brás Cubas", "genero": "Romance", "nota": 4.6, "disponivel": true },
      { "id": "3", "titulo": "A Hora da Estrela", "genero": "Romance", "nota": 4.4, "disponivel": false },
      { "id": "4", "titulo": "1984", "genero": "Distopia", "nota": 4.8, "disponivel": true },
      { "id": "5", "titulo": "A Revolução dos Bichos", "genero": "Fábula", "nota": 4.5, "disponivel": true }
    ]
  }
}
```

**Query 3 – Campo com argumento (usando variável)**
```graphql
query LivrosPorGenero($genero: String) {
  livros(genero: $genero) {
    titulo
    ano
  }
}
```
Variáveis: `{ "genero": "Romance" }`
```json
{
  "data": {
    "livros": [
      { "titulo": "Dom Casmurro", "ano": 1899 },
      { "titulo": "Memórias Póstumas de Brás Cubas", "ano": 1881 },
      { "titulo": "A Hora da Estrela", "ano": 1977 }
    ]
  }
}
```

**Query 4 – Relacionamento Livro → Autor**
```graphql
query {
  livro(id: "4") {
    titulo
    ano
    autor {
      nome
      nacionalidade
    }
  }
}
```
```json
{
  "data": {
    "livro": {
      "titulo": "1984",
      "ano": 1949,
      "autor": { "nome": "George Orwell", "nacionalidade": "Britânica" }
    }
  }
}
```

**Query 5 – Relacionamento Autor → [Livro]**
```graphql
query {
  autor(id: "1") {
    nome
    livros {
      titulo
      ano
    }
  }
}
```
```json
{
  "data": {
    "autor": {
      "nome": "Machado de Assis",
      "livros": [
        { "titulo": "Dom Casmurro", "ano": 1899 },
        { "titulo": "Memórias Póstumas de Brás Cubas", "ano": 1881 }
      ]
    }
  }
}
```

**Query 6 – Query complexa: livro + autor + outros livros do autor**

É a "tela de detalhes do livro". A estrutura aninhada da query segue os relacionamentos do domínio, como no exemplo `user → orders → product` da aula.
```graphql
query {
  livro(id: "4") {
    titulo
    nota
    autor {
      nome
      livros {
        titulo
        ano
      }
    }
  }
}
```
```json
{
  "data": {
    "livro": {
      "titulo": "1984",
      "nota": 4.8,
      "autor": {
        "nome": "George Orwell",
        "livros": [
          { "titulo": "1984", "ano": 1949 },
          { "titulo": "A Revolução dos Bichos", "ano": 1945 }
        ]
      }
    }
  }
}
```

## 4. Mutations

```java
@MutationMapping
public Livro adicionarLivro(@Argument NovoLivroInput input) {
    if (repository.buscarAutor(input.autorId()).isEmpty()) {
        throw new RegraDeNegocioException("Autor com id " + input.autorId() + " não existe");
    }
    Livro novo = new Livro(repository.proximoIdLivro(), input.titulo(), input.ano(),
            input.genero(), 0.0, true, input.autorId());
    return repository.salvar(novo);
}
```
O Spring converte o `input` do GraphQL direto para o record `NovoLivroInput`.

**Mutation 1 – Adicionar um livro**
```graphql
mutation NovoLivro($input: NovoLivroInput!) {
  adicionarLivro(input: $input) {
    id
    titulo
    disponivel
    autor {
      nome
    }
  }
}
```
Variáveis: `{ "input": { "titulo": "Laços de Família", "ano": 1960, "genero": "Contos", "autorId": "2" } }`
```json
{
  "data": {
    "adicionarLivro": {
      "id": "6",
      "titulo": "Laços de Família",
      "disponivel": true,
      "autor": { "nome": "Clarice Lispector" }
    }
  }
}
```
A mutation também deixa o cliente escolher os campos da resposta. Aqui ele já recebe o nome do autor do livro recém-criado, sem precisar de outra chamada.

**Mutation 2 – Emprestar um livro**
```graphql
mutation {
  emprestarLivro(id: "1") {
    titulo
    disponivel
  }
}
```
```json
{ "data": { "emprestarLivro": { "titulo": "Dom Casmurro", "disponivel": false } } }
```

Depois das mutations, a query `autores { nome livros { titulo disponivel } }` já mostra "Laços de Família" entre os livros da Clarice Lispector e "Dom Casmurro" como indisponível (ver `saida_demo.txt`).

## 5. Relacionamento entre as entidades

```
Autor ──livros──▶ [Livro]
  ▲                  │
  └──────autor───────┘
```

- **Livro → Autor** (campo `Livro.autor`, método `autorDoLivro`): um livro pertence a um autor (Query 4).
- **Autor → [Livro]** (campo `Autor.livros`, método `livrosDoAutor`): um autor tem vários livros (Query 5).

A navegação funciona nos dois sentidos e pode ser encadeada: livro → autor → livros do autor (Query 6). Em REST, cada um desses passos exigiria um endpoint e uma chamada a mais.

## 6. O cliente escolhe os campos

> "Em REST, o servidor decide quais dados retornar. Em GraphQL, o cliente decide quais dados receber." (slide "Cliente Define os Dados")

A mesma query `livros` devolve formatos diferentes conforme o pedido do cliente:

| Cliente | Pede | Recebe |
|---|---|---|
| App de celular | `livros { titulo ano }` | `{ "titulo": "Dom Casmurro", "ano": 1899 }` |
| Painel da biblioteca | `livros { id titulo genero nota disponivel }` | `{ "id": "1", "titulo": "Dom Casmurro", "genero": "Romance", "nota": 4.7, "disponivel": true }` |

O método Java (`livros()`) é o mesmo nos dois casos e devolve o record completo. Quem filtra os campos é o GraphQL, de acordo com a query. Campos não pedidos não aparecem na resposta.

## 7. Estrutura da response e tratamento de erros

Como mostrado em aula, a resposta tem `data` na raiz, eventuais erros no campo `errors`, e o status HTTP é **200**.

**Erro de execução**: a regra de negócio lança `RegraDeNegocioException`, e um `@GraphQlExceptionHandler` transforma a exceção num erro GraphQL:
```java
@GraphQlExceptionHandler
public GraphQLError tratarRegraDeNegocio(RegraDeNegocioException ex, DataFetchingEnvironment env) {
    return GraphqlErrorBuilder.newError(env)
            .message(ex.getMessage())
            .errorType(ErrorType.BAD_REQUEST)
            .build();
}
```
```json
{
  "errors": [{
    "message": "Autor com id 99 não existe",
    "locations": [{ "line": 2, "column": 3 }],
    "path": ["adicionarLivro"],
    "extensions": { "classification": "BAD_REQUEST" }
  }],
  "data": null
}
```

**Erro de validação** (tipagem forte): a query pede um campo que não existe no schema e é rejeitada **antes** de executar qualquer resolver.
```graphql
query {
  livro(id: "1") {
    titulo
    editora
  }
}
```
```json
{
  "errors": [{
    "message": "Validation error (FieldUndefined@[livro/editora]) : Field 'editora' in type 'Livro' is undefined",
    "locations": [{ "line": 4, "column": 5 }],
    "extensions": { "classification": "ValidationError" }
  }]
}
```

> Observação: com `Accept: application/json` (usado pelo `ClienteDemo`), o Spring responde sempre HTTP 200, como visto em aula. O GraphiQL usa o formato mais novo da especificação GraphQL-over-HTTP (`application/graphql-response+json`). Nesse formato, erros de validação vêm com HTTP 400, mas o corpo da resposta é o mesmo.

## 8. Auto-documentação (Introspection e GraphiQL)

O schema **é** a documentação. Qualquer cliente pode consultá-lo em tempo de execução:
```graphql
query {
  __type(name: "Livro") {
    name
    description
    fields { name description }
  }
}
```
```json
{
  "data": {
    "__type": {
      "name": "Livro",
      "description": "Livro do acervo da biblioteca",
      "fields": [
        { "name": "id", "description": null },
        { "name": "titulo", "description": null },
        { "name": "ano", "description": "Ano de publicação" },
        { "name": "genero", "description": null },
        { "name": "nota", "description": "Nota média dos leitores, de 0 a 5" },
        { "name": "disponivel", "description": "true se o livro está na estante, false se está emprestado" },
        { "name": "autor", "description": "Autor do livro (relacionamento Livro -> Autor)" }
      ]
    }
  }
}
```
O GraphiQL (`http://localhost:8080/graphiql`) usa essa introspection para oferecer autocomplete e a aba de documentação, sem nenhum arquivo separado (como o Swagger no REST).

---

## 9. Comparação com uma implementação REST

### Os mesmos recursos em REST

Em Spring, uma versão REST teria um `@RestController` com um método por endpoint:

| Operação | REST (`@RestController`) | GraphQL (`POST /graphql`) |
|---|---|---|
| Listar livros | `@GetMapping("/livros")` | `livros { ... }` |
| Filtrar por gênero | `GET /livros?genero=Romance` | `livros(genero: "Romance") { ... }` |
| Buscar livro | `@GetMapping("/livros/{id}")` | `livro(id: "4") { ... }` |
| Buscar autor | `@GetMapping("/autores/{id}")` | `autor(id: "3") { ... }` |
| Livros de um autor | `@GetMapping("/autores/{id}/livros")` | `autor(id: "1") { livros { ... } }` |
| Cadastrar livro | `@PostMapping("/livros")` | `mutation { adicionarLivro(...) }` |
| Emprestar livro | `@PatchMapping("/livros/{id}")` | `mutation { emprestarLivro(id: "1") }` |

REST precisa de **7 endpoints** com verbos HTTP diferentes. GraphQL usa **1 endpoint** com dois tipos de operação (query e mutation).

### Overfetching: dados desnecessários

O app de celular só quer mostrar o título do livro. Em REST, `GET /livros/4` devolve o record inteiro:
```json
{ "id": "4", "titulo": "1984", "ano": 1949, "genero": "Distopia", "nota": 4.8, "disponivel": true, "autorId": "3" }
```
Em GraphQL, `livro(id: "4") { titulo }` devolve só `{ "titulo": "1984" }`. Isso economiza banda, processamento e bateria, o que importa em redes móveis.

Para evitar isso em REST, seria preciso criar DTOs ou endpoints específicos para cada tela, e o backend teria que mudar sempre que uma tela mudasse.

### Underfetching: chamadas encadeadas

Para montar a tela de detalhes do livro (Query 6), o frontend REST precisaria de:

```
1ª chamada  GET /livros/4           → { titulo, nota, autorId: 3, ... }
2ª chamada  GET /autores/3          → { nome, nacionalidade, ... }
3ª chamada  GET /autores/3/livros   → [ { titulo, ano, genero, nota, ... }, ... ]
```
São **3 chamadas = 3 RTTs**, e a 2ª e a 3ª dependem da 1ª (é preciso ler o `autorId` antes), então nem dá para fazer em paralelo. O frontend ainda precisa orquestrar as chamadas e juntar os resultados.

Em GraphQL, é **1 chamada = 1 RTT**, já com os dados juntos e só com os campos usados. Se a tela listasse 10 livros com seus autores, o REST precisaria de 1 + 10 chamadas (o problema N+1 visto em aula). No GraphQL continua sendo uma chamada só.

### Diferença fundamental

| | REST | GraphQL (este projeto) |
|---|---|---|
| Quem decide os dados | Servidor | Cliente |
| Endpoints | Vários (`/livros`, `/autores/{id}/livros`...) | Um (`/graphql`) |
| Operações | Verbos HTTP (GET, POST, PATCH, DELETE) | Query e Mutation |
| Erros | Códigos HTTP variados (404, 400...) | HTTP 200 com o campo `errors` |
| Contrato e documentação | Swagger/OpenAPI, separado do código | `schema.graphqls` + introspection |
| Relacionamentos | Um endpoint ou chamada por relação | Campos aninhados na mesma query |

### Onde o REST seria melhor

Os slides "Quando NÃO usar GraphQL" também se aplicam aqui:

- **Caching HTTP**: `GET /livros/4` pode ser cacheado por navegador e CDN. No GraphQL tudo é `POST /graphql`, então o cache precisa de estratégias mais elaboradas.
- **API simples**: se a biblioteca tivesse apenas "listar livros" e nenhuma tela com dados relacionados, um `@RestController` seria mais simples. GraphQL seria over-engineering.
- **Upload de arquivos**: subir a capa de um livro é natural em REST (`MultipartFile`) e exige soluções extras em GraphQL.
- **Curva de aprendizado**: schema, resolvers e anotações novas para a equipe.
- **Cuidado no servidor**: no GraphQL, o resolver `Autor.livros` roda uma vez para cada autor da lista. Com banco de dados real, isso precisa ser otimizado (o Spring for GraphQL tem `@BatchMapping` para isso), e é bom limitar a profundidade das queries.

### REST vs gRPC vs GraphQL

Ligando com a atividade anterior de gRPC (`ProductService`):

| Dimensão | REST | gRPC | GraphQL |
|---|---|---|---|
| Protocolo | HTTP + JSON | HTTP/2 + Protobuf | HTTP + JSON |
| Contrato | Documentação (Swagger) | `.proto` (rigoroso) | Schema (flexível) |
| Flexibilidade | Baixa (servidor decide) | Baixa (proto fixo) | Alta (cliente decide) |
| Cenário ideal | APIs simples, caching HTTP | Microsserviços internos | APIs com muitos relacionamentos, mobile |

No gRPC, `GetProductById` sempre devolvia a mensagem `ProductResponse` inteira, definida no `.proto`. Aqui, `livro(id)` devolve só os campos que o cliente pede.

## Conclusão

Para a biblioteca, o GraphQL resolve bem o problema central da aula. Clientes diferentes (site, app, painel) recebem só os dados de que precisam, e telas com dados relacionados (livro + autor + outros livros) são montadas em uma única requisição, em vez de três. O schema tipado serve ao mesmo tempo de contrato, validação e documentação.

Isso não torna o REST obsoleto. Para endpoints simples, cacheáveis ou de upload, ele continua mais direto. Como dizem os slides, **não há vencedor absoluto**: GraphQL complementa REST e gRPC, e a escolha depende do cenário.
