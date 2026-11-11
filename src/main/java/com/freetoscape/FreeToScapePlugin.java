package com.freetoscape;

import com.google.gson.*;
import com.google.inject.Provides;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.ImageUtil;
import okhttp3.*;

@PluginDescriptor(name="FreeToScape", description="Clan competitions and Slaughterhouse bounty notices")
public class FreeToScapePlugin extends Plugin {
    @Inject private FreeToScapeConfig config;
    @Inject private ClientToolbar toolbar;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    @Inject private BountyPasswordOverlay passwordOverlay;
    @Inject private ClanActivityReporter activityReporter;
    @Inject private ConfigManager configManager;
    private ScheduledExecutorService worker;
    private NavigationButton navigation;
    private JTextArea home, slaughterhouse, eventOverview;
    private JTextArea activityStatus;
    private LeaderboardPane sotw, botw, eventDetails;
    private JComboBox<EventChoice> eventPicker;
    private volatile int selectedEvent;
    private boolean updatingPicker;
    private int knownGroup;
    private JsonArray discoveredEvents;
    private boolean discoveryFailed;
    private volatile long session;
    private String feedIdentity = "";
    private final Set<String> seen = new LinkedHashSet<>();
    private boolean baseline;
    private long lastCompetitions;
    private RefreshThrottle refreshThrottle;
    private JButton refreshButton;
    private javax.swing.Timer refreshTimer;
    private JTextArea eventConnection, feedConnection;
    private ConnectionStatus womStatus, feedStatus;
    private String statusFeedUrl;

