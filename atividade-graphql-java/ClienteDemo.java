// Cliente de demonstração: envia cada query/mutation para POST /graphql e imprime a resposta.
// Uso (com o servidor rodando):  java ClienteDemo.java
// Usa só a biblioteca padrão do Java (java.net.http), sem dependências.

import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClienteDemo {

    static final String ENDPOINT = "http://localhost:8080/graphql";

    record Operacao(String titulo, String query, String variaveis) {
    }

    static final List<Operacao> OPERACOES = List.of(
            new Operacao("Query 1 - Query simples: listar livros pedindo só título e ano", """
                    query {
                      livros {
                        titulo
                        ano
                      }
                    }""", null),

            new Operacao("Query 2 - Mesma query, agora o cliente pede outros campos", """
                    query {
                      livros {
                        id
                        titulo
                        genero
                        nota
                        disponivel
                      }
                    }""", null),

            new Operacao("Query 3 - Argumento com variável: livros de um gênero", """
                    query LivrosPorGenero($genero: String) {
                      livros(genero: $genero) {
                        titulo
                        ano
                      }
                    }""", """
                    {"genero": "Romance"}"""),

            new Operacao("Query 4 - Relacionamento Livro -> Autor", """
                    query {
                      livro(id: "4") {
                        titulo
                        ano
                        autor {
                          nome
                          nacionalidade
                        }
                      }
                    }""", null),

            new Operacao("Query 5 - Relacionamento Autor -> [Livro]", """
                    query {
                      autor(id: "1") {
                        nome
                        livros {
                          titulo
                          ano
                        }
                      }
                    }""", null),

            new Operacao("Query 6 - Query complexa: livro + autor + outros livros do autor (1 chamada no lugar de 3 em REST)", """
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
                    }""", null),

            new Operacao("Mutation 1 - Adicionar um livro", """
                    mutation NovoLivro($input: NovoLivroInput!) {
                      adicionarLivro(input: $input) {
                        id
                        titulo
                        disponivel
                        autor {
                          nome
                        }
                      }
                    }""", """
                    {"input": {"titulo": "Laços de Família", "ano": 1960, "genero": "Contos", "autorId": "2"}}"""),

            new Operacao("Mutation 2 - Emprestar um livro", """
                    mutation {
                      emprestarLivro(id: "1") {
                        titulo
                        disponivel
                      }
                    }""", null),

            new Operacao("Query 7 - Conferindo o resultado das mutations", """
                    query {
                      autores {
                        nome
                        livros {
                          titulo
                          disponivel
                        }
                      }
                    }""", null),

            new Operacao("Erro - Mutation com autor inexistente (erro vem no campo \"errors\")", """
                    mutation {
                      adicionarLivro(input: {titulo: "Livro X", ano: 2024, genero: "Teste", autorId: "99"}) {
                        id
                      }
                    }""", null),

            new Operacao("Erro - Validação contra o Schema: campo que não existe", """
                    query {
                      livro(id: "1") {
                        titulo
                        editora
                      }
                    }""", null),

            new Operacao("Introspection - O cliente consulta o próprio Schema (auto-documentação)", """
                    query {
                      __type(name: "Livro") {
                        name
                        description
                        fields {
                          name
                          description
                        }
                      }
                    }""", null)
    );

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newHttpClient();
        String linha = "=".repeat(70);

        for (Operacao op : OPERACOES) {
            String body = "{\"query\": \"" + escaparJson(op.query()) + "\""
                    + (op.variaveis() != null ? ", \"variables\": " + op.variaveis() : "")
                    + "}";

            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            out.println(linha);
            out.println(op.titulo());
            out.println(linha);
            out.println("POST /graphql");
            out.println(op.query());
            if (op.variaveis() != null) {
                out.println("\nVariáveis: " + op.variaveis());
            }
            out.println("\nResposta (HTTP " + response.statusCode() + "):");
            out.println(formatarJson(response.body()));
            out.println();
        }
    }

    static String escaparJson(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // Indenta o JSON da resposta para ficar legível (sem biblioteca externa)
    static String formatarJson(String json) {
        StringBuilder sb = new StringBuilder();
        int nivel = 0;
        boolean dentroDeString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (dentroDeString) {
                sb.append(c);
                if (c == '\\') {
                    sb.append(json.charAt(++i));
                } else if (c == '"') {
                    dentroDeString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    dentroDeString = true;
                    sb.append(c);
                }
                case '{', '[' -> {
                    char proximo = json.charAt(i + 1);
                    if (proximo == '}' || proximo == ']') {
                        sb.append(c).append(proximo);
                        i++;
                    } else {
                        sb.append(c).append('\n').append("  ".repeat(++nivel));
                    }
                }
                case '}', ']' -> sb.append('\n').append("  ".repeat(--nivel)).append(c);
                case ',' -> sb.append(",\n").append("  ".repeat(nivel));
                case ':' -> sb.append(": ");
                default -> {
                    if (!Character.isWhitespace(c)) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
