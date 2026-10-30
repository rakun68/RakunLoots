package me.rakkoon69.rakunloots.text;

import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text into what a player sees: {@code <#rrggbb>} and {@code <#rgb>} become hex colors,
 * {@code &} color codes become legacy color codes, and chances format as percentages.
 */
public final class MessageText {

    private static final Pattern HEX = Pattern.compile("<#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})>");

    private MessageText() {
    }

    /**
     * Formats a percent value in [0, 1] into a string representation in {@code Locale.ROOT}.
     */
    public static String formatPercent(double chance, int decimals) {
        BigDecimal val = BigDecimal.valueOf(chance);
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal pct = val.multiply(hundred);

        BigDecimal rounded = pct.setScale(decimals, RoundingMode.HALF_UP);

        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal one = BigDecimal.ONE;

        if (val.compareTo(zero) > 0 && rounded.compareTo(zero) == 0) {
            if (decimals == 0) {
                return "<1";
            }
            return "<0." + "0".repeat(decimals - 1) + "1";
        }
        if (val.compareTo(one) < 0 && rounded.compareTo(hundred) >= 0) {
            if (decimals == 0) {
                return "99";
            }
            return "99." + "9".repeat(decimals);
        }
        return rounded.toPlainString();
    }

    /**
     * Text in legacy color codes. A hex color becomes {@code §x} followed by its six digits, each after {@code §},
     * which servers from 1.16 show as that color; a three digit hex color doubles each digit.
     */
    public static String color(String text) {
        Matcher matcher = HEX.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group(1);
            if (digits.length() == 3) {
                digits = "" + digits.charAt(0) + digits.charAt(0) + digits.charAt(1) + digits.charAt(1)
                        + digits.charAt(2) + digits.charAt(2);
            }
            StringBuilder hex = new StringBuilder("&x");
            for (char digit : digits.toCharArray()) {
                hex.append('&').append(digit);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(hex.toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }
}