    @Provides FreeToScapeConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(FreeToScapeConfig.class);
    }

    @Override protected void startUp() {
        long token = ++session;
        passwordOverlay.clear();
        activityReporter.start();
        overlayManager.add(passwordOverlay);
        refreshThrottle = new RefreshThrottle();
        womStatus = new ConnectionStatus();
        feedStatus = new ConnectionStatus();
        statusFeedUrl = "";
        lastCompetitions = 0;
        discoveredEvents = null;
        selectedEvent = 0;
        knownGroup = config.groupId();
        discoveryFailed = false;
        feedIdentity = "";
        baseline = false;
        seen.clear();
        PluginPanel panel = new PluginPanel() {};
        panel.setLayout(new BorderLayout(0, 8));
        JLabel title = new JLabel("FreeToScape", SwingConstants.CENTER);
        title.setIcon(new ImageIcon(ImageUtil.loadImageResource(FreeToScapePlugin.class, "logo.png")));
        title.setHorizontalTextPosition(SwingConstants.CENTER);
        title.setVerticalTextPosition(SwingConstants.BOTTOM);
        title.setForeground(new Color(218, 182, 97));
        panel.add(title, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        home = page(tabs, "Home"); sotw = leaderboardPage(tabs, "SOTW");
        botw = leaderboardPage(tabs, "BOTW");
        eventConnection = statusArea(); feedConnection = statusArea();
        slaughterhouse = pageWithStatus(tabs, "Slaughterhouse", feedConnection);
        home.setText("Welcome to FreeToScape!\n\nYour clan companion for RuneLite.\n\nEvents\nDiscover active and upcoming clan competitions and view their standings.\n\nSOTW & BOTW\nFollow Skill of the Week XP gains and Boss of the Week kill counts.\n\nUse the buttons below to visit Wise Old Man or join the clan Discord.");
        JPanel eventsPanel = new JPanel(new BorderLayout(0, 6));
        eventPicker = new JComboBox<>();
        eventPicker.setPreferredSize(new Dimension(200, 28));
        eventOverview = new JTextArea(4, 19);
        eventDetails = new LeaderboardPane();
        for (JTextArea area : Arrays.asList(eventOverview)) {
            area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
            area.setMargin(new Insets(10, 8, 10, 8));
        }
        eventOverview.setText("Discovering clan events...");
        JPanel eventBody = new JPanel(new BorderLayout(0, 6));
        eventBody.add(new JScrollPane(eventOverview), BorderLayout.NORTH);
        eventBody.add(new JScrollPane(eventDetails), BorderLayout.CENTER);
        eventsPanel.add(eventPicker, BorderLayout.NORTH); eventsPanel.add(eventBody, BorderLayout.CENTER);
        eventsPanel.add(eventConnection, BorderLayout.SOUTH);
        tabs.insertTab("Events", null, eventsPanel, "All clan competitions", 1);
        eventPicker.addActionListener(e -> {
            if (updatingPicker || worker == null || worker.isShutdown()) return;
            EventChoice choice = (EventChoice) eventPicker.getSelectedItem();
            if (choice != null) {
                selectedEvent = choice.id;
                worker.execute(() -> competition(eventDetails, choice.id, null, token));
            }
        });
        JTextArea privacy = page(tabs, "Privacy");
        privacy.setText("FreeToScape is an unofficial clan plugin. It is not provided, supported or moderated by Jagex.\n\nThe plugin reads public Wise Old Man competitions and your configured clan feed. These services can see your connecting IP address.\n\nPublic leaderboard names and scores are held temporarily to display standings. No player names, account credentials, chat history or IP addresses are written to files by this plugin.\n\nThe plugin saves only its settings through RuneLite. Requests disable disk caching and cookies. It has no analytics. Optional activity reporting remains off unless an authorised officer enables it.\n\nRuneLite itself, Wise Old Man, Discord, the existing bot and any feed host have separate data practices.\n\nBounty passwords are event codes, never RuneScape account passwords. Never put account credentials into plugin settings.");
        privacy.append("\n\nOptional officer activity reporting is off by default. If enabled, it uploads only officer-linked RuneScape names, clan ranks and observed clan-channel presence to the configured bot. The bot retains activity for seven days; account links remain until an officer unlinks them. No worlds, locations or game chat are sent. The earlier no-upload description applies while this option is disabled.");
        activityStatus = new JTextArea(18, 19);
        activityStatus.setEditable(false);
        activityStatus.setLineWrap(true); activityStatus.setWrapStyleWord(true);
        activityStatus.setMargin(new Insets(10, 8, 10, 8));
        activityStatus.setText(activityReporter.status());
        JPanel activityPanel = new JPanel(new BorderLayout(0, 6));
        activityPanel.add(new JScrollPane(activityStatus), BorderLayout.CENTER);
        JPanel officerButtons = new JPanel(new GridLayout(0, 1, 0, 4));
        JButton setup = new JButton("Officer setup");
        setup.addActionListener(e -> officerSetup(panel));
        JButton forget = new JButton("Forget session credential");
        forget.addActionListener(e -> {
            activityReporter.forgetCredential();
            activityStatus.setText(activityReporter.status());
        });
        officerButtons.add(setup); officerButtons.add(forget);
        activityPanel.add(officerButtons, BorderLayout.SOUTH);
        tabs.addTab("Clan activity", activityPanel);
        privacy.append("\n\nOfficer setup accepts a dedicated reporter credential in a masked field. It is held only in process memory, bound to the selected receiver, and cleared on plugin shutdown or Forget session credential. It is not saved in RuneLite configuration. Changing receiver requires setting up its credential again. The officer launcher remains an optional environment-variable fallback.");
        panel.add(tabs, BorderLayout.CENTER);
        JPanel links = new JPanel(new GridLayout(0, 1, 0, 4));
        JButton wom = new JButton("Wise Old Man");
        wom.addActionListener(e -> LinkBrowser.browse("https://wiseoldman.net/groups/" + config.groupId()));
        JButton discord = new JButton("Discord");
        discord.addActionListener(e -> LinkBrowser.browse("https://discord.gg/ZBQZDtg2H"));
        refreshButton = new JButton("Refresh now");
        refreshButton.setToolTipText("Refresh events, standings and the clan feed. 30-second cooldown.");
        refreshButton.addActionListener(e -> {
            if (worker == null || worker.isShutdown() || session != token
                    || !refreshThrottle.request(System.currentTimeMillis())) return;
            updateRefreshButton(token);
            worker.execute(() -> refresh(token, true));
        });
        links.add(refreshButton); links.add(wom); links.add(discord); panel.add(links, BorderLayout.SOUTH);
        navigation = NavigationButton.builder().tooltip("FreeToScape")
            .icon(ImageUtil.loadImageResource(FreeToScapePlugin.class, "icon.png"))
            .panel(panel).priority(7).build();
        toolbar.addNavigation(navigation);
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "freetoscape-refresh"); t.setDaemon(true); return t;
        });
        refreshTimer = new javax.swing.Timer(1000, e -> updateRefreshButton(token));
        refreshTimer.start();
        worker.scheduleWithFixedDelay(() -> refresh(token, false), 0, 15, TimeUnit.SECONDS);
    }

    private JTextArea statusArea() {
        JTextArea area = new JTextArea(7, 19);
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setMargin(new Insets(6, 8, 6, 8));
        area.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(80, 80, 80)));
        return area;
    }

    private void officerSetup(Component parent) {
        JTextField server = new JTextField(config.clanSyncUrl().isBlank()
            ? "http://127.0.0.1:8788" : config.clanSyncUrl(), 22);
        JPasswordField secret = new JPasswordField(22);
        JPanel form = new JPanel(new BorderLayout(0, 12));
        JLabel guide = new JLabel("<html><body style='width: 310px'>Authorised officers only. When reporting is enabled, linked RuneScape names, ranks below Warden and observed clan-channel presence are sent to this bot.<br><br>The bot retains activity for 7 days. Use HTTPS for a remote bot; localhost works only on the bot PC.<br><br>Use the dedicated reporter credential from your bot operator. It lasts until the plugin closes. Enable activity reporting separately in FreeToScape settings.</body></html>");
        form.add(guide, BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 6));
        fields.add(new JLabel("Clan sync server URL")); fields.add(server);
        fields.add(new JLabel("Reporter credential (this session only)")); fields.add(secret);
        form.add(fields, BorderLayout.CENTER);
        int choice = JOptionPane.showOptionDialog(parent, form, "FreeToScape officer setup",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            new String[]{"Use for this session", "Cancel"}, "Use for this session");
        char[] entered = secret.getPassword();
        try {
            if (choice != 0) return;
            String address = ReporterCredential.serverAddress(server.getText());
            activityReporter.useCredential(address, entered);
            configManager.setConfiguration("freetoscape", "clanSyncUrl", address);
            activityStatus.setText(activityReporter.status());
        } catch (IllegalArgumentException error) {
            JOptionPane.showMessageDialog(parent, "Check the server URL (HTTPS or local loopback HTTP) and the dedicated 32–256 character reporter credential.",
                "Officer setup incomplete", JOptionPane.WARNING_MESSAGE);
        } finally {
            Arrays.fill(entered, '\0');
            secret.setText("");
        }
    }

    private JTextArea pageWithStatus(JTabbedPane tabs, String name, JTextArea status) {
        JTextArea area = new JTextArea(22, 19);
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setMargin(new Insets(10, 8, 10, 8));
        JPanel container = new JPanel(new BorderLayout(0, 6));
        container.add(new JScrollPane(area), BorderLayout.CENTER);
        container.add(status, BorderLayout.SOUTH);
        tabs.addTab(name, container);
        return area;
    }

    private void updateRefreshButton(long token) {
        if (session != token) return;
        long seconds = refreshThrottle.secondsRemaining(System.currentTimeMillis());
        boolean busy = refreshThrottle.busy();
        refreshButton.setEnabled(!busy && seconds == 0);
        refreshButton.setText(busy ? "Refreshing..." : seconds > 0 ? "Refresh in " + seconds + "s" : "Refresh now");
    }

    private JTextArea page(JTabbedPane tabs, String name) {
        JTextArea area = new JTextArea(22, 19);
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setMargin(new Insets(10, 8, 10, 8));
        tabs.addTab(name, new JScrollPane(area)); return area;
    }

    private LeaderboardPane leaderboardPage(JTabbedPane tabs, String name) {
        LeaderboardPane pane = new LeaderboardPane();
        tabs.addTab(name, new JScrollPane(pane));
        return pane;
    }

    @Override protected void shutDown() {
        ++session;
        if (refreshTimer != null) refreshTimer.stop();
        overlayManager.remove(passwordOverlay);
        passwordOverlay.clear();
        activityReporter.stop();
        if (worker != null) worker.shutdownNow();
        if (navigation != null) toolbar.removeNavigation(navigation);
        discoveredEvents = null;
        seen.clear();
        feedIdentity = "";
        selectedEvent = 0;
        JTextComponent[] oldPages = {home, sotw, botw, slaughterhouse, eventOverview, eventDetails, eventConnection, feedConnection, activityStatus};
        JComboBox<EventChoice> oldPicker = eventPicker;
        SwingUtilities.invokeLater(() -> {
            for (JTextComponent area : oldPages) if (area != null) area.setText("");
            if (oldPicker != null) oldPicker.removeAllItems();
        });
    }

    private JsonElement getJson(String url) throws IOException {
        OkHttpClient timed = privateHttpClient(http);
        try (Response response = timed.newCall(new Request.Builder().url(url)
                .header("User-Agent", "FreeToScape-RuneLite")
                .header("Cache-Control", "no-store").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new HttpFailure(response.code());
            if (response.body().contentLength() > 2_000_000) throw new IOException("Response too large");
            byte[] bytes = response.body().byteStream().readNBytes(2_000_001);
            if (bytes.length > 2_000_000) throw new IOException("Response too large");
            return gson.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), JsonElement.class);
        }
    }

    private JsonObject get(String url) throws IOException { return getJson(url).getAsJsonObject(); }

    static OkHttpClient privateHttpClient(OkHttpClient base) {
        return base.newBuilder().cache(null).cookieJar(CookieJar.NO_COOKIES)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .followRedirects(false).followSslRedirects(false)
            .callTimeout(20, TimeUnit.SECONDS).build();
    }

    private void show(JTextComponent area, String text, long token) {
        SwingUtilities.invokeLater(() -> { if (session == token) area.setText(text); });
    }

    private void refresh(long token, boolean force) {
        if (session != token) return;
        RefreshThrottle gate = refreshThrottle;
        gate.started(force);
        SwingUtilities.invokeLater(() -> updateRefreshButton(token));
        try {
            refreshData(token, force);
            if (session == token) { activityReporter.poll(); show(activityStatus, activityReporter.status(), token); }
        }
        finally {
            gate.finished();
            if (session == token) {
                String womText = womStatus.text("Wise Old Man"), feedText = feedStatus.text("Clan feed");
                show(eventConnection, womText, token);
                show(feedConnection, feedText, token);
                SwingUtilities.invokeLater(() -> updateRefreshButton(token));
            }
        }
    }

    private void refreshData(long token, boolean force) {
        if (knownGroup != config.groupId()) {
            knownGroup = config.groupId(); discoveredEvents = null; lastCompetitions = 0;
            selectedEvent = 0; womStatus.reset();
        }
        if (force || System.currentTimeMillis() - lastCompetitions >= 420_000) {
            lastCompetitions = System.currentTimeMillis();
            int skillId = config.sotwId(), bossId = config.botwId();
            try {
                JsonArray events = getJson("https://api.wiseoldman.net/v2/groups/" + config.groupId() + "/competitions").getAsJsonArray();
                if (session != token) return;
                discoveredEvents = events;
                discoveryFailed = false;
                womStatus.connected(Instant.now());
                if (skillId == 0) skillId = latest(events, false);
                if (bossId == 0) bossId = latest(events, true);
                updateEventPicker(token);
                boolean complete = competition(sotw, skillId, false, token);
                complete = competition(botw, bossId, true, token) && complete;
                if (selectedEvent > 0) complete = competition(eventDetails, selectedEvent, null, token) && complete;
                if (!complete) womStatus.warning("Some standings unavailable. Check the event IDs or retry later.");
            } catch (Exception e) {
                if (session != token) return;
                discoveryFailed = true;
                womStatus.unavailable(connectionHelp(e, false, ""));
                stale(sotw, "Competition discovery unavailable.", token);
                stale(botw, "Competition discovery unavailable.", token);
            }
        }
        if (discoveredEvents != null) {
            show(eventOverview, ClanEvents.summary(discoveredEvents, Instant.now())
                + (discoveryFailed ? "\nSTALE: discovery unavailable; last list retained." : ""), token);
        } else if (discoveryFailed) show(eventOverview, "Events unavailable. Retrying automatically.", token);
        String url = config.feedUrl().trim();
        if (!url.equals(statusFeedUrl)) { statusFeedUrl = url; feedStatus.reset(); }
        if (url.isEmpty()) { feedStatus.notConfigured(); passwordOverlay.clear(); show(slaughterhouse, "Clan feed is not configured yet.", token); return; }
        try {
            validateFeedUrl(url);
            JsonObject feed = get(url);
            if (session != token) return;
            passwordOverlay.update(feed);
            StringBuilder scores = new StringBuilder("Slaughterhouse\n\n");
            if (feed.has("teams") && feed.get("teams").isJsonArray()) {
                scores.append(Standings.teams(feed.getAsJsonArray("teams"), feed.get("scoresStale").getAsBoolean()));
                if (feed.has("scoresUpdatedAt") && !feed.get("scoresUpdatedAt").isJsonNull())
                    scores.append("\nScores updated ").append(EventFormat.date(Instant.ofEpochSecond(feed.get("scoresUpdatedAt").getAsLong())));
            } else scores.append("Scores not yet available.");
            if (feed.get("scoresStale").getAsBoolean()) scores.append("\nSTALE: score refresh unavailable.");
            show(slaughterhouse, scores.toString(), token);
            JsonArray events = feed.getAsJsonArray("events");
            if (!url.equals(feedIdentity)) { feedIdentity = url; baseline = false; seen.clear(); }
            List<String> notices = new ArrayList<>();
            for (JsonElement element : events) {
                String id = element.getAsJsonObject().get("id").getAsString();
                if (id.matches("[0-9]{1,24}") && seen.add(id) && baseline) notices.add(id);
            }
            feedStatus.connected(Instant.now());
            if (feed.get("scoresStale").getAsBoolean()) feedStatus.warning("Feed connected; scores are stale. Check the bot's Google Sheets access.");
            baseline = true;
            while (seen.size() > 500) seen.remove(seen.iterator().next());
            // Consume while logged out/disabled; never flood chat with missed history.
            if (config.broadcasts() && !notices.isEmpty()) clientThread.invokeLater(() -> {
                if (session == token && client.getGameState() == GameState.LOGGED_IN && config.broadcasts())
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>[Clan Broadcast]</col> A new bounty has been posted!", "");
            });
        } catch (Exception e) {
            if (session != token) return;
            passwordOverlay.clear();
            String help = connectionHelp(e, true, url);
            feedStatus.unavailable(help);
            stale(slaughterhouse, help, token);
        }
    }

    private static final class HttpFailure extends IOException {
        final int code;
        HttpFailure(int code) { this.code = code; }
    }

    static String connectionHelp(Exception error, boolean feed, String url) {
        if (error instanceof HttpFailure) {
            int code = ((HttpFailure) error).code;
            if (code == 429) return "Service rate limit reached. Wait before refreshing again.";
            if (code == 401 || code == 403) return "Access denied. Check the service's access settings.";
            if (code == 404) return feed ? "Feed not found. Use the URL ending /v1/feed." : "Group not found. Check the Wise Old Man group ID.";
            return "Service returned an error. Retry later.";
        }
        if (error instanceof IllegalArgumentException && feed) return "Check the feed URL. Use HTTPS, or local http://127.0.0.1:8787/v1/feed without URL tokens.";
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof java.net.ConnectException) {
                boolean local = url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:");
                return feed && local ? "Local feed is not responding. Start F2S Bounty Bot and keep its window open."
                    : "Cannot connect. Check the service address and internet connection.";
            }
            if (cause instanceof java.net.UnknownHostException) return "Service address could not be found. Check the URL and internet connection.";
            if (cause instanceof java.io.InterruptedIOException) return "Connection timed out. Check the connection and retry.";
            cause = cause.getCause();
        }
        return feed ? "Unexpected feed response. Check /v1/feed and the bot's console."
            : "Event service unavailable. Check your connection and retry later.";
    }

    static int latest(JsonArray events, boolean boss) {
        return ClanEvents.select(events, boss, Instant.now());
    }

    private static final class EventChoice {
        final int id; final String label;
        EventChoice(JsonObject event, Instant now) {
            id = event.get("id").getAsInt();
            label = ClanEvents.status(event, now) + " · " + event.get("title").getAsString();
        }
        public String toString() { return label; }
    }

    private void updateEventPicker(long token) {
        List<JsonObject> events = ClanEvents.visible(discoveredEvents, Instant.now());
        if (events.stream().noneMatch(e -> e.get("id").getAsInt() == selectedEvent))
            selectedEvent = events.isEmpty() ? 0 : events.get(0).get("id").getAsInt();
        SwingUtilities.invokeLater(() -> {
            if (session != token) return;
            updatingPicker = true;
            try {
                eventPicker.removeAllItems();
                for (JsonObject event : events) {
                    EventChoice choice = new EventChoice(event, Instant.now());
                    eventPicker.addItem(choice);
                    if (choice.id == selectedEvent) eventPicker.setSelectedItem(choice);
                }
                if (events.isEmpty()) eventDetails.setText("No clan competitions found.");
            } finally { updatingPicker = false; }
        });
    }

    static void validateFeedUrl(String value) {
        URI uri = URI.create(value);
        boolean local = "http".equals(uri.getScheme()) &&
            ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
            throw new IllegalArgumentException("Use HTTPS or loopback HTTP");
    }

    private void stale(JTextComponent area, String message, long token) {
        SwingUtilities.invokeLater(() -> {
            if (session != token) return;
            String old = area.getText();
            int marker = old.indexOf("\n\nSTALE:");
            if (marker >= 0) old = old.substring(0, marker);
            area.setText(old + "\n\nSTALE: " + message);
        });
    }

    private boolean competition(JTextComponent area, int id, Boolean boss, long token) {
        if (id <= 0) { show(area, "Select a " + (Boolean.TRUE.equals(boss) ? "BOTW" : "SOTW") + " competition ID in plugin settings.", token); return true; }
        try {
            JsonObject data = get("https://api.wiseoldman.net/v2/competitions/" + id);
            JsonObject group = data.getAsJsonObject("group");
            if (group == null || group.get("id").getAsInt() != config.groupId()) throw new IOException("Wrong group");
            String metric = data.get("metric").getAsString();
            if (Boolean.TRUE.equals(boss) && !EventFormat.boss(metric)) throw new IOException("Not a boss metric");
            if (Boolean.FALSE.equals(boss) && !EventFormat.skill(metric)) throw new IOException("Not XP");
            Instant start = Instant.parse(data.get("startsAt").getAsString());
            Instant end = Instant.parse(data.get("endsAt").getAsString());
            Instant now = Instant.now();
            String state = ClanEvents.status(data, now);
            StringBuilder text = new StringBuilder(data.get("title").getAsString()).append("\n")
                .append(EventFormat.name(metric)).append(" · ").append(state)
                .append("\n").append(EventFormat.timing(start, end, now))
                .append("\nStarts ").append(EventFormat.date(start)).append("\nEnds ")
                .append(EventFormat.date(end)).append("\n\n");
            text.append(Standings.competition(data.getAsJsonArray("participations"), metric, state.equals("Completed")));
            text.append("\nUpdated ").append(EventFormat.date(now));
            if (area != eventDetails || selectedEvent == id) show(area, text.toString(), token);
            return true;
        } catch (Exception e) { stale(area, "Competition unavailable. Check ID, group and metric, or retry.", token); return false; }
    }


}
