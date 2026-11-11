package com.freetoscape;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;

public class BountyPasswordOverlay extends OverlayPanel {
    private final FreeToScapeConfig config;
    private volatile Password current;

    @Inject public BountyPasswordOverlay(FreeToScapeConfig config) {
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
    }

    void update(com.google.gson.JsonObject feed) {
        current = null;
        if (!feed.has("currentBounty") || !feed.get("currentBounty").isJsonObject()) return;
        com.google.gson.JsonObject bounty = feed.getAsJsonObject("currentBounty");
        String password = bounty.get("password").getAsString();
        long expiry = bounty.get("expiresAt").getAsLong();
        if (password.isBlank() || password.length() > 96 || password.contains("<")
                || password.contains(">") || password.contains("\n") || password.contains("\r")) return;
        current = new Password(password, expiry);
    }

    void clear() { current = null; }

    @Override public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        Password value = current;
        if (!config.showBountyPassword() || value == null
                || java.time.Instant.now().getEpochSecond() >= value.expiry) return null;
        panelComponent.getChildren().add(LineComponent.builder().left("Bounty password").build());
        panelComponent.getChildren().add(LineComponent.builder().left(value.text).build());
        return super.render(graphics);
    }

    private static final class Password {
        final String text; final long expiry;
        Password(String text, long expiry) { this.text = text; this.expiry = expiry; }
    }
}
