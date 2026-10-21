package br.edu.uniacademia.biblioteca.repository;

import br.edu.uniacademia.biblioteca.model.Autor;
import br.edu.uniacademia.biblioteca.model.Livro;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// "Banco de dados" em memória da biblioteca.
// Num sistema real, aqui ficaria o acesso a PostgreSQL, MongoDB, outra API etc.
@Repository
public class BibliotecaRepository {

    private final List<Autor> autores = new ArrayList<>(List.of(
            new Autor("1", "Machado de Assis", "Brasileira"),
            new Autor("2", "Clarice Lispector", "Brasileira"),
            new Autor("3", "George Orwell", "Britânica")
    ));

    private final List<Livro> livros = new ArrayList<>(List.of(
            new Livro("1", "Dom Casmurro", 1899, "Romance", 4.7, true, "1"),
            new Livro("2", "Memórias Póstumas de Brás Cubas", 1881, "Romance", 4.6, true, "1"),
            new Livro("3", "A Hora da Estrela", 1977, "Romance", 4.4, false, "2"),
            new Livro("4", "1984", 1949, "Distopia", 4.8, true, "3"),
            new Livro("5", "A Revolução dos Bichos", 1945, "Fábula", 4.5, true, "3")
    ));

    public List<Autor> listarAutores() {
        return autores;
    }

    public Optional<Autor> buscarAutor(String id) {
        return autores.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public List<Livro> listarLivros() {
        return livros;
    }

    public Optional<Livro> buscarLivro(String id) {
        return livros.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    public List<Livro> livrosDoAutor(String autorId) {
        return livros.stream().filter(l -> l.autorId().equals(autorId)).toList();
    }

    public String proximoIdLivro() {
        return String.valueOf(livros.size() + 1);
    }

    public Livro salvar(Livro livro) {
        livros.removeIf(l -> l.id().equals(livro.id()));
        livros.add(livro);
        livros.sort((a, b) -> Integer.compare(Integer.parseInt(a.id()), Integer.parseInt(b.id())));
        return livro;
    }
}
