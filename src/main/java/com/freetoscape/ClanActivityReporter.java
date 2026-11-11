package com.freetoscape;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;

final class ClanActivityReporter {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private FreeToScapeConfig config;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    private volatile String status = "Activity reporting disabled.";
    private volatile boolean stopped = true;
    private volatile Call activeCall;
    private String collector;
    private long lastAttempt;
    private volatile long generation;

    void start() { generation++; collector = UUID.randomUUID().toString(); stopped = false; lastAttempt = 0; }
    void stop() { generation++; stopped = true; if (activeCall != null) activeCall.cancel(); status = "Activity reporting disabled."; }
    String status() { return status; }

    void poll() {
        if (stopped) return;
        if (!config.clanActivityReporting()) { status = "Activity reporting disabled. No clan member data is uploaded."; return; }
        if (System.currentTimeMillis() - lastAttempt < 60_000) return;
        lastAttempt = System.currentTimeMillis();
        long token = generation;
        String secret = System.getenv("CLAN_SYNC_REPORTER_TOKEN");
        if (secret == null || secret.length() < 32) { status = "Reporter credential missing. Use the officer-only reporter launcher."; return; }
        String base = config.clanSyncUrl().trim().replaceAll("/+$", "");
        try {
            FreeToScapePlugin.validateFeedUrl(base);
            JsonObject links = request(base + "/v1/clan/links", secret, null);
            if (stopped || token != generation || !config.clanActivityReporting()) return;
            JsonArray approved = links.getAsJsonArray("members");
            if (approved.size() > 500) throw new IOException("Too many linked members");
            String clan = links.get("clan").getAsString();
            CompletableFuture<JsonObject> pending = new CompletableFuture<>();
            clientThread.invokeLater(() -> {
                if (stopped || token != generation) { pending.cancel(false); return; }
                try { pending.complete(capture(client, approved, clan, collector)); }
                catch (RuntimeException error) { pending.completeExceptionally(error); }
            });
            JsonObject snapshot = pending.get(5, TimeUnit.SECONDS);
            if (stopped || token != generation || !config.clanActivityReporting()) return;
            JsonObject result = request(base + "/v1/clan/snapshot", secret, snapshot);
            if (stopped || token != generation) return;
            status = "Reporter connected\nLinked accounts processed: " + result.get("accepted").getAsInt()
                + "\nRole sync mode: " + result.get("mode").getAsString()
                + "\nActivity retained by bot: 7 days\nAbsent from channel does not prove globally offline.";
        } catch (Exception error) {
            if (!stopped && token == generation) status = "Activity reporter unavailable. Check the bot, server URL and private reporter credential. No role change was confirmed.";
        } finally { activeCall = null; }
    }

    private JsonObject request(String url, String secret, JsonObject payload) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).header("Authorization", "Bearer " + secret)
            .header("Cache-Control", "no-store").header("User-Agent", "FreeToScape-RuneLite");
        if (payload != null) builder.post(RequestBody.create(MediaType.parse("application/json"), payload.toString()));
        Call call = FreeToScapePlugin.privateHttpClient(http).newCall(builder.build());
        activeCall = call;
        if (stopped) { call.cancel(); throw new IOException("Reporter stopped"); }
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Reporter request rejected");
            byte[] bytes = response.body().byteStream().readNBytes(65537);
            if (bytes.length > 65536) throw new IOException("Reporter response too large");
            return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    static String clanKey(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    static JsonObject capture(Client client, JsonArray approved, String expectedClan, String collector) {
        JsonObject result = new JsonObject();
        result.addProperty("clan", expectedClan); result.addProperty("collector", collector);
        JsonArray members = new JsonArray(); result.add("members", members);
        ClanSettings settings = client.getClanSettings();
        ClanChannel channel = client.getClanChannel();
        boolean available = client.getGameState() == GameState.LOGGED_IN && settings != null && channel != null
            && clanKey(settings.getName()).equals(clanKey(expectedClan))
            && clanKey(channel.getName()).equals(clanKey(expectedClan));
        result.addProperty("available", available);
        if (!available) return result;
        for (JsonElement element : approved) {
            String name = element.getAsString();
            if (!name.replace('\u00a0',' ').matches("[A-Za-z0-9 _]{1,12}")) continue;
            ClanMember rosterMember = settings.findMember(name);
            JsonObject member = new JsonObject(); member.addProperty("name", name);
            if (rosterMember == null) {
                member.add("present", JsonNull.INSTANCE); member.add("rank", JsonNull.INSTANCE);
            } else {
                member.addProperty("present", channel.findMember(name) != null);
                ClanTitle title = settings.titleForRank(rosterMember.getRank());
                if (title == null) member.add("rank", JsonNull.INSTANCE); else member.addProperty("rank", title.getName());
            }
            members.add(member);
        }
        return result;
    }
}
