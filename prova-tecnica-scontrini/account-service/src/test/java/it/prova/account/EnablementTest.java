package it.prova.account;

import it.prova.account.enablement.Enablement;
import it.prova.account.enablement.EnablementStatus;
import it.prova.account.enablement.Product;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EnablementTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void isActiveOnlyInsideThePeriodWithBoundsIncluded() {
        Enablement enablement = new Enablement("org-1", Product.SCONTRINA);
        enablement.update(EnablementStatus.ENABLED, START, END, Instant.now());

        assertThat(enablement.isActiveOn(START.minusDays(1))).isFalse();
        assertThat(enablement.isActiveOn(START)).isTrue();
        assertThat(enablement.isActiveOn(END)).isTrue();
        assertThat(enablement.isActiveOn(END.plusDays(1))).isFalse();
    }

    @Test
    void withoutEndDateNeverExpires() {
        Enablement enablement = new Enablement("org-1", Product.SCONTRINA);
        enablement.update(EnablementStatus.ENABLED, START, null, Instant.now());

        assertThat(enablement.isActiveOn(LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    void disabledIsNeverActive() {
        Enablement enablement = new Enablement("org-1", Product.SCONTRINA);
        enablement.update(EnablementStatus.DISABLED, START, END, Instant.now());

        assertThat(enablement.isActiveOn(START)).isFalse();
    }
}
