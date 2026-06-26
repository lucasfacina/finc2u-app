package br.com.finc2u.server.shared.vo;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public record MonthlyPeriod(int month, int year) {

    public MonthlyPeriod {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Mês deve estar entre 1 e 12.");
        }
    }

    public static MonthlyPeriod of(int month, int year) {
        return new MonthlyPeriod(month, year);
    }

    public LocalDate start() {
        return LocalDate.of(year, month, 1);
    }
    
    public LocalDate end() {
        return start().with(TemporalAdjusters.lastDayOfMonth());
    }

    public MonthlyPeriod previous() {
        return month == 1
                ? new MonthlyPeriod(12, year - 1)
                : new MonthlyPeriod(month - 1, year);
    }

}