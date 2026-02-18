package com.temporosstracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

@ConfigGroup("temporossSoloTracker")
public interface TemporossSoloTrackerConfig extends Config
{
    @ConfigItem(
        keyName = "stormNotifyNotification",
        name = "Storm notify",
        description = "Notification settings for storm intensity alerts"
    )
    default Notification stormNotifyNotification()
    {
        return Notification.ON;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "stormNotifyThreshold",
        name = "Storm notify threshold (%)",
        description = "Storm intensity threshold for notification"
    )
    default int stormNotifyThreshold()
    {
        return 92;
    }

    @ConfigItem(
        keyName = "stormNotifyDebug",
        name = "Storm notify debug logging",
        description = "Log storm intensity reads and notification checks (debug only)"
    )
    default boolean stormNotifyDebug()
    {
        return false;
    }

    @ConfigItem(
        keyName = "autoReset",
        name = "Auto reset",
        description = "Auto-reset checkboxes on new Tempoross game"
    )
    default boolean autoReset()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightCurrentStep",
        name = "Highlight current step",
        description = "Highlight the next unchecked step"
    )
    default boolean highlightCurrentStep()
    {
        return true;
    }
}
