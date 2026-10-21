package br.edu.uniacademia.biblioteca.controller;

import br.edu.uniacademia.biblioteca.model.Autor;
import br.edu.uniacademia.biblioteca.model.Livro;
import br.edu.uniacademia.biblioteca.model.NovoLivroInput;
import br.edu.uniacademia.biblioteca.repository.BibliotecaRepository;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;

import java.util.List;

// Resolvers: cada método abaixo resolve um campo do schema.
//
// Correspondência com a assinatura vista em aula (parent, args, context, info):
//   parent -> o objeto pai, recebido como parâmetro (ex.: Livro em autor(Livro livro))
//   args   -> parâmetros anotados com @Argument
//   info   -> DataFetchingEnvironment (pode ser recebido como parâmetro quando necessário)
// Campos simples (titulo, ano, nome...) não precisam de método: são lidos direto dos records.
@Controller
public class BibliotecaController {

    private final BibliotecaRepository repository;

    public BibliotecaController(BibliotecaRepository repository) {
        this.repository = repository;
    }

    // ---------- Queries (resolvers raiz) ----------

    @QueryMapping
    public List<Livro> livros(@Argument String genero) {
        if (genero == null) {
            return repository.listarLivros();
        }
        return repository.listarLivros().stream()
                .filter(l -> l.genero().equalsIgnoreCase(genero))
                .toList();
    }

    @QueryMapping
    public Livro livro(@Argument String id) {
        return repository.buscarLivro(id).orElse(null);
    }

    @QueryMapping
    public List<Autor> autores() {
        return repository.listarAutores();
    }

    @QueryMapping
    public Autor autor(@Argument String id) {
        return repository.buscarAutor(id).orElse(null);
    }

    // ---------- Relacionamentos (resolvers de campo) ----------
    // Só executam se o cliente pedir o campo na query.

    @SchemaMapping(typeName = "Livro", field = "autor")
    public Autor autorDoLivro(Livro livro) {
        return repository.buscarAutor(livro.autorId()).orElseThrow();
    }

    @SchemaMapping(typeName = "Autor", field = "livros")
    public List<Livro> livrosDoAutor(Autor autor) {
        return repository.livrosDoAutor(autor.id());
    }

    // ---------- Mutations ----------

    @MutationMapping
    public Livro adicionarLivro(@Argument NovoLivroInput input) {
        if (repository.buscarAutor(input.autorId()).isEmpty()) {
            throw new RegraDeNegocioException("Autor com id " + input.autorId() + " não existe");
        }
        Livro novo = new Livro(repository.proximoIdLivro(), input.titulo(), input.ano(),
                input.genero(), 0.0, true, input.autorId());
        return repository.salvar(novo);
    }

    @MutationMapping
    public Livro emprestarLivro(@Argument String id) {
        Livro livro = repository.buscarLivro(id)
                .orElseThrow(() -> new RegraDeNegocioException("Livro com id " + id + " não existe"));
        if (!livro.disponivel()) {
            throw new RegraDeNegocioException("O livro '" + livro.titulo() + "' já está emprestado");
        }
        return repository.salvar(livro.emprestado());
    }

    // ---------- Erros ----------
    // Transforma a exceção num erro GraphQL, que vai para o campo "errors" da resposta.

    @GraphQlExceptionHandler
    public GraphQLError tratarRegraDeNegocio(RegraDeNegocioException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .errorType(ErrorType.BAD_REQUEST)
                .build();
    }
}
