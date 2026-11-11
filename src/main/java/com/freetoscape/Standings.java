package com.freetoscape;

import com.google.gson.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

final class Standings {
    private static final class Row {
        final String name;
        final BigDecimal score;
        Row(String name, BigDecimal score) { this.name = name; this.score = score; }
    }

    private static BigDecimal score(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || value.getAsJsonPrimitive().isBoolean()) return null;
        try {
            BigDecimal result = new BigDecimal(value.getAsString());
            return result.precision() > 40 || Math.abs((long) result.scale()) > 12 ? null : result;
        } catch (NumberFormatException e) { return null; }
    }

    private static String number(BigDecimal value, String metric) {
        DecimalFormat format = new DecimalFormat("#,##0.############", DecimalFormatSymbols.getInstance(Locale.UK));
        if (EventFormat.unit(metric).equals("hours")) format.setMinimumFractionDigits(2);
        return format.format(value);
    }

    private static String amount(BigDecimal value, String metric) {
        return number(value, metric) + " " + EventFormat.unit(metric);
    }

    private static void sort(List<Row> rows) {
        rows.sort(Comparator.<Row, BigDecimal>comparing(r -> r.score).reversed()
            .thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER));
    }

    private static String names(List<Row> rows) {
        String text = rows.stream().limit(5).map(r -> r.name).collect(Collectors.joining(", "));
        return rows.size() > 5 ? text + " (+" + (rows.size() - 5) + " more)" : text;
    }

    static String competition(JsonArray participants, String metric, boolean completed) {
        List<Row> rows = new ArrayList<>();
        int unavailable = 0;
        for (JsonElement element : participants) {
            try {
                JsonObject participant = element.getAsJsonObject();
                BigDecimal value = score(participant.getAsJsonObject("progress").get("gained"));
                String name = participant.getAsJsonObject("player").get("displayName").getAsString();
                if (value == null || value.signum() < 0 || name.isBlank()) { unavailable++; continue; }
                rows.add(new Row(name, value));
            } catch (RuntimeException e) { unavailable++; }
        }
        sort(rows);
        StringBuilder text = new StringBuilder();
        if (rows.isEmpty()) {
            if (unavailable > 0) return "Results unavailable: participant scores are missing.\n";
            return "No participants yet.\n";
        }
        BigDecimal best = rows.get(0).score;
        List<Row> leaders = rows.stream().filter(r -> r.score.compareTo(best) == 0).collect(Collectors.toList());
        if (completed) {
            text.append("COMPLETED RESULTS\n");
            if (unavailable > 0) {
                text.append("Result incomplete — winner not confirmed.\nHighest recorded score: ")
                    .append(amount(best, metric)).append("\n");
            } else if (best.signum() == 0) {
                text.append("No recorded gains. No winner shown.\n");
            } else {
                text.append(leaders.size() == 1 ? "Winner: " : "Joint winners: ").append(names(leaders))
                    .append("\nFinal score: ").append(amount(best, metric)).append("\n");
                Optional<Row> runnerUp = rows.stream().filter(r -> r.score.compareTo(best) < 0).findFirst();
                if (runnerUp.isPresent()) text.append(leaders.size() == 1 ? "Winning margin: " : "Margin over next score: ")
                    .append(amount(best.subtract(runnerUp.get().score), metric)).append("\n");
            }
            text.append("\n");
        }
        text.append(completed && unavailable == 0 ? "Final standings\n\n" : "Available standings\n\n");
        int rank = 1, previousRank = 1;
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            Row row = rows.get(i);
            if (i > 0 && row.score.compareTo(rows.get(i - 1).score) != 0) rank = i + 1;
            text.append(rank).append(". ").append(row.name).append(" — ").append(amount(row.score, metric)).append("\n");
            if (i > 0) {
                BigDecimal gap = rows.get(i - 1).score.subtract(row.score);
                text.append(gap.signum() == 0 ? "   Tied with place " + rank :
                    "   " + amount(gap, metric) + " behind place " + previousRank).append("\n");
            }
            previousRank = rank;
        }
        if (unavailable > 0) text.append("\nUnavailable participant scores: ").append(unavailable)
            .append(". Ranks cover available scores only.\n");
        return text.toString();
    }

    static String teams(JsonArray teams, boolean stale) {
        List<Row> rows = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean incomplete = false;
        for (JsonElement element : teams) {
            try {
                JsonObject team = element.getAsJsonObject();
                String name = team.get("name").getAsString();
                BigDecimal value = score(team.get("score"));
                text.append(name).append(": ").append(value == null ? "Unavailable" : number(value, "points") + " points").append("\n");
                if (value == null || name.isBlank()) incomplete = true;
                else rows.add(new Row(name, value));
            } catch (RuntimeException e) { incomplete = true; }
        }
        text.append(stale ? "\nLast known comparison\n" : "\nTeam comparison\n");
        if (incomplete || rows.size() < 2) return text.append("Comparison unavailable — at least two complete team scores are needed.\n").toString();
        sort(rows);
        BigDecimal best = rows.get(0).score;
        List<Row> leaders = rows.stream().filter(r -> r.score.compareTo(best) == 0).collect(Collectors.toList());
        if (leaders.size() == rows.size()) text.append("Teams are tied at ").append(number(best, "points")).append(" points.\n");
        else if (leaders.size() > 1) text.append("Joint leaders: ").append(names(leaders)).append("\n")
            .append("Lead over next team: ").append(number(best.subtract(rows.get(leaders.size()).score), "points")).append(" points\n");
        else text.append(rows.get(0).name).append(" leads ").append(rows.get(1).name).append(" by ")
            .append(number(best.subtract(rows.get(1).score), "points")).append(" points.\n");
        return text.toString();
    }
}
