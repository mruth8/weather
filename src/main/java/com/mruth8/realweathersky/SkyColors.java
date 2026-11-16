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

/**
 * Palette and color selection. Pure Java with no RuneLite dependency, so it is unit-testable and benchmarkable.
 */
public final class SkyColors
{
	public static final int NIGHT = 0x1C2433;
	public static final int SUNRISE = 0xCFA27C;
	public static final int CLEAR = 0x8AA6B6;
	public static final int CLOUDY = 0xA4A49C;
	public static final int RAIN = 0x6A7076;
	public static final int SNOW = 0xD6DDE4;
	public static final int SUNSET = 0xC48462;

	/** Length of the dawn blend before sunrise, and of the dusk blend after sunset, in seconds. */
	static final long TWILIGHT_SECONDS = 40 * 60;

	/** Heaviest shading applied to rain, as a fraction of the rain color removed. */
	static final double MAX_RAIN_DARKENING = 0.30;

	private SkyColors()
	{
	}

	public static boolean isSnow(WeatherData w)
	{
		int c = w.getWeatherCode();
		return w.getSnowfallCm() > 0 || (c >= 71 && c <= 77) || c == 85 || c == 86;
	}

	public static boolean isThunderstorm(WeatherData w)
	{
		int c = w.getWeatherCode();
		return c >= 95 && c <= 99;
	}

	/**
	 * Rain intensity in mm per hour. Uses the measured precipitation, but never less than a typical figure
	 * for the reported weather code, so a "heavy rain" code with a zero reading still looks like rain.
	 */
	static double rainMm(WeatherData w)
	{
		double typical;
		switch (w.getWeatherCode())
		{
			case 51:
				typical = 0.3;
				break;
			case 53:
				typical = 0.7;
				break;
			case 55:
				typical = 1.2;
				break;
			case 56:
			case 61:
			case 80:
				typical = 1.0;
				break;
			case 57:
			case 66:
				typical = 2.0;
				break;
			case 63:
			case 81:
				typical = 3.0;
				break;
			case 65:
			case 67:
			case 82:
				typical = 8.0;
				break;
			case 95:
			case 96:
			case 99:
				typical = 6.0;
				break;
			default:
				typical = 0.0;
		}
		return Math.max(typical, w.getPrecipitationMm());
	}

	static boolean isRain(WeatherData w)
	{
		return !isSnow(w) && rainMm(w) > 0;
	}

	/** The daytime sky color for the conditions: snow, then rain (darker when heavier), then clear to cloudy. */
	public static int dayColor(WeatherData w)
	{
		if (isSnow(w))
		{
			return SNOW;
		}
		if (isRain(w))
		{
			double mm = rainMm(w);
			if (isThunderstorm(w))
			{
				mm = Math.max(mm, 6.0);
			}
			if (mm < 1.0)
			{
				return lerp(CLOUDY, RAIN, 0.35 + 0.65 * mm);
			}
			double heavy = smooth(clamp((mm - 1.0) / 9.0));
			return scale(RAIN, 1.0 - MAX_RAIN_DARKENING * heavy);
		}
		int code = w.getWeatherCode();
		if (code == 45 || code == 48)
		{
			return CLOUDY;
		}
		return lerp(CLEAR, CLOUDY, smooth(clamp((w.getCloudCover() - 15) / 70.0)));
	}

	/**
	 * The target sky color at the given real time. Around sunrise it blends night, sunrise, then the day color.
	 * Around sunset it blends the day color, sunset, then night.
	 *
	 * @param nowEpochSeconds the real clock, in Unix seconds
	 */
	public static int target(long nowEpochSeconds, WeatherData w)
	{
		int day = dayColor(w);
		long[] sunrises = w.sunrises();
		long[] sunsets = w.sunsets();

		long lastSunrise = Long.MIN_VALUE;
		long lastSunset = Long.MIN_VALUE;
		for (long s : sunrises)
		{
			long d = nowEpochSeconds - s;
			if (d >= -TWILIGHT_SECONDS && d <= TWILIGHT_SECONDS)
			{
				return d < 0
					? lerp(NIGHT, SUNRISE, smooth((double) (d + TWILIGHT_SECONDS) / TWILIGHT_SECONDS))
					: lerp(SUNRISE, day, smooth((double) d / TWILIGHT_SECONDS));
			}
			if (s <= nowEpochSeconds && s > lastSunrise)
			{
				lastSunrise = s;
			}
		}
		for (long s : sunsets)
		{
			long d = nowEpochSeconds - s;
			if (d >= -TWILIGHT_SECONDS && d <= TWILIGHT_SECONDS)
			{
				return d < 0
					? lerp(day, SUNSET, smooth((double) (d + TWILIGHT_SECONDS) / TWILIGHT_SECONDS))
					: lerp(SUNSET, NIGHT, smooth((double) d / TWILIGHT_SECONDS));
			}
			if (s <= nowEpochSeconds && s > lastSunset)
			{
				lastSunset = s;
			}
		}

		if (lastSunrise == Long.MIN_VALUE && lastSunset == Long.MIN_VALUE)
		{
			// Polar day or night, or no sun times in the data: trust the service's own day flag.
			return w.isDay() ? day : NIGHT;
		}
		return lastSunrise > lastSunset ? day : NIGHT;
	}

	public static int lerp(int a, int b, double t)
	{
		t = clamp(t);
		int r = channel(a >> 16, b >> 16, t);
		int g = channel(a >> 8, b >> 8, t);
		int bl = channel(a, b, t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int channel(int a, int b, double t)
	{
		a &= 0xFF;
		b &= 0xFF;
		return (int) Math.round(a + (b - a) * t);
	}

	static int scale(int rgb, double factor)
	{
		int r = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
		int g = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
		int b = (int) Math.round((rgb & 0xFF) * factor);
		return (r << 16) | (g << 8) | b;
	}

	static double clamp(double v)
	{
		return v < 0 ? 0 : Math.min(v, 1);
	}

	static double smooth(double t)
	{
		return t * t * (3 - 2 * t);
	}
}
