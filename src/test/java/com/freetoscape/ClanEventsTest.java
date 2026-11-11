package com.freetoscape;
import com.google.gson.*;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanEventsTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private JsonObject event(int id, String title, String start, String end) {
        JsonObject e = new JsonObject();
        e.addProperty("id", id); e.addProperty("title", title); e.addProperty("metric", "yama");
        e.addProperty("startsAt", start); e.addProperty("endsAt", end); return e;
    }
    @Test public void startIsInclusiveAndEndIsExclusive() {
        JsonObject e = event(1,"test","2026-11-10T12:00:00Z","2026-11-11T12:00:00Z");
        assertEquals("Upcoming", ClanEvents.status(e, NOW.minusSeconds(1)));
        assertEquals("Active", ClanEvents.status(e, NOW));
        assertEquals("Completed", ClanEvents.status(e, NOW.plusSeconds(86400)));
    }
    @Test public void customActiveEventsAppearWithoutWeeklyTitles() {
        JsonArray events = new JsonArray();
        events.add(event(92566,"DataBase","2025-06-05T00:00:00Z","2026-12-31T20:00:00Z"));
        events.add(event(155448,"Pet Hunt - Yama","2026-09-12T18:00:00Z","2027-04-01T19:00:00Z"));
        String text = ClanEvents.summary(events, NOW);
        assertTrue(text.contains("DataBase")); assertTrue(text.contains("Pet Hunt - Yama"));
        assertEquals(0, ClanEvents.select(events, false, NOW));
        assertEquals(0, ClanEvents.select(events, true, NOW));
    }
    @Test public void activeWeeklyEventWinsOverFutureAndFinished() {
        JsonArray events = new JsonArray();
        events.add(event(1,"BOTW past","2026-10-01T00:00:00Z","2026-10-08T00:00:00Z"));
        events.add(event(2,"BOTW future","2026-11-20T00:00:00Z","2026-11-27T00:00:00Z"));
        events.add(event(3,"BOTW active","2026-11-09T00:00:00Z","2026-11-16T00:00:00Z"));
        assertEquals(3, ClanEvents.select(events, true, NOW));
        assertEquals(2, ClanEvents.select(events, true, Instant.parse("2026-11-17T00:00:00Z")));
        assertEquals(2, ClanEvents.select(events, true, Instant.parse("2026-12-01T00:00:00Z")));
    }
    @Test public void selectorLimitsHistoryToFiveAndKeepsActiveEventsFirst() {
        JsonArray events = new JsonArray();
        for (int i=1; i<=7; i++) events.add(event(i,"Past " + i,
            String.format("2026-10-%02dT00:00:00Z", i), "2026-10-20T00:00:00Z"));
        events.add(event(8,"Active", "2025-01-01T00:00:00Z", "2027-01-01T00:00:00Z"));
        java.util.List<JsonObject> shown = ClanEvents.visible(events, NOW);
        assertEquals(6, shown.size());
        assertEquals(8, shown.get(0).get("id").getAsInt());
        assertEquals(7, shown.get(1).get("id").getAsInt());
        assertEquals(3, shown.get(5).get("id").getAsInt());
        assertEquals(0, ClanEvents.visible(new JsonArray(), NOW).size());
    }
    @Test public void emptyGroupHasClearState() {
        assertTrue(ClanEvents.summary(new JsonArray(), NOW).contains("No active events."));
        assertEquals(0, ClanEvents.select(new JsonArray(), false, NOW));
    }
    @Test public void overviewOnlyShowsActiveNamesWhileFiveCompletedRemainSelectable() {
        JsonArray events = new JsonArray();
        for (int i=1; i<=7; i++) events.add(event(i,"Result " + i,
            String.format("2026-10-%02dT00:00:00Z", i), "2026-10-20T00:00:00Z"));
        events.add(event(8,"Current event", "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z"));
        String summary = ClanEvents.summary(events, NOW);
        assertTrue(summary.contains("Current event"));
        assertFalse(summary.contains("Recent results"));
        assertFalse(summary.contains("Result 7"));
        assertEquals(6, ClanEvents.visible(events, NOW).size());
    }
    @Test public void recentCompletedUsesEndDateAndDoesNotIncludeUpcomingEvents() {
        JsonArray events = new JsonArray();
        for (int i=1; i<=6; i++) events.add(event(i,"Old " + i,
            "2026-10-01T00:00:00Z", String.format("2026-10-%02dT00:00:00Z", i+1)));
        events.add(event(7,"Long-running recent finish", "2025-01-01T00:00:00Z", "2026-11-09T00:00:00Z"));
        events.add(event(8,"Future", "2026-12-01T00:00:00Z", "2026-12-10T00:00:00Z"));
        java.util.List<JsonObject> shown = ClanEvents.visible(events, NOW);
        assertEquals(5, shown.size());
        assertEquals(7, shown.get(0).get("id").getAsInt());
        assertTrue(shown.stream().noneMatch(e -> e.get("id").getAsInt() == 8));
    }
}
