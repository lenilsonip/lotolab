package br.com.lotolab.domain;

public record GameGenerationCriteria(int quantity, Integer evenNumbers, Integer oddNumbers, Long seed) {
    public static final int MAX_GAMES = 100;

    public GameGenerationCriteria {
        if (quantity < 1 || quantity > MAX_GAMES) {
            throw new IllegalArgumentException("A quantidade deve estar entre 1 e " + MAX_GAMES);
        }
        if ((evenNumbers == null) != (oddNumbers == null)) {
            throw new IllegalArgumentException("Informe pares e ímpares juntos");
        }
        if (evenNumbers != null && (evenNumbers < 0 || oddNumbers < 0
                || evenNumbers + oddNumbers != 15 || evenNumbers > 12 || oddNumbers > 13)) {
            throw new IllegalArgumentException("Os filtros devem somar 15 e respeitar a disponibilidade de dezenas");
        }
        long possible = evenNumbers == null ? 3_268_760L
                : combinations(12, evenNumbers) * combinations(13, oddNumbers);
        if (possible < quantity) {
            throw new IllegalArgumentException("Não existem combinações suficientes para a quantidade e os filtros");
        }
    }

    private static long combinations(int n, int k) {
        if (k < 0 || k > n) return 0;
        long result = 1;
        for (int i = 1; i <= Math.min(k, n - k); i++) result = result * (n - i + 1) / i;
        return result;
    }
}
