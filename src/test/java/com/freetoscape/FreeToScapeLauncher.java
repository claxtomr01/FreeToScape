package com.freetoscape;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
public class FreeToScapeLauncher {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(FreeToScapePlugin.class);
        RuneLite.main(args);
    }
}
