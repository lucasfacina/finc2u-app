package br.com.finc2u.server.config;

public class BusinessConstants {

    private BusinessConstants() {
    }

    /*
     * Número mínimo de parcelas para despesas do tipo PARCELED.
     */
    public static final int MIN_INSTALLMENTS = 1;

    /*
     * Índice padrão da parcela inicial quando não informado.
     */
    public static final int DEFAULT_FIRST_INSTALLMENT = 1;

}
