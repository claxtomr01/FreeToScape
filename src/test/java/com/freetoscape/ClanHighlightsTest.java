package com.freetoscape;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanHighlightsTest {
    @Test public void sortsNewestFirstAndLimitsDisplayToTen() {
        JsonObject feed=new JsonObject(); JsonArray rows=new JsonArray(); feed.add("highlights",rows);
        for(int i=1;i<=12;i++) {
            JsonObject row=new JsonObject(); row.addProperty("poster","Player"+i);
            row.addProperty("summary","Drop "+i); row.addProperty("postedAt",i); rows.add(row);
        }
        String text=ClanHighlights.format(feed);
        assertTrue(text.indexOf("Player12")<text.indexOf("Player11"));
        assertTrue(text.contains("10. Posted by Player3"));
        assertFalse(text.contains("Posted by Player2\n"));
    }
    @Test public void olderFeedsShowSetupHelpWithoutBreakingOtherPanels() {
        assertTrue(ClanHighlights.format(new JsonObject()).contains("not enabled"));
    }
    @Test public void savedHighlightsAreLabelledWhenSourceUnavailable() {
        JsonObject feed=new JsonObject();feed.add("highlights",new JsonArray());feed.addProperty("highlightsStale",true);
        assertTrue(ClanHighlights.format(feed).contains("Saved highlights"));
    }
}
