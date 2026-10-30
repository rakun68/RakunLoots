package me.rakkoon69.rakunloots.text;

import java.util.Locale;

/**
 * The one seconds formatter shared by holograms and the wiki: {@code m:ss}, or {@code h:mm:ss} from an hour.
 */
public final class TimeText {

    private TimeText() {
    }

    public static String time(int seconds) {
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        int rest = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }
}
