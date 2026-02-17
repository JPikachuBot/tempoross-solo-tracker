package com.temporosstracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
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
    private static final String CONFIG_GROUP = "tempoross-solo-tracker";
    private static final String CHECKLIST_STATE_KEY = "checklistState";

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
        List<PhaseStep> checklist = ChecklistDefinition.createChecklist();
        String serialized = configManager.getConfiguration(CONFIG_GROUP, CHECKLIST_STATE_KEY);
        TrackerState trackerState = TrackerState.deserialize(serialized, checklist);
        panel = new TemporossSoloTrackerPanel(checklist, trackerState);
        panel.setOnStateChange(this::persistState);
        panel.setOnReset(() -> persistState(trackerState));

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

    private void persistState(TrackerState state)
    {
        if (state == null)
        {
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, CHECKLIST_STATE_KEY, state.serialize());
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
