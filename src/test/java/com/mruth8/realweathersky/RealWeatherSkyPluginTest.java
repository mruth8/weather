package com.mruth8.realweathersky;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches the RuneLite client with this plugin loaded, for manual testing. */
public class RealWeatherSkyPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RealWeatherSkyPlugin.class);
		RuneLite.main(args);
	}
}
