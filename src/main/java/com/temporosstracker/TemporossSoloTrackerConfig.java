package com.temporosstracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("temporossSoloTracker")
public interface TemporossSoloTrackerConfig extends Config
{
    @ConfigItem(
        keyName = "notifyAt92",
        name = "Notify at 92%",
        description = "Enable/disable 92% storm intensity notification"
    )
    default boolean notifyAt92()
    {
        return true;
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
