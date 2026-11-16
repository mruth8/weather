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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Turns Open-Meteo JSON into plain objects. Uses the Gson instance RuneLite injects into the plugin.
 */
public final class OpenMeteoParser
{
	private final Gson gson;

	public OpenMeteoParser(Gson gson)
	{
		this.gson = gson;
	}

	/** A resolved city. Coordinates are rounded to two decimals (about 1 km) before they are ever sent again. */
	public static final class Location
	{
		private final String name;
		private final double latitude;
		private final double longitude;

		public Location(String name, double latitude, double longitude)
		{
			this.name = name;
			this.latitude = Math.round(latitude * 100.0) / 100.0;
			this.longitude = Math.round(longitude * 100.0) / 100.0;
		}

		public String getName()
		{
			return name;
		}

		public double getLatitude()
		{
			return latitude;
		}

		public double getLongitude()
		{
			return longitude;
		}
	}

	/**
	 * Picks a place from a geocoding response.
	 *
	 * @param hint text typed after a comma, such as "France" or "Texas"; the first result whose country, region,
	 *             or country code matches it wins, otherwise the first result
	 * @return the place, or null if the response has no results
	 */
	@Nullable
	public Location parseGeocode(String json, @Nullable String hint)
	{
		JsonObject root = object(json);
		if (root == null || !root.has("results") || !root.get("results").isJsonArray())
		{
			return null;
		}
		JsonArray results = root.getAsJsonArray("results");
		JsonObject chosen = null;
		String wanted = hint == null ? "" : hint.trim().toLowerCase(Locale.ROOT);
		for (JsonElement e : results)
		{
			if (!e.isJsonObject() || !hasCoordinates(e.getAsJsonObject()))
			{
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			if (chosen == null)
			{
				chosen = o;
				if (wanted.isEmpty())
				{
					break;
				}
			}
			if (!wanted.isEmpty() && (string(o, "country").equalsIgnoreCase(wanted)
				|| string(o, "country_code").equalsIgnoreCase(wanted)
				|| string(o, "admin1").toLowerCase(Locale.ROOT).startsWith(wanted)))
			{
				chosen = o;
				break;
			}
		}
		if (chosen == null)
		{
			return null;
		}
		return new Location(string(chosen, "name"), chosen.get("latitude").getAsDouble(),
			chosen.get("longitude").getAsDouble());
	}

	/**
	 * Parses a forecast response requested with {@code timeformat=unixtime}.
	 *
	 * @return the data, or null if the response is not in the expected shape
	 */
	@Nullable
	public WeatherData parseForecast(String json)
	{
		JsonObject root = object(json);
		if (root == null || !root.has("current") || !root.get("current").isJsonObject())
		{
			return null;
		}
		JsonObject current = root.getAsJsonObject("current");
		if (!current.has("weather_code") || !current.get("weather_code").isJsonPrimitive())
		{
			return null;
		}
		long[] sunrises = new long[0];
		long[] sunsets = new long[0];
		if (root.has("daily") && root.get("daily").isJsonObject())
		{
			JsonObject daily = root.getAsJsonObject("daily");
			sunrises = times(daily, "sunrise");
			sunsets = times(daily, "sunset");
		}
		return new WeatherData(
			(int) number(current, "weather_code", 0),
			number(current, "precipitation", 0),
			number(current, "snowfall", 0),
			(int) number(current, "cloud_cover", 0),
			number(current, "is_day", 1) >= 1,
			sunrises,
			sunsets);
	}

	@Nullable
	private JsonObject object(String json)
	{
		try
		{
			return gson.fromJson(json, JsonObject.class);
		}
		catch (JsonSyntaxException e)
		{
			return null;
		}
	}

	private static boolean hasCoordinates(JsonObject o)
	{
		return isNumber(o.get("latitude")) && isNumber(o.get("longitude"));
	}

	private static boolean isNumber(@Nullable JsonElement e)
	{
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
	}

	private static String string(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static double number(JsonObject o, String key, double fallback)
	{
		JsonElement e = o.get(key);
		return isNumber(e) ? e.getAsDouble() : fallback;
	}

	/** Reads an array of Unix times, skipping nulls, which Open-Meteo sends when the sun does not rise or set. */
	private static long[] times(JsonObject daily, String key)
	{
		JsonElement e = daily.get(key);
		if (e == null || !e.isJsonArray())
		{
			return new long[0];
		}
		JsonArray a = e.getAsJsonArray();
		long[] out = new long[a.size()];
		int n = 0;
		for (JsonElement t : a)
		{
			if (isNumber(t))
			{
				out[n++] = t.getAsLong();
			}
		}
		return java.util.Arrays.copyOf(out, n);
	}
}
