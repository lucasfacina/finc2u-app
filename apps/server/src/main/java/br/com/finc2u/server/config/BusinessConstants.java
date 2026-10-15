package br.com.finc2u.server.config;

public class BusinessConstants {

    private BusinessConstants() {
    }

    // Número mínimo de parcelas para despesas do tipo PARCELED.
    public static final int MIN_INSTALLMENTS = 1;

    // Número máximo de parcelas (10 anos): limita o que a projeção e a materialização de parcelas precisam gerar.
    public static final int MAX_INSTALLMENTS = 120;

    // Índice padrão da parcela inicial quando não informado.
    public static final int DEFAULT_FIRST_INSTALLMENT = 1;

}
