package com.temporosstracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
    name = "Tempoross Solo Tracker",
    description = "Interactive checklist for solo Tempoross runs",
    tags = {"tempoross", "minigame", "tracker", "checklist"}
)
public class TemporossSoloTrackerPlugin extends Plugin
{
    // TODO (Appendix A): Replace placeholders with verified region IDs.
    private static final int TEMPOROSS_LOBBY_REGION_ID = 12078;
    private static final int TEMPOROSS_FIGHT_REGION_ID = 12588;

    // TODO (Appendix B/C): Replace placeholders with verified widget/var IDs.
    private static final int STORM_INTENSITY_WIDGET_GROUP_ID = -1;
    private static final int STORM_INTENSITY_WIDGET_CHILD_ID = -1;
    private static final int STORM_INTENSITY_VARPLAYER_ID = -1;
    private static final int STORM_INTENSITY_VARBIT_ID = -1;

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private Notifier notifier;

    @Inject
    private ConfigManager configManager;

    private TemporossSoloTrackerPanel panel;
    private NavigationButton navButton;

    @Provides
    TemporossSoloTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TemporossSoloTrackerConfig.class);
    }

    @Override
    protected void startUp()
    {
        panel = new TemporossSoloTrackerPanel();

        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navButton = NavigationButton.builder()
            .tooltip("Tempoross Solo Tracker")
            .icon(icon)
            .panel(panel)
            .priority(5)
            .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        panel = null;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // TODO: Implement storm intensity tracking + notifications.
        // TEMPORARY DEBUG — REMOVE AFTER VERIFYING REGION IDS
        // client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Region ID: "
        //     + client.getLocalPlayer().getWorldLocation().getRegionID(), null);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        // TODO: Implement auto-reset based on region transitions.
    }
}
