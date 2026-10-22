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

    public SpreadsheetEntry markedPaid() {
        return new SpreadsheetEntry(paymentMonth, kind, card, description, value, installment, totalInstallments, true);
    }

}
