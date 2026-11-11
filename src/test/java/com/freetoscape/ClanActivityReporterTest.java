package com.freetoscape;
import com.google.gson.*;
import java.lang.reflect.Proxy;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanActivityReporterTest {
    @SuppressWarnings("unchecked") private <T> T proxy(Class<T> type, java.util.function.BiFunction<String,Object[],Object> call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->call.apply(method.getName(),args));
    }
    private Client client(GameState state, String clan) {
        return client(state, clan, 10, "Topaz", true);
    }
    private Client client(GameState state, String clan, int rank, String title, boolean hasWarden) {
        ClanMember member=proxy(ClanMember.class,(method,args)-> {
            if (method.equals("getRank")) return new ClanRank(rank);
            throw new AssertionError("Unexpected member data read: "+method);
        });
        ClanSettings settings=proxy(ClanSettings.class,(method,args)-> {
            if (method.equals("getName")) return clan;
            if (method.equals("findMember")) return args[0].equals("Alice")?member:null;
            if (method.equals("titleForRank")) {
                int value = ((ClanRank)args[0]).getRank();
                if (hasWarden && value == 100) return new ClanTitle(2, "Warden");
                return value == rank ? new ClanTitle(1, title) : null;
            }
            throw new AssertionError("Unexpected roster data read: "+method);
        });
        ClanChannelMember online=proxy(ClanChannelMember.class,(method,args)-> {throw new AssertionError("World or extra online data must not be read");});
        ClanChannel channel=proxy(ClanChannel.class,(method,args)-> {
            if (method.equals("getName")) return clan;
            if (method.equals("findMember")) return args[0].equals("Alice")?online:null;
            throw new AssertionError("Unexpected channel data read: "+method);
        });
        return proxy(Client.class,(method,args)-> {
            if (method.equals("getGameState")) return state;
            if (method.equals("getClanSettings")) return settings;
            if (method.equals("getClanChannel")) return channel;
            throw new AssertionError("Unexpected client data read: "+method);
        });
    }
    @Test public void reportsOnlyApprovedNamesRanksAndPresence() {
        JsonArray names=new JsonArray(); names.add("Alice"); names.add("Cara");
        JsonObject data=ClanActivityReporter.capture(client(GameState.LOGGED_IN,"FreeToScape"),names,"FreeToScape","collector-1234567890");
        assertTrue(data.get("available").getAsBoolean());
        assertEquals(2,data.getAsJsonArray("members").size());
        JsonObject alice=data.getAsJsonArray("members").get(0).getAsJsonObject();
        assertEquals(3,alice.size());
        assertTrue(alice.get("present").getAsBoolean());
        assertEquals("Topaz",alice.get("rank").getAsString());
        assertTrue(data.getAsJsonArray("members").get(1).getAsJsonObject().get("present").isJsonNull());
    }
    @Test public void loggedOutOrWrongClanReportsUnknownInsteadOfOffline() {
        JsonArray names=new JsonArray(); names.add("Alice");
        for (Client client:new Client[]{client(GameState.LOGIN_SCREEN,"FreeToScape"),client(GameState.LOGGED_IN,"OtherClan")}) {
            JsonObject data=ClanActivityReporter.capture(client,names,"FreeToScape","collector-1234567890");
            assertFalse(data.get("available").getAsBoolean());
            assertEquals(0,data.getAsJsonArray("members").size());
        }
    }
    @Test public void activityUploadsAreOffByDefault() {
        assertFalse(new FreeToScapeConfig(){}.clanActivityReporting());
    }
    @Test public void wardenAndAllHigherRanksAreWithheldButPresenceRemains() {
        JsonArray names = new JsonArray(); names.add("Alice");
        for (int rank : new int[]{100, 110, 125, 126, 127}) {
            JsonObject data = ClanActivityReporter.capture(client(GameState.LOGGED_IN,"FreeToScape",rank,"Custom staff title",true),names,"FreeToScape","collector-1234567890");
            JsonObject member = data.getAsJsonArray("members").get(0).getAsJsonObject();
            assertTrue(member.get("present").getAsBoolean());
            assertTrue("Excluded rank " + rank, member.get("rank").isJsonNull());
        }
    }
    @Test public void rankImmediatelyBelowWardenRemainsEligible() {
        JsonArray names = new JsonArray(); names.add("Alice");
        JsonObject data = ClanActivityReporter.capture(client(GameState.LOGGED_IN,"FreeToScape",99,"Veteran",true),names,"FreeToScape","collector-1234567890");
        assertEquals("Veteran",data.getAsJsonArray("members").get(0).getAsJsonObject().get("rank").getAsString());
    }
    @Test public void missingWardenBoundaryWithholdsRankInsteadOfGuessing() {
        JsonArray names = new JsonArray(); names.add("Alice");
        JsonObject data = ClanActivityReporter.capture(client(GameState.LOGGED_IN,"FreeToScape",10,"Topaz",false),names,"FreeToScape","collector-1234567890");
        JsonObject member = data.getAsJsonArray("members").get(0).getAsJsonObject();
        assertTrue(member.get("present").getAsBoolean());
        assertTrue(member.get("rank").isJsonNull());
    }
}
