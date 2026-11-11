package com.freetoscape;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class QualityOfLifeTest {
    @Test public void refreshCannotQueueDuplicatesOrBypassCooldown() {
        RefreshThrottle gate = new RefreshThrottle();
        assertTrue(gate.request(100_000));
        assertFalse(gate.request(100_000));
        gate.started(true);
        assertFalse(gate.request(200_000));
        gate.finished();
        assertFalse(gate.request(129_999));
        assertEquals(1, gate.secondsRemaining(129_999));
        assertTrue(gate.request(130_000));
        gate.started(false);
        gate.finished();
        assertTrue(gate.busy());
        assertFalse(gate.request(200_000));
        gate.started(true);
        gate.finished();
        assertTrue(gate.request(200_000));
    }
    @Test public void failedRequestKeepsLastSuccessfulTime() {
        ConnectionStatus state = new ConnectionStatus();
        assertTrue(state.text("Feed").contains("Not yet"));
        state.connected(Instant.parse("2026-11-10T12:00:00Z"));
        state.unavailable("Start the bot.");
        assertTrue(state.text("Feed").contains("Unavailable"));
        assertTrue(state.text("Feed").contains("10 Nov 2026, 12:00 GMT"));
        assertTrue(state.text("Feed").contains("Start the bot."));
        state.notConfigured();
        assertTrue(state.text("Feed").contains("Not yet"));
    }
    @Test public void metricNamesAndUnitsMatchData() {
        assertEquals("King Black Dragon", EventFormat.name("king_black_dragon"));
        assertEquals("1,234 kills", EventFormat.amount(1234, "yama"));
        assertEquals("1,234 XP", EventFormat.amount(1234, "overall"));
        assertEquals("12.50 hours", EventFormat.amount(12.5, "ehb"));
        assertEquals("4 completions", EventFormat.amount(4, "chambers_of_xeric"));
        assertEquals("8 chests", EventFormat.amount(8, "barrows_chests"));
        assertEquals("2,000 gained", EventFormat.amount(2000, "new_unknown_metric"));
    }
    @Test public void relativeTimesHandleStartAndEndBoundaries() {
        Instant start = Instant.parse("2026-11-10T12:00:00Z");
        Instant end = Instant.parse("2026-11-12T16:00:00Z");
        assertEquals("Starts in 1 hour", EventFormat.timing(start, end, start.minusSeconds(3600)));
        assertEquals("Ends in 2 days 4 hours", EventFormat.timing(start, end, start));
        assertEquals("Ended less than a minute ago", EventFormat.timing(start, end, end));
        assertEquals("Ended 1 day ago", EventFormat.timing(start, end, end.plusSeconds(86400)));
    }
    @Test public void datesShowCorrectUkSeasonalTimezone() {
        assertEquals("1 Apr 2027, 20:00 BST", EventFormat.date(Instant.parse("2027-04-01T19:00:00Z")));
        assertEquals("31 Dec 2026, 20:00 GMT", EventFormat.date(Instant.parse("2026-12-31T20:00:00Z")));
    }
    @Test public void localConnectionErrorGivesUsefulHelpWithoutAssumingBotIsStopped() {
        String help = FreeToScapePlugin.connectionHelp(new java.net.ConnectException("private host details"), true,
            "http://127.0.0.1:8787/v1/feed");
        assertTrue(help.contains("Start F2S Bounty Bot"));
        assertFalse(help.contains("private host details"));
    }
}
