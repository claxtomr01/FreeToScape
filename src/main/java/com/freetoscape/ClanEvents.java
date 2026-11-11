package com.freetoscape;
import com.google.gson.*;
import java.time.Instant;
import java.util.*;

final class ClanEvents {
    static String status(JsonObject e, Instant now) {
        if (now.isBefore(Instant.parse(e.get("startsAt").getAsString()))) return "Upcoming";
        return now.isBefore(Instant.parse(e.get("endsAt").getAsString())) ? "Active" : "Completed";
    }
    static int priority(JsonObject e, Instant now) {
        String s = status(e, now);
        return s.equals("Active") ? 0 : s.equals("Upcoming") ? 1 : 2;
    }
    static List<JsonObject> ordered(JsonArray events, Instant now) {
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement e : events) rows.add(e.getAsJsonObject());
        rows.sort((a,b) -> {
            int p = Integer.compare(priority(a, now), priority(b, now));
            if (p != 0) return p;
            Instant sa = Instant.parse(a.get("startsAt").getAsString());
            Instant sb = Instant.parse(b.get("startsAt").getAsString());
            return priority(a, now) == 1 ? sa.compareTo(sb) : sb.compareTo(sa);
        });
        return rows;
    }
    static List<JsonObject> visible(JsonArray events, Instant now) {
        List<JsonObject> rows = new ArrayList<>(), completed = new ArrayList<>();
        for (JsonObject event : ordered(events, now)) {
            if (status(event, now).equals("Active")) rows.add(event);
            else if (status(event, now).equals("Completed")) completed.add(event);
        }
        completed.sort(Comparator.comparing((JsonObject e) -> Instant.parse(e.get("endsAt").getAsString()))
            .reversed().thenComparing(e -> Instant.parse(e.get("startsAt").getAsString()), Comparator.reverseOrder()));
        rows.addAll(completed.subList(0, Math.min(5, completed.size())));
        return rows;
    }
    static int select(JsonArray events, boolean boss, Instant now) {
        for (JsonObject e : ordered(events, now)) {
            String title = e.get("title").getAsString().toLowerCase(Locale.ROOT);
            if (boss ? title.contains("botw") || title.contains("boss of the week")
                    : title.contains("sotw") || title.contains("skill of the week"))
                return e.get("id").getAsInt();
        }
        return 0;
    }
    static String summary(JsonArray events, Instant now) {
        StringBuilder text = new StringBuilder("Active events\n\n");
        int count = 0;
        for (JsonObject event : ordered(events, now)) {
            if (!status(event, now).equals("Active")) continue;
            text.append(event.get("title").getAsString()).append("\n");
            count++;
        }
        if (count == 0) text.append("No active events.");
        return text.toString();
    }
}
