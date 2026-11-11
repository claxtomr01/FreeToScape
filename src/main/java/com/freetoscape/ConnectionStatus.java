package com.freetoscape;

import java.time.Instant;

final class ConnectionStatus {
    private String status = "Waiting for first update";
    private String help = "";
    private Instant lastSuccess;
    void connected(Instant now) { status = "Connected"; help = ""; lastSuccess = now; }
    void unavailable(String message) { status = "Unavailable"; help = message; }
    void warning(String message) { status = "Connected — some data unavailable"; help = message; }
    void notConfigured() { status = "Not configured"; help = "Set Clan feed URL in plugin settings."; lastSuccess = null; }
    void reset() { status = "Waiting for first update"; help = ""; lastSuccess = null; }
    String text(String service) {
        return service + ": " + status + "\nLast successful update: "
            + (lastSuccess == null ? "Not yet" : EventFormat.date(lastSuccess))
            + (help.isEmpty() ? "" : "\n" + help);
    }
}
