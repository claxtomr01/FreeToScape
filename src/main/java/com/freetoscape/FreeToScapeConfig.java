package com.freetoscape;

import net.runelite.client.config.*;

@ConfigGroup("freetoscape")
public interface FreeToScapeConfig extends Config {
    @ConfigItem(keyName="groupId", name="Wise Old Man group", description="FreeToScape group ID", position=0)
    default int groupId() { return 8036; }
    @ConfigItem(keyName="sotwId", name="SOTW competition ID", description="0 selects active SOTW, then next upcoming, then latest completed", position=1)
    default int sotwId() { return 0; }
    @ConfigItem(keyName="botwId", name="BOTW competition ID", description="0 selects active BOTW, then next upcoming, then latest completed", position=2)
    default int botwId() { return 0; }
    @ConfigItem(keyName="feedUrl", name="Clan feed URL", description="HTTPS URL ending /v1/feed; loopback HTTP permitted for local development", position=3)
    default String feedUrl() { return ""; }
    @ConfigItem(keyName="broadcasts", name="New bounty broadcasts", description="Show [Clan Broadcast] in red, followed by: A new bounty has been posted!", position=4)
    default boolean broadcasts() { return true; }
    @ConfigItem(keyName="showBountyPassword", name="Show bounty password", description="Display the currently posted regular bounty password on screen until it expires", position=5)
    default boolean showBountyPassword() { return false; }
    @ConfigItem(keyName="clanActivityReporting", name="Officer clan activity reporting", description="OFF by default. Uploads officer-linked RuneScape names, clan ranks and observed channel presence to your bot; activity is retained for 7 days. Use only as an authorised reporter.", position=6)
    default boolean clanActivityReporting() { return false; }
    @ConfigItem(keyName="clanSyncUrl", name="Clan sync server URL", description="Authenticated officer reporter server base URL. HTTPS remotely; loopback HTTP locally. No credentials in this URL.", position=7)
    default String clanSyncUrl() { return ""; }
}
