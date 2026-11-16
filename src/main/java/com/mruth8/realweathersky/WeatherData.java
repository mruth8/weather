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
 * An immutable snapshot of the current conditions and the nearby sunrise and sunset times for one city.
 * All times are Unix epoch seconds, so no time zone maths is needed to compare them with the real clock.
 */
public final class WeatherData
{
	private final int weatherCode;
	private final double precipitationMm;
	private final double snowfallCm;
	private final int cloudCover;
	private final boolean day;
	private final long[] sunrises;
	private final long[] sunsets;

	public WeatherData(int weatherCode, double precipitationMm, double snowfallCm, int cloudCover, boolean day,
		long[] sunrises, long[] sunsets)
	{
		this.weatherCode = weatherCode;
		this.precipitationMm = precipitationMm;
		this.snowfallCm = snowfallCm;
		this.cloudCover = cloudCover;
		this.day = day;
		this.sunrises = sunrises.clone();
		this.sunsets = sunsets.clone();
	}

	/** WMO weather interpretation code. */
	public int getWeatherCode()
	{
		return weatherCode;
	}

	/** Precipitation over the preceding hour, in millimetres. */
	public double getPrecipitationMm()
	{
		return precipitationMm;
	}

	/** Snowfall over the preceding hour, in centimetres. */
	public double getSnowfallCm()
	{
		return snowfallCm;
	}

	/** Cloud cover from 0 to 100 percent. */
	public int getCloudCover()
	{
		return cloudCover;
	}

	/** Open-Meteo's own is_day flag, used only when sunrise or sunset times are missing (polar day or night). */
	public boolean isDay()
	{
		return day;
	}

	long[] sunrises()
	{
		return sunrises;
	}

	long[] sunsets()
	{
		return sunsets;
	}
}
