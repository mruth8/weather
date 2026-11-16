package com.mruth8.realweathersky;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkyColorsTest
{
	private static final long SUNRISE = 1_794_812_463L;
	private static final long SUNSET = 1_794_845_348L;
	private static final long PREV_SUNSET = 1_794_759_018L;
	private static final long NEXT_SUNRISE = 1_794_898_953L;
	private static final long TW = SkyColors.TWILIGHT_SECONDS;

	private static WeatherData weather(int code, double mm, double snow, int cloud)
	{
		return new WeatherData(code, mm, snow, cloud, true,
			new long[]{SUNRISE - 86400, SUNRISE, NEXT_SUNRISE}, new long[]{PREV_SUNSET, SUNSET, SUNSET + 86400});
	}

	private static double luma(int c)
	{
		return 0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF);
	}

	@Test
	public void paletteMatchesTheApprovedValues()
	{
		assertEquals(0x1C2433, SkyColors.NIGHT);
		assertEquals(0xCFA27C, SkyColors.SUNRISE);
		assertEquals(0x8AA6B6, SkyColors.CLEAR);
		assertEquals(0xA4A49C, SkyColors.CLOUDY);
		assertEquals(0x6A7076, SkyColors.RAIN);
		assertEquals(0xD6DDE4, SkyColors.SNOW);
		assertEquals(0xC48462, SkyColors.SUNSET);
	}

	@Test
	public void deepNightIsNight()
	{
		WeatherData w = weather(0, 0, 0, 0);
		assertEquals(SkyColors.NIGHT, SkyColors.target(SUNSET + TW + 3600, w));
		assertEquals(SkyColors.NIGHT, SkyColors.target(SUNRISE - TW - 3600, w));
	}

	@Test
	public void dawnBlendsNightToSunriseToDay()
	{
		WeatherData w = weather(0, 0, 0, 0);
		assertEquals(SkyColors.NIGHT, SkyColors.target(SUNRISE - TW, w));
		assertEquals(SkyColors.SUNRISE, SkyColors.target(SUNRISE, w));
		assertEquals(SkyColors.CLEAR, SkyColors.target(SUNRISE + TW, w));
		int early = SkyColors.target(SUNRISE - TW / 2, w);
		assertTrue(luma(early) > luma(SkyColors.NIGHT) && luma(early) < luma(SkyColors.SUNRISE));
	}

	@Test
	public void duskBlendsDayToSunsetToNight()
	{
		WeatherData w = weather(0, 0, 0, 0);
		assertEquals(SkyColors.CLEAR, SkyColors.target(SUNSET - TW, w));
		assertEquals(SkyColors.SUNSET, SkyColors.target(SUNSET, w));
		assertEquals(SkyColors.NIGHT, SkyColors.target(SUNSET + TW, w));
	}

	@Test
	public void middayFollowsCloudCover()
	{
		long noon = (SUNRISE + SUNSET) / 2;
		assertEquals(SkyColors.CLEAR, SkyColors.target(noon, weather(0, 0, 0, 0)));
		assertEquals(SkyColors.CLEAR, SkyColors.target(noon, weather(1, 0, 0, 10)));
		assertEquals(SkyColors.CLOUDY, SkyColors.target(noon, weather(3, 0, 0, 100)));
		int partly = SkyColors.target(noon, weather(2, 0, 0, 50));
		assertNotEquals(SkyColors.CLEAR, partly);
		assertNotEquals(SkyColors.CLOUDY, partly);
	}

	@Test
	public void fogIsCloudy()
	{
		assertEquals(SkyColors.CLOUDY, SkyColors.dayColor(weather(45, 0, 0, 20)));
	}

	@Test
	public void snowByCodeOrByMeasurement()
	{
		assertEquals(SkyColors.SNOW, SkyColors.dayColor(weather(73, 0, 0, 100)));
		assertEquals(SkyColors.SNOW, SkyColors.dayColor(weather(3, 0.4, 0.5, 100)));
		assertEquals(SkyColors.SNOW, SkyColors.dayColor(weather(86, 0, 0, 100)));
	}

	@Test
	public void rainGetsDarkerWhenHeavier()
	{
		int drizzle = SkyColors.dayColor(weather(51, 0.1, 0, 100));
		int light = SkyColors.dayColor(weather(61, 1.0, 0, 100));
		int heavy = SkyColors.dayColor(weather(65, 10.0, 0, 100));
		int downpour = SkyColors.dayColor(weather(65, 50.0, 0, 100));
		assertEquals(SkyColors.RAIN, light);
		assertTrue(luma(drizzle) > luma(light));
		assertTrue(luma(heavy) < luma(light));
		assertEquals(heavy, downpour);
		assertEquals(SkyColors.scale(SkyColors.RAIN, 1 - SkyColors.MAX_RAIN_DARKENING), heavy);
	}

	@Test
	public void rainCodeWithZeroReadingStillLooksLikeRain()
	{
		int heavyCode = SkyColors.dayColor(weather(65, 0, 0, 100));
		assertTrue(luma(heavyCode) < luma(SkyColors.RAIN));
	}

	@Test
	public void thunderstormIsAtLeastModeratelyDark()
	{
		int storm = SkyColors.dayColor(weather(95, 0, 0, 100));
		assertTrue(luma(storm) < luma(SkyColors.RAIN));
		assertTrue(SkyColors.isThunderstorm(weather(99, 0, 0, 100)));
		assertTrue(!SkyColors.isThunderstorm(weather(65, 9, 0, 100)));
	}

	@Test
	public void polarFallbackUsesTheDayFlag()
	{
		WeatherData day = new WeatherData(0, 0, 0, 0, true, new long[0], new long[0]);
		WeatherData night = new WeatherData(0, 0, 0, 0, false, new long[0], new long[0]);
		assertEquals(SkyColors.CLEAR, SkyColors.target(5, day));
		assertEquals(SkyColors.NIGHT, SkyColors.target(5, night));
	}

	@Test
	public void lerpEndpointsAndMidpoint()
	{
		assertEquals(0x000000, SkyColors.lerp(0x000000, 0xFFFFFF, 0));
		assertEquals(0xFFFFFF, SkyColors.lerp(0x000000, 0xFFFFFF, 1));
		assertEquals(0x808080, SkyColors.lerp(0x000000, 0xFFFFFF, 0.5));
		assertEquals(0xFFFFFF, SkyColors.lerp(0x000000, 0xFFFFFF, 7));
		assertEquals(0x000000, SkyColors.lerp(0x000000, 0xFFFFFF, -3));
	}

	@Test
	public void everyTargetOverADayIsNeverBlack()
	{
		WeatherData w = weather(65, 9, 0, 100);
		for (long t = SUNRISE - 86400; t < SUNRISE + 86400; t += 60)
		{
			assertNotEquals(0, SkyColors.target(t, w));
		}
	}
}
