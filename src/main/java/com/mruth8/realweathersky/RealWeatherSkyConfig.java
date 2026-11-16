/*
 * Copyright (c) 2026, Matthew Ruth
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.mruth8.realweathersky;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(RealWeatherSkyConfig.GROUP)
public interface RealWeatherSkyConfig extends Config
{
	String GROUP = "realweathersky";

	@ConfigItem(
		keyName = "city",
		name = "City",
		description = "The city whose real weather and daylight to follow, such as London, or Paris, France.",
		position = 1
	)
	default String city()
	{
		return "";
	}

	@ConfigItem(
		keyName = "lookupEnabled",
		name = "Enable weather lookup",
		description = "<html>Off by default. When on, and while logged in,<br>"
			+ "every 10 minutes this sends the city text, then<br>"
			+ "its rounded coordinates, to Open-Meteo<br>"
			+ "(open-meteo.com), a third-party service that<br>"
			+ "can see your IP address. Nothing about your<br>"
			+ "account or character is sent. Also turn off the<br>"
			+ "core Skybox plugin.</html>",
		position = 2
	)
	default boolean lookupEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fadeSeconds",
		name = "Fade time (seconds)",
		description = "How long a change of weather takes to blend in.",
		position = 3
	)
	@Range(min = 5, max = 120)
	default int fadeSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "lightning",
		name = "Lightning flash",
		description = "<html>Off by default. During thunderstorms,<br>"
			+ "briefly and gently brightens the sky color about<br>"
			+ "every 20 to 60 seconds. Sky only. Leave off<br>"
			+ "if you are sensitive to flashing.</html>",
		position = 4
	)
	default boolean lightning()
	{
		return false;
	}
}
