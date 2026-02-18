package com.temporosstracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "Tempoross Solo Tracker",
    description = "Interactive checklist + storm intensity notification for solo Tempoross",
    tags = {"tempoross", "minigame", "tracker", "checklist"}
)
public class TemporossSoloTrackerPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(TemporossSoloTrackerPlugin.class);

    // Shipping default: no debug logging. Keep the hooks so we can re-enable quickly later.
    private static final boolean DEBUG = false;

    // Verified lobby/waiting area region ID (Jackson)
    private static final int TEMPOROSS_LOBBY_REGION_ID = 12588;

    // Verified: storm intensity text widget (Jackson)
    private static final int STORM_INTENSITY_WIDGET_GROUP_ID = 437;
    private static final int STORM_INTENSITY_WIDGET_CHILD_ID = 55;
    private static final Pattern STORM_INTENSITY_PATTERN = Pattern.compile("(\\d{1,3})");

    private static final String SOLO_START_OPTION = "Solo-start";
    private static final String SOLO_START_TARGET = "Rope ladder";

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private Notifier notifier;

    @Inject
    private TemporossSoloTrackerConfig config;

    private TemporossSoloTrackerPanel panel;
    private NavigationButton navButton;

    // Solo-only gate: only true after the player clicks Solo-start on the boat.
    private boolean soloRunActive = false;

    private int lastRegionId = -1;
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
        TrackerState trackerState = TrackerState.fromChecklist(checklist);
        panel = new TemporossSoloTrackerPanel(checklist, trackerState);
        panel.setOnReset(() -> {
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

        lastRegionId = getRegionIdSafe();
        soloRunActive = false;
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

        soloRunActive = false;
        lastRegionId = -1;
        lastStormIntensity = -1;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e)
    {
        // Start tracking only for solo runs.
        String opt = e.getMenuOption();
        if (!SOLO_START_OPTION.equals(opt))
        {
            return;
        }

        String target = Text.removeTags(e.getMenuTarget());
        if (!SOLO_START_TARGET.equals(target))
        {
            return;
        }

        soloRunActive = true;
        lastStormIntensity = -1;

        if (DEBUG)
        {
            log.debug("Solo-start clicked: action={} id={} p0={} p1={} regionId={}",
                e.getMenuAction(), e.getId(), e.getParam0(), e.getParam1(), getRegionIdSafe());
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        int regionId = getRegionIdSafe();

        // Reset checklist ONLY when the player returns to the lobby region (game ended).
        if (config.autoReset() && regionId == TEMPOROSS_LOBBY_REGION_ID && lastRegionId != TEMPOROSS_LOBBY_REGION_ID)
        {
            soloRunActive = false;
            lastStormIntensity = -1;
            if (panel != null)
            {
                panel.resetChecklist();
            }
        }
        lastRegionId = regionId;

        // Storm intensity notification: solo-only, fight-UI-only, and rising-edge like Idle Notifier.
        if (!soloRunActive)
        {
            return;
        }

        StormIntensityReading reading = readStormIntensity();
        if (!reading.fightUiPresent)
        {
            return;
        }

        int intensity = reading.intensity;
        if (intensity < 0)
        {
            if (DEBUG)
            {
                log.debug("Storm notify debug: rawText='{}' parsed=-1 threshold={} lastIntensity={}",
                    reading.rawText, config.stormNotifyThreshold(), lastStormIntensity);
            }
            return;
        }

        int threshold = config.stormNotifyThreshold();
        boolean crossedUp = (lastStormIntensity < threshold) && (intensity >= threshold);
        if (crossedUp)
        {
            notifier.notify(config.stormNotifyNotification(), "Storm intensity at " + intensity + "%");
        }

        if (DEBUG)
        {
            log.debug("Storm notify debug: intensity={} threshold={} lastIntensity={} fired={} rawText='{}'",
                intensity, threshold, lastStormIntensity, crossedUp, reading.rawText);
        }

        lastStormIntensity = intensity;
    }

    private StormIntensityReading readStormIntensity()
    {
        Widget widget = client.getWidget(STORM_INTENSITY_WIDGET_GROUP_ID, STORM_INTENSITY_WIDGET_CHILD_ID);
        if (widget == null)
        {
            return new StormIntensityReading(-1, null, false);
        }

        String rawText = widget.getText();
        int value = parseStormIntensity(rawText);
        return new StormIntensityReading(value, rawText, true);
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
        private final boolean fightUiPresent;

        private StormIntensityReading(int intensity, String rawText, boolean fightUiPresent)
        {
            this.intensity = intensity;
            this.rawText = rawText;
            this.fightUiPresent = fightUiPresent;
        }
    }
}
