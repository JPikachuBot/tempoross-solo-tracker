package com.temporosstracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TemporossSoloTrackerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(TemporossSoloTrackerPlugin.class);
        RuneLite.main(args);
    }
}
