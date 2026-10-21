package br.edu.uniacademia.biblioteca.model;

// Guarda só o autorId (como uma chave estrangeira). O campo "autor" do schema
// é resolvido no controller a partir desse id.
public record Livro(String id, String titulo, int ano, String genero, double nota,
                    boolean disponivel, String autorId) {

    public Livro emprestado() {
        return new Livro(id, titulo, ano, genero, nota, false, autorId);
    }
}
