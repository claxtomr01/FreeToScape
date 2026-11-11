package com.freetoscape;
import org.junit.Test;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import static org.junit.Assert.assertEquals;
public class FeedUrlTest {
    @org.junit.Rule public org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();
    @Test public void networkClientDisablesPersistenceAndCredentialReuse() throws Exception {
        okhttp3.CookieJar jar = new okhttp3.CookieJar() {
            public void saveFromResponse(okhttp3.HttpUrl url, java.util.List<okhttp3.Cookie> cookies) {}
            public java.util.List<okhttp3.Cookie> loadForRequest(okhttp3.HttpUrl url) { return java.util.Collections.emptyList(); }
        };
        okhttp3.Cache inheritedCache = new okhttp3.Cache(temporary.newFolder("inherited-cache"), 1024 * 1024);
        okhttp3.OkHttpClient base = new okhttp3.OkHttpClient.Builder().cache(inheritedCache).cookieJar(jar).build();
        okhttp3.OkHttpClient client = FreeToScapePlugin.privateHttpClient(base);
        org.junit.Assert.assertNull(client.cache());
        org.junit.Assert.assertSame(okhttp3.CookieJar.NO_COOKIES, client.cookieJar());
        org.junit.Assert.assertSame(okhttp3.Authenticator.NONE, client.authenticator());
        org.junit.Assert.assertFalse(client.followRedirects());
        org.junit.Assert.assertFalse(client.followSslRedirects());
        inheritedCache.close();
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsSecretsInQueryStrings() {
        FreeToScapePlugin.validateFeedUrl("https://clan.example/v1/feed?token=secret");
    }
    @Test public void selectsNewestNamedCompetitionWithoutAssumingListOrder() {
        JsonArray rows = new Gson().fromJson("[{\"id\":1,\"title\":\"DataBase\",\"startsAt\":\"2026-12-01T00:00:00Z\"},{\"id\":2,\"title\":\"Runecrafting SOTW\",\"startsAt\":\"2026-11-02T12:00:00Z\"},{\"id\":3,\"title\":\"Boss of the Week\",\"startsAt\":\"2026-09-21T11:00:00Z\"},{\"id\":4,\"title\":\"Skill of the Week\",\"startsAt\":\"2026-08-01T00:00:00Z\"}]", JsonArray.class);
        for (com.google.gson.JsonElement row : rows) row.getAsJsonObject().addProperty("endsAt", "2027-01-01T00:00:00Z");
        assertEquals(2, ClanEvents.select(rows, false, java.time.Instant.parse("2026-11-10T00:00:00Z")));
        assertEquals(3, ClanEvents.select(rows, true, java.time.Instant.parse("2026-11-10T00:00:00Z")));
    }
    @Test public void acceptsTlsAndLoopback() {
        FreeToScapePlugin.validateFeedUrl("https://clan.example/v1/feed");
        FreeToScapePlugin.validateFeedUrl("http://127.0.0.1:8787/v1/feed");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsRemotePlaintext() {
        FreeToScapePlugin.validateFeedUrl("http://clan.example/v1/feed");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsCredentials() {
        FreeToScapePlugin.validateFeedUrl("https://secret@clan.example/v1/feed");
    }
}
