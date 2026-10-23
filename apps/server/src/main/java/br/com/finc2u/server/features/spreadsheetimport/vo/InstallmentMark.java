package br.com.finc2u.server.features.spreadsheetimport.vo;

public record InstallmentMark(int number, int total) {

    public InstallmentMark {
        if (!isValid(number, total)) throw new IllegalArgumentException("A parcela deve estar entre 1 e o total.");
    }

    public static InstallmentMark of(int number, int total) {
        return new InstallmentMark(number, total);
    }

    public static boolean isValid(int number, int total) {
        return number >= 1 && number <= total;
    }

}
