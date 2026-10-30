package me.rakkoon69.rakunloots.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeTextTest {

    @Test
    void formatsSecondsUnderHour() {
        assertEquals("0:00", TimeText.time(0));
        assertEquals("0:05", TimeText.time(5));
        assertEquals("0:59", TimeText.time(59));
        assertEquals("1:00", TimeText.time(60));
        assertEquals("1:05", TimeText.time(65));
        assertEquals("59:59", TimeText.time(3599));
    }

    @Test
    void formatsSecondsOverHour() {
        assertEquals("1:00:00", TimeText.time(3600));
        assertEquals("1:01:05", TimeText.time(3665));
        assertEquals("2:02:00", TimeText.time(7320));
    }
}
