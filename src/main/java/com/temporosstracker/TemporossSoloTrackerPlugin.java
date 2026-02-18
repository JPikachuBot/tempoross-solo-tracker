package com.temporosstracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "Tempoross Solo Tracker",
    description = "Interactive checklist for solo Tempoross runs",
    tags = {"tempoross", "minigame", "tracker", "checklist"}
)
public class TemporossSoloTrackerPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(TemporossSoloTrackerPlugin.class);

    private static final String CONFIG_GROUP = "tempoross-solo-tracker";
    private static final String CHECKLIST_STATE_KEY = "checklistState";

    // Verified region ID (Jackson, 2026-02-17)
    private static final int TEMPOROSS_FIGHT_REGION_ID = 12076;

    // Verified (Jackson, 2026-02-17): Tempoross HUD widgets are in group 437.
    // The readable text (e.g. "Storm intensity: 86%") appears on child 55 (STORM_INTENSITY_TITLE).
    private static final int STORM_INTENSITY_WIDGET_GROUP_ID = 437;
    private static final int STORM_INTENSITY_WIDGET_CHILD_ID = 55;
    private static final int STORM_INTENSITY_VARPLAYER_ID = -1;
    private static final int STORM_INTENSITY_VARBIT_ID = -1;
    private static final Pattern STORM_INTENSITY_PATTERN = Pattern.compile("(\\d{1,3})");

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

    private int lastStormIntensity = -1;

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
            lastStormIntensity = -1;
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
        lastStormIntensity = -1;
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
            lastStormIntensity = -1;
            if (panel != null)
            {
                panel.resetChecklist();
            }
        }
        else if (!inFight && wasInFightRegion)
        {
            // Leaving the fight clears warning state.
            lastStormIntensity = -1;
        }
        wasInFightRegion = inFight;

        // --- Storm intensity warning (edge-trigger at configured threshold) ---
        // Notify each time storm crosses from below threshold to >= threshold (not continuously).
        if (!inFight)
        {
            return;
        }

        StormIntensityReading reading = readStormIntensity();
        int stormIntensity = reading.intensity;
        if (stormIntensity < 0)
        {
            if (config.stormNotifyDebug())
            {
                log.debug(
                    "Storm notify debug: regionId={}, rawText='{}', parsedIntensity={}, threshold={}, lastIntensity={}, fired={}",
                    getRegionIdSafe(),
                    reading.rawText,
                    stormIntensity,
                    config.stormNotifyThreshold(),
                    lastStormIntensity,
                    false
                );
            }
            return;
        }

        int threshold = config.stormNotifyThreshold();
        int previousIntensity = lastStormIntensity;
        boolean fired = false;

        // Fire on rising edge: previously below threshold, now at/above.
        if (previousIntensity >= 0 && previousIntensity < threshold && stormIntensity >= threshold)
        {
            String plain = "Storm at " + stormIntensity + "%, fill the cannon!";

            // Match Idle Notifier behavior:
            // - Call Notifier with a Notification config entry
            // - Let Notifier decide whether to also emit an in-client CONSOLE message,
            //   based on RuneLite notification settings.
            Notification stormNotification = config.stormNotifyNotification();
            notifier.notify(stormNotification, plain);
            fired = true;
        }

        lastStormIntensity = stormIntensity;

        if (config.stormNotifyDebug())
        {
            log.debug(
                "Storm notify debug: regionId={}, rawText='{}', parsedIntensity={}, threshold={}, lastIntensity={}, fired={}",
                getRegionIdSafe(),
                reading.rawText,
                stormIntensity,
                threshold,
                previousIntensity,
                fired
            );
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

    private StormIntensityReading readStormIntensity()
    {
        String rawText = null;

        // VarBit is preferred if known
        if (STORM_INTENSITY_VARBIT_ID != -1)
        {
            return new StormIntensityReading(client.getVarbitValue(STORM_INTENSITY_VARBIT_ID), rawText);
        }

        // VarPlayer fallback
        if (STORM_INTENSITY_VARPLAYER_ID != -1)
        {
            return new StormIntensityReading(client.getVarpValue(STORM_INTENSITY_VARPLAYER_ID), rawText);
        }

        // Widget fallback (parse something like "Storm intensity: 86%")
        if (STORM_INTENSITY_WIDGET_GROUP_ID != -1 && STORM_INTENSITY_WIDGET_CHILD_ID != -1)
        {
            Widget widget = client.getWidget(STORM_INTENSITY_WIDGET_GROUP_ID, STORM_INTENSITY_WIDGET_CHILD_ID);
            if (widget != null)
            {
                rawText = widget.getText();
            }
        }

        return new StormIntensityReading(parseStormIntensity(rawText), rawText);
    }

    private int parseStormIntensity(String text)
    {
        if (text == null)
        {
            return -1;
        }

        Matcher matcher = STORM_INTENSITY_PATTERN.matcher(text);
        if (!matcher.find())
        {
            return -1;
        }

        try
        {
            int value = Integer.parseInt(matcher.group(1));
            return value >= 0 && value <= 100 ? value : -1;
        }
        catch (NumberFormatException ignored)
        {
            return -1;
        }
    }

    private int getRegionIdSafe()
    {
        if (client == null || client.getLocalPlayer() == null)
        {
            return -1;
        }
        return client.getLocalPlayer().getWorldLocation().getRegionID();
    }

    private static final class StormIntensityReading
    {
        private final int intensity;
        private final String rawText;

        private StormIntensityReading(int intensity, String rawText)
        {
            this.intensity = intensity;
            this.rawText = rawText;
        }
    }
}
