package com.temporosstracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("temporossSoloTracker")
public interface TemporossSoloTrackerConfig extends Config
{
    @ConfigItem(
        keyName = "notifyAt92",
        name = "Storm notify enabled",
        description = "Enable/disable storm intensity notifications"
    )
    default boolean notifyAt92()
    {
        return true;
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
        keyName = "notifyCooldownTicks",
        name = "Notify cooldown (ticks)",
        description = "Ticks between repeated 92% warnings"
    )
    default int notifyCooldownTicks()
    {
        return 5;
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
