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
    @Inject private ReporterCredential credential;
    private volatile String status = "Activity reporting disabled.";
    private volatile boolean stopped = true;
    private volatile Call activeCall;
    private String collector;
    private volatile long lastAttempt;
    private volatile long generation;

    void start() { generation++; collector = UUID.randomUUID().toString(); stopped = false; lastAttempt = 0; }
    void stop() { generation++; stopped = true; if (activeCall != null) activeCall.cancel(); credential.clear(); status = "Activity reporting disabled."; }
    String status() {
        String destination;
        boolean ready = false;
        try {
            destination = ReporterCredential.serverAddress(config.clanSyncUrl());
            ready = credential.configuredFor(destination);
        }
        catch (IllegalArgumentException error) { destination = "Not configured; open Officer setup."; }
        return status + "\n\nActivity destination:\n" + destination
            + "\nCredential: " + (ready ? "ready for this session" : "setup required")
            + "\n\nYour bot stores 7 days of observed activity. View it with !clanactivity DISCORD_USER_ID in the private bot-testing channel."
            + "\n\nWarden and higher ranks are excluded from rank sync. Channel absence does not prove globally offline."
            + "\n\nSetup credentials are kept only for this plugin session.";
    }

    void useCredential(String address, char[] value) {
        credential.use(address, value);
        credentialChanged();
        status = "Credential ready for this session. Uploads run only while Officer clan activity reporting is enabled in FreeToScape settings.";
    }

    void forgetCredential() {
        credential.clear();
        credentialChanged();
        status = "Session credential forgotten. Activity uploads require setup again.";
    }

    private void credentialChanged() {
        generation++;
        if (activeCall != null) activeCall.cancel();
        lastAttempt = 0;
    }

    void poll() {
        if (stopped) return;
        if (!config.clanActivityReporting()) { status = "Activity reporting disabled. No clan member data is uploaded."; return; }
        if (System.currentTimeMillis() - lastAttempt < 60_000) return;
        lastAttempt = System.currentTimeMillis();
        long token = generation;
        try {
            String base = ReporterCredential.serverAddress(config.clanSyncUrl());
            credential.importEnvironment(base, System.getenv("CLAN_SYNC_REPORTER_TOKEN"));
            String secret = credential.forServer(base);
            if (secret == null) { status = "Reporter credential missing for this server. Open Clan activity → Officer setup."; return; }
            JsonObject links = request(base + "/v1/clan/links", secret, null, token);
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
            if (!base.equals(ReporterCredential.serverAddress(config.clanSyncUrl()))) return;
            JsonObject result = request(base + "/v1/clan/snapshot", secret, snapshot, token);
            if (stopped || token != generation) return;
            status = "Reporter connected\nLinked accounts processed: " + result.get("accepted").getAsInt()
                + "\nRole sync mode: " + result.get("mode").getAsString()
                + "\nActivity retained by bot: 7 days\nAbsent from channel does not prove globally offline.";
        } catch (Exception error) {
            if (!stopped && token == generation) status = "Activity reporter unavailable. Check the bot, server URL and private reporter credential. No role change was confirmed.";
        } finally { activeCall = null; }
    }

    private JsonObject request(String url, String secret, JsonObject payload, long token) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).header("Authorization", "Bearer " + secret)
            .header("Cache-Control", "no-store").header("User-Agent", "FreeToScape-RuneLite");
        if (payload != null) builder.post(RequestBody.create(MediaType.parse("application/json"), payload.toString()));
        Call call = FreeToScapePlugin.privateHttpClient(http).newCall(builder.build());
        activeCall = call;
        if (stopped || token != generation) { call.cancel(); throw new IOException("Reporter stopped or credential changed"); }
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Reporter request rejected");
            byte[] bytes = response.body().byteStream().readNBytes(65537);
            if (bytes.length > 65536) throw new IOException("Reporter response too large");
            return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    static String clanKey(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    static int wardenCutoff(ClanSettings settings) {
        // Titles are clan-configurable: use Warden's actual numeric position,
        // rather than guessing which names or built-in ranks are above it.
        for (int value = 0; value <= ClanRank.JMOD.getRank(); value++) {
            ClanTitle title = settings.titleForRank(new ClanRank(value));
            if (title != null && clanKey(title.getName()).equals("warden")) return value;
        }
        // An unavailable boundary must never allow an automatic rank change.
        return -1;
    }

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
        int cutoff = wardenCutoff(settings);
        for (JsonElement element : approved) {
            String name = element.getAsString();
            if (!name.replace('\u00a0',' ').matches("[A-Za-z0-9 _]{1,12}")) continue;
            ClanMember rosterMember = settings.findMember(name);
            JsonObject member = new JsonObject(); member.addProperty("name", name);
            if (rosterMember == null) {
                member.add("present", JsonNull.INSTANCE); member.add("rank", JsonNull.INSTANCE);
            } else {
                member.addProperty("present", channel.findMember(name) != null);
                ClanRank rank = rosterMember.getRank();
                ClanTitle title = rank != null && rank.getRank() >= 0 && rank.getRank() < cutoff
                    ? settings.titleForRank(rank) : null;
                if (title == null) member.add("rank", JsonNull.INSTANCE); else member.addProperty("rank", title.getName());
            }
            members.add(member);
        }
        return result;
    }
}
