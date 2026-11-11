package com.freetoscape;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StandingsTest {
    private JsonObject participant(String name, String gained) {
        JsonObject row = new JsonObject(), player = new JsonObject(), progress = new JsonObject();
        player.addProperty("displayName", name);
        if (gained == null) progress.add("gained", JsonNull.INSTANCE); else progress.addProperty("gained", gained);
        row.add("player", player); row.add("progress", progress);
        return row;
    }
    private JsonArray participants(String... pairs) {
        JsonArray rows = new JsonArray();
        for (int i=0; i<pairs.length; i+=2) rows.add(participant(pairs[i], pairs[i+1]));
        return rows;
    }
    private JsonArray teams(String one, String two) {
        JsonArray rows = new JsonArray();
        for (int i=0; i<2; i++) {
            JsonObject team = new JsonObject();
            team.addProperty("name", "Team " + (i+1));
            String score = i == 0 ? one : two;
            if (score == null) team.add("score", JsonNull.INSTANCE); else team.addProperty("score", score);
            rows.add(team);
        }
        return rows;
    }
    @Test public void completedEventShowsWinnerFinalScoreAndMargin() {
        String text = Standings.competition(participants("Bob","600","Alice","1000"), "runecrafting", true);
        assertTrue(text.contains("Winner: Alice"));
        assertTrue(text.contains("Final score: 1,000 XP"));
        assertTrue(text.contains("Winning margin: 400 XP"));
        assertTrue(text.contains("400 XP behind place 1"));
    }
    @Test public void equalScoresGetJointWinnersAndSharedRanks() {
        String text = Standings.competition(participants("Bob","100.0","Alice","100","Cara","80"), "yama", true);
        assertTrue(text.contains("Joint winners: Alice, Bob"));
        assertTrue(text.contains("1. Bob"));
        assertTrue(text.contains("3. Cara"));
        assertTrue(text.contains("Tied with place 1"));
        assertTrue(text.contains("20 kills behind place 1"));
    }
    @Test public void activeEventDoesNotDeclareACompletedWinner() {
        String text = Standings.competition(participants("Alice","100","Bob","90"), "yama", false);
        assertFalse(text.contains("Winner:"));
        assertFalse(text.contains("Final score:"));
        assertTrue(text.contains("10 kills behind place 1"));
    }
    @Test public void missingScoresCannotCreateAConfirmedWinner() {
        String text = Standings.competition(participants("Alice","100","Bob",null), "yama", true);
        assertTrue(text.contains("winner not confirmed"));
        assertFalse(text.contains("Winner: Alice"));
        assertFalse(text.contains("Final standings"));
        assertTrue(text.contains("Unavailable participant scores: 1"));
    }
    @Test public void zeroAndEmptyResultsDoNotInventWinners() {
        String zero = Standings.competition(participants("Alice","0","Bob","0"), "yama", true);
        assertTrue(zero.contains("No recorded gains. No winner shown."));
        assertFalse(zero.contains("Joint winners:"));
        assertEquals("No participants yet.\n", Standings.competition(new JsonArray(), "yama", true));
        assertTrue(Standings.competition(participants("Alice","NaN","Bob","-1"), "yama", true).contains("scores are missing"));
    }
    @Test public void winnerTiesIncludePlayersBeyondTheTopTen() {
        JsonArray rows = new JsonArray();
        for (int i=0; i<12; i++) rows.add(participant("Player" + i,"100"));
        String text = Standings.competition(rows,"yama",true);
        assertTrue(text.contains("Joint winners:"));
        assertTrue(text.contains("(+7 more)"));
    }
    @Test public void teamLeadUsesExactDecimalArithmetic() {
        String text = Standings.teams(teams("10.10","10.09"), false);
        assertTrue(text.contains("Team 1 leads Team 2 by 0.01 points."));
        assertFalse(text.contains("0.009999"));
    }
    @Test public void tiedTeamsAndStaleScoresAreExplicit() {
        assertTrue(Standings.teams(teams("0","0.00"),false).contains("Teams are tied at 0 points."));
        String text = Standings.teams(teams("20","30"),true);
        assertTrue(text.contains("Last known comparison"));
        assertTrue(text.contains("Team 2 leads Team 1 by 10 points."));
    }
    @Test public void incompleteTeamScoresDoNotTreatMissingValuesAsZero() {
        String text = Standings.teams(teams("50",null),false);
        assertTrue(text.contains("Team 2: Unavailable"));
        assertTrue(text.contains("Comparison unavailable"));
        assertFalse(text.contains("leads"));
        assertTrue(Standings.teams(teams("NaN","Infinity"),false).contains("Comparison unavailable"));
    }
}
