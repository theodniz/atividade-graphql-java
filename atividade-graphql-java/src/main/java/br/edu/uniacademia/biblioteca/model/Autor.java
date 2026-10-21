package br.edu.uniacademia.biblioteca.model;

// Os campos do record têm os mesmos nomes do type Autor no schema.
// O campo "livros" não fica aqui: ele é resolvido no controller (relacionamento).
public record Autor(String id, String nome, String nacionalidade) {
}
