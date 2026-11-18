package com.freetoscape;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

final class ClanHighlights {
    static String format(JsonObject feed) {
        if (!feed.has("highlights") || !feed.get("highlights").isJsonArray())
            return "Clan highlights are not enabled on this feed yet.";
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement entry : feed.getAsJsonArray("highlights")) {
            if (!entry.isJsonObject()) continue;
            JsonObject row = entry.getAsJsonObject();
            try {
                Instant.ofEpochSecond(row.get("postedAt").getAsLong());
                if (row.get("poster").isJsonPrimitive() && row.get("summary").isJsonPrimitive()) rows.add(row);
            } catch (RuntimeException ignored) { }
        }
        rows.sort(Comparator.comparingLong((JsonObject row) -> row.get("postedAt").getAsLong()).reversed());
        StringBuilder result = new StringBuilder("Latest clan highlights\n\n");
        if (feed.has("highlightsStale") && feed.get("highlightsStale").isJsonPrimitive()
                && feed.getAsJsonPrimitive("highlightsStale").isBoolean() && feed.get("highlightsStale").getAsBoolean())
            result.append("Saved highlights — source connection pending or unavailable.\n\n");
        if (rows.isEmpty()) result.append("No achievement/drop posts available yet.");
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            JsonObject row = rows.get(i);
            result.append(i + 1).append(". Posted by ").append(text(row.get("poster"),64))
                .append("\n").append(text(row.get("summary"),240)).append("\n")
                .append(EventFormat.date(Instant.ofEpochSecond(row.get("postedAt").getAsLong()))).append("\n\n");
        }
        return result.toString();
    }
    private static String text(JsonElement value, int limit) {
        String result = value.getAsString().replaceAll("[\\r\\n\\t]+", " ");
        return result.substring(0, Math.min(limit, result.length()));
    }
}
