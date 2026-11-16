package br.com.lotolab.contests.application;

public class ContestNotFoundException extends RuntimeException {
    public ContestNotFoundException() { super("Nenhum concurso cadastrado"); }
    public ContestNotFoundException(int number) { super("Concurso " + number + " não encontrado"); }
}
