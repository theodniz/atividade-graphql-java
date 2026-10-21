package br.edu.uniacademia.biblioteca.model;

// Corresponde ao "input NovoLivroInput" do schema
public record NovoLivroInput(String titulo, int ano, String genero, String autorId) {
}
