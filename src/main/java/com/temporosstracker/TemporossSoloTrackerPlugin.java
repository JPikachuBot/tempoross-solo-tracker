package com.temporosstracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
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

    // Note: we rely on RuneLite's "Game message notifications" setting by emitting a GAMEMESSAGE,
    // instead of calling Notifier directly (prevents double notifications).

    @Inject
    private ConfigManager configManager;

    @Inject
    private TemporossSoloTrackerConfig config;

    private TemporossSoloTrackerPanel panel;
    private NavigationButton navButton;

    private boolean wasInFightRegion = false;

    // Track which phases we've already warned in for the current fight.
    private final Set<Integer> warnedPhasesThisFight = new HashSet<>();

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
        panel.setOnReset(() -> {
            persistState(trackerState);
            warnedPhasesThisFight.clear();
        });

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
        warnedPhasesThisFight.clear();
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
            warnedPhasesThisFight.clear();
            if (panel != null)
            {
                panel.resetChecklist();
            }
        }
        else if (!inFight && wasInFightRegion)
        {
            // Leaving the fight clears warning state.
            warnedPhasesThisFight.clear();
        }
        wasInFightRegion = inFight;

        // --- Storm intensity warning (notify once per relevant phase) ---
        if (!inFight || !config.notifyAt92() || panel == null)
        {
            return;
        }

        TrackerState trackerState = panel.getTrackerState();
        int activeIndex = trackerState.getActiveStepIndex();
        PhaseStep activeStep = trackerState.getStep(activeIndex);

        int phaseNumber = activeStep.getPhaseNumber();

        // Warn once per "cook" phase (not just when the active step happens to be the warning step).
        // ChecklistDefinition uses phaseNumber=2 for Phase 2 and phaseNumber=4 for Phase 3.
        if (phaseNumber != 2 && phaseNumber != 4)
        {
            return;
        }

        if (warnedPhasesThisFight.contains(phaseNumber))
        {
            return;
        }

        int stormIntensity = readStormIntensityPercent();
        if (stormIntensity < 92)
        {
            return;
        }

        String plain = "Storm at " + stormIntensity + "%, fill the cannon!";
        String chat = "<col=ff3d00>⚠ " + plain + "</col>";
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chat, null);
        warnedPhasesThisFight.add(phaseNumber);
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
        if (STORM_INTENSITY_WIDGET_GROUP_ID != -1 && STORM_INTENSITY_WIDGET_CHILD_ID != -1)
        {
            Widget widget = client.getWidget(STORM_INTENSITY_WIDGET_GROUP_ID, STORM_INTENSITY_WIDGET_CHILD_ID);
            if (widget != null)
            {
                String text = widget.getText();
                if (text != null)
                {
                    String digits = text.replace("%", "").replaceAll("[^0-9]", "");
                    if (!digits.isEmpty())
                    {
                        try
                        {
                            return Integer.parseInt(digits);
                        }
                        catch (NumberFormatException ignored)
                        {
                            // fall through
                        }
                    }
                }
            }
        }

        return -1;
    }
}
