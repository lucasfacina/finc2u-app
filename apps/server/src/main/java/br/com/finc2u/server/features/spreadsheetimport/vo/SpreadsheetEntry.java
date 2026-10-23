package br.com.finc2u.server.features.spreadsheetimport.vo;

import br.com.finc2u.server.features.spreadsheetimport.enums.EntryKind;

import java.math.BigDecimal;
import java.time.YearMonth;

/*
 * Uma linha da planilha já decodificada, sem nada do domínio do backend. `paymentMonth` é o mês em que o bloco é pago (o
 * bloco de janeiro é pago em fevereiro). `card` só vem nas linhas de cartão; `description` é nula nos itens do cartão e
 * nos gerais, porque a planilha não os descreve; `installment`/`totalInstallments` só vêm quando a linha é uma parcela
 * (n/m). `paid` é o "PG" da planilha: a fatura inteira, a conta ou o recebimento já quitado.
 */
public record SpreadsheetEntry(
    YearMonth paymentMonth,
    EntryKind kind,
    String card,
    String description,
    BigDecimal value,
    Integer installment,
    Integer totalInstallments,
    boolean paid
) {

    public static SpreadsheetEntry cardItem(YearMonth paymentMonth, String card, BigDecimal value, InstallmentMark installment) {
        return of(EntryKind.CARD_ITEM, paymentMonth, card, null, value, installment, false);
    }

    public static SpreadsheetEntry cardFixed(YearMonth paymentMonth, String card, String description, BigDecimal value) {
        return of(EntryKind.CARD_FIXED, paymentMonth, card, description, value, null, false);
    }

    public static SpreadsheetEntry cardGeneral(YearMonth paymentMonth, String card, BigDecimal value) {
        return of(EntryKind.CARD_GENERAL, paymentMonth, card, null, value, null, false);
    }

    public static SpreadsheetEntry bill(YearMonth paymentMonth, String description, BigDecimal value, boolean paid) {
        return of(EntryKind.BILL, paymentMonth, null, description, value, null, paid);
    }

    public static SpreadsheetEntry extra(YearMonth paymentMonth, String description, BigDecimal value, InstallmentMark installment, boolean paid) {
        return of(EntryKind.EXTRA, paymentMonth, null, description, value, installment, paid);
    }

    public static SpreadsheetEntry income(YearMonth paymentMonth, String description, BigDecimal value, boolean paid) {
        return of(EntryKind.INCOME, paymentMonth, null, description, value, null, paid);
    }

    private static SpreadsheetEntry of(EntryKind kind, YearMonth paymentMonth, String card, String description, BigDecimal value, InstallmentMark installment, boolean paid) {
        var number = installment == null
            ? null
            : installment.number();
        var total = installment == null
            ? null
            : installment.total();

        return new SpreadsheetEntry(paymentMonth, kind, card, description, value, number, total, paid);
    }

    public SpreadsheetEntry markedPaid() {
        return new SpreadsheetEntry(paymentMonth, kind, card, description, value, installment, totalInstallments, true);
    }

}
