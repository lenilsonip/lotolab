package br.com.lotolab.domain;

public record LearningModel(long id, int version, String status, String strategyKey,
                            int sampleSize, double meanHits, double varianceHits,
                            Integer firstContest, Integer lastContest) { }
