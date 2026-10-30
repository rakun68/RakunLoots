package me.rakkoon69.rakunloots.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageTextTest {

    @Test
    void formatPercentCoversN0126AndSpecialValues() {
        // N = 0, 1, 2, 6
        // 0
        assertEquals("0", MessageText.formatPercent(0.0, 0));
        assertEquals("0.0", MessageText.formatPercent(0.0, 1));
        assertEquals("0.00", MessageText.formatPercent(0.0, 2));
        assertEquals("0.000000", MessageText.formatPercent(0.0, 6));

        // 1
        assertEquals("100", MessageText.formatPercent(1.0, 0));
        assertEquals("100.0", MessageText.formatPercent(1.0, 1));
        assertEquals("100.00", MessageText.formatPercent(1.0, 2));
        assertEquals("100.000000", MessageText.formatPercent(1.0, 6));

        // 0.00004 (<0.1 at N 1, <0.01 at N 2, <1 at N 0, 0.004000 at N 6)
        assertEquals("<1", MessageText.formatPercent(0.00004, 0));
        assertEquals("<0.1", MessageText.formatPercent(0.00004, 1));
        assertEquals("<0.01", MessageText.formatPercent(0.00004, 2));
        assertEquals("0.004000", MessageText.formatPercent(0.00004, 6));

        // 0.99996 (99.9 at N 1, never 100.0, 99 at N 0, 99.99 at N 2, 99.996000 at N 6)
        assertEquals("99", MessageText.formatPercent(0.99996, 0));
        assertEquals("99.9", MessageText.formatPercent(0.99996, 1));
        assertEquals("99.99", MessageText.formatPercent(0.99996, 2));
        assertEquals("99.996000", MessageText.formatPercent(0.99996, 6));

        // half-up rounding at 0.125 with N 2
        assertEquals("12.50", MessageText.formatPercent(0.125, 2));
        // half-up rounding at 0.125 with N 0
        assertEquals("13", MessageText.formatPercent(0.125, 0));
    }

    @Test
    void colorTranslatesLegacyAndHexColorCodes() {
        assertEquals("§aHello", MessageText.color("&aHello"));
        assertEquals("§x§1§2§3§4§5§6Text", MessageText.color("<#123456>Text"));
        assertEquals("§x§a§a§b§b§c§cText", MessageText.color("<#abc>Text"));
        assertEquals("Plain text", MessageText.color("Plain text"));
    }
}
