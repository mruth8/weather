package com.mruth8.realweathersky;

import com.google.gson.Gson;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class OpenMeteoParserTest
{
	private final OpenMeteoParser parser = new OpenMeteoParser(new Gson());

	/** A real forecast response for Paris, requested with timeformat=unixtime, past_days=1, forecast_days=2. */
	static final String PARIS_FORECAST = "{\"latitude\":48.853382,\"longitude\":2.355208,\"generationtime_ms\":0.07,"
		+ "\"utc_offset_seconds\":3600,\"timezone\":\"Europe/Paris\",\"timezone_abbreviation\":\"GMT+1\","
		+ "\"elevation\":46.0,\"current_units\":{\"time\":\"unixtime\",\"interval\":\"seconds\","
		+ "\"weather_code\":\"wmo code\",\"precipitation\":\"mm\",\"snowfall\":\"cm\",\"cloud_cover\":\"%\",\"is_day\":\"\"},"
		+ "\"current\":{\"time\":1794806100,\"interval\":900,\"weather_code\":3,\"precipitation\":0.00,\"snowfall\":0.00,"
		+ "\"cloud_cover\":86,\"is_day\":0},\"daily_units\":{\"time\":\"unixtime\",\"sunrise\":\"unixtime\","
		+ "\"sunset\":\"unixtime\"},\"daily\":{\"time\":[1794697200,1794783600,1794870000],"
		+ "\"sunrise\":[1794725972,1794812463,1794898953],\"sunset\":[1794759018,1794845348,1794931681]}}";

	static final String GEOCODE = "{\"results\":["
		+ "{\"id\":2988507,\"name\":\"Paris\",\"latitude\":48.85341,\"longitude\":2.3488,\"country_code\":\"FR\","
		+ "\"timezone\":\"Europe/Paris\",\"country\":\"France\",\"admin1\":\"Ile-de-France Region\"},"
		+ "{\"id\":4717560,\"name\":\"Paris\",\"latitude\":33.66094,\"longitude\":-95.55551,\"country_code\":\"US\","
		+ "\"timezone\":\"America/Chicago\",\"country\":\"United States\",\"admin1\":\"Texas\"}],"
		+ "\"generationtime_ms\":0.5}";

	@Test
	public void parsesRealForecast()
	{
		WeatherData w = parser.parseForecast(PARIS_FORECAST);
		assertNotNull(w);
		assertEquals(3, w.getWeatherCode());
		assertEquals(0.0, w.getPrecipitationMm(), 0.0001);
		assertEquals(86, w.getCloudCover());
		assertFalse(w.isDay());
		assertArrayEquals(new long[]{1794725972L, 1794812463L, 1794898953L}, w.sunrises());
		assertArrayEquals(new long[]{1794759018L, 1794845348L, 1794931681L}, w.sunsets());
	}

	@Test
	public void parsesRainAndSnowFields()
	{
		String json = "{\"current\":{\"weather_code\":65,\"precipitation\":7.4,\"snowfall\":1.5,\"cloud_cover\":100,"
			+ "\"is_day\":1},\"daily\":{\"sunrise\":[10],\"sunset\":[20]}}";
		WeatherData w = parser.parseForecast(json);
		assertNotNull(w);
		assertEquals(65, w.getWeatherCode());
		assertEquals(7.4, w.getPrecipitationMm(), 0.0001);
		assertEquals(1.5, w.getSnowfallCm(), 0.0001);
		assertTrue(w.isDay());
	}

	@Test
	public void skipsNullSunTimesInPolarRegions()
	{
		String json = "{\"current\":{\"weather_code\":0,\"cloud_cover\":0,\"is_day\":1},"
			+ "\"daily\":{\"sunrise\":[null,null],\"sunset\":[null]}}";
		WeatherData w = parser.parseForecast(json);
		assertNotNull(w);
		assertEquals(0, w.sunrises().length);
		assertEquals(0, w.sunsets().length);
		assertEquals(SkyColors.dayColor(w), SkyColors.target(1_000_000L, w));
	}

	@Test
	public void missingDailyBlockStillParses()
	{
		WeatherData w = parser.parseForecast("{\"current\":{\"weather_code\":1,\"cloud_cover\":10}}");
		assertNotNull(w);
		assertEquals(0, w.sunrises().length);
	}

	@Test
	public void rejectsBadForecasts()
	{
		assertNull(parser.parseForecast("not json"));
		assertNull(parser.parseForecast(""));
		assertNull(parser.parseForecast("[]"));
		assertNull(parser.parseForecast("{\"error\":true,\"reason\":\"Latitude must be in range\"}"));
		assertNull(parser.parseForecast("{\"current\":{\"cloud_cover\":10}}"));
	}

	@Test
	public void geocodeTakesFirstResultWithoutHint()
	{
		OpenMeteoParser.Location l = parser.parseGeocode(GEOCODE, "");
		assertNotNull(l);
		assertEquals("Paris", l.getName());
		assertEquals(48.85, l.getLatitude(), 0.0001);
		assertEquals(2.35, l.getLongitude(), 0.0001);
	}

	@Test
	public void geocodeUsesCountryOrRegionHint()
	{
		OpenMeteoParser.Location texas = parser.parseGeocode(GEOCODE, "Texas");
		assertEquals(33.66, texas.getLatitude(), 0.0001);
		assertEquals(-95.56, texas.getLongitude(), 0.0001);
		assertEquals(33.66, parser.parseGeocode(GEOCODE, "us").getLatitude(), 0.0001);
		assertEquals(33.66, parser.parseGeocode(GEOCODE, "United States").getLatitude(), 0.0001);
		assertEquals(48.85, parser.parseGeocode(GEOCODE, "nowhere").getLatitude(), 0.0001);
	}

	@Test
	public void geocodeWithNoResultsIsNull()
	{
		assertNull(parser.parseGeocode("{\"generationtime_ms\":0.2}", ""));
		assertNull(parser.parseGeocode("{\"results\":[]}", ""));
		assertNull(parser.parseGeocode("garbage", ""));
		assertNull(parser.parseGeocode("{\"results\":[{\"name\":\"X\"}]}", ""));
	}
}
