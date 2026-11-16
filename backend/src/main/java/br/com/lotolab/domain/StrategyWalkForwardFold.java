package br.com.lotolab.domain;

public record StrategyWalkForwardFold(int foldNumber, int trainingFirstContest, int trainingLastContest,
                                      int validationFirstContest, int validationLastContest,
                                      int trainingSampleSize, int validationSampleSize,
                                      long strategyScore, long randomBaselineScore,
                                      long randomSeed, int candidateBudget) { }
