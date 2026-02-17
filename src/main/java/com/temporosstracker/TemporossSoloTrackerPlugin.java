package com.temporosstracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
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

    // Verified region IDs (Jackson, 2026-02-17)
    private static final int TEMPOROSS_LOBBY_REGION_ID = 12588;
    private static final int TEMPOROSS_FIGHT_REGION_ID = 12076;

    // Verified (Jackson, 2026-02-17): Tempoross HUD widgets are in group 437.
    // The readable text (e.g. "Storm intensity: 86%") appears on child 55 (STORM_INTENSITY_TITLE).
    private static final int STORM_INTENSITY_WIDGET_GROUP_ID = 437;
    private static final int STORM_INTENSITY_WIDGET_CHILD_ID = 55;
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

    @Inject
    private TemporossSoloTrackerConfig config;

    private TemporossSoloTrackerPanel panel;
    private NavigationButton navButton;

    private boolean wasInFightRegion = false;
    private int notifyCooldownRemaining = 0;

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

        // Initialize region tracking so we don't auto-reset if the plugin is enabled mid-fight.
        wasInFightRegion = isInFightRegion();
        notifyCooldownRemaining = 0;
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
        // --- Auto-reset on entering fight region (new game) ---
        boolean inFight = isInFightRegion();
        if (config.autoReset() && inFight && !wasInFightRegion)
        {
            if (panel != null)
            {
                panel.resetChecklist();
            }
        }
        wasInFightRegion = inFight;

        // --- Storm intensity warning (optional; compiles even when unknown) ---
        int stormIntensity = readStormIntensityPercent();
        if (stormIntensity >= 92 && config.notifyAt92())
        {
            if (notifyCooldownRemaining <= 0)
            {
                String plain = "⚠ Storm intensity at " + stormIntensity + "%! Wait before filling cannon!";
                // Always show an in-client warning in chat.
                // RuneLite chat supports <col=...> tags.
                String chat = "<col=ff3d00>" + plain + "</col>";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chat, null);

                // Desktop notification: will only show if the user has RuneLite notifications enabled.
                notifier.notify(plain);

                notifyCooldownRemaining = Math.max(1, config.notifyCooldownTicks());
            }
            else
            {
                notifyCooldownRemaining--;
            }
        }
        else
        {
            notifyCooldownRemaining = 0;
        }
    }

    private boolean isInFightRegion()
    {
        if (client == null || client.getLocalPlayer() == null)
        {
            return false;
        }
        return client.getLocalPlayer().getWorldLocation().getRegionID() == TEMPOROSS_FIGHT_REGION_ID;
    }

    /**
     * Returns storm intensity percent, or -1 if not yet wired.
     */
    private int readStormIntensityPercent()
    {
        // VarBit is preferred if known
        if (STORM_INTENSITY_VARBIT_ID != -1)
        {
            return client.getVarbitValue(STORM_INTENSITY_VARBIT_ID);
        }

        // VarPlayer fallback
        if (STORM_INTENSITY_VARPLAYER_ID != -1)
        {
            return client.getVarpValue(STORM_INTENSITY_VARPLAYER_ID);
        }

        // Widget fallback (parse something like "Storm intensity: 86%")
        if (STORM_INTENSITY_WIDGET_GROUP_ID != -1)
        {
            // Primary child (verified)
            int[] candidateChildren = new int[] {STORM_INTENSITY_WIDGET_CHILD_ID, 23};

            for (int childId : candidateChildren)
            {
                if (childId == -1)
                {
                    continue;
                }

                Widget widget = client.getWidget(STORM_INTENSITY_WIDGET_GROUP_ID, childId);
                if (widget == null)
                {
                    continue;
                }

                String text = widget.getText();
                if (text == null)
                {
                    continue;
                }

                String digits = text.replace("%", "").replaceAll("[^0-9]", "");
                if (digits.isEmpty())
                {
                    continue;
                }

                try
                {
                    return Integer.parseInt(digits);
                }
                catch (NumberFormatException ignored)
                {
                    // try next candidate
                }
            }
        }

        return -1;
    }
}
