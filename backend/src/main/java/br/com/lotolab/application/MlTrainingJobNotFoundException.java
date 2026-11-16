package br.com.lotolab.application;

public class MlTrainingJobNotFoundException extends RuntimeException {
    public MlTrainingJobNotFoundException(long id) { super("Treinamento ML não encontrado: " + id); }
}
