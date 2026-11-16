package com.mruth8.realweathersky;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LightningTest
{
	private static final double FRAME = 1.0 / 60;

	private static final WeatherData STORM = new WeatherData(95, 4, 0, 100, true, new long[]{0}, new long[]{1});
	private static final WeatherData CALM = new WeatherData(3, 0, 0, 100, true, new long[]{0}, new long[]{1});

	@Test
	public void noFlashesWhenNotStormy()
	{
		Lightning l = new Lightning(new Random(1));
		for (int i = 0; i < 60 * 600; i++)
		{
			assertEquals(0.0, l.update(i * FRAME, false), 0);
		}
	}

	@Test
	public void flashesAreInfrequentGentleAndSoft()
	{
		Lightning l = new Lightning(new Random(7));
		List<Double> starts = new ArrayList<>();
		double prev = 0;
		double max = 0;
		double maxStep = 0;
		for (int i = 0; i < 60 * 3600; i++)
		{
			double t = i * FRAME;
			double v = l.update(t, true);
			if (prev == 0 && v > 0)
			{
				starts.add(t);
			}
			max = Math.max(max, v);
			maxStep = Math.max(maxStep, Math.abs(v - prev));
			prev = v;
		}
		assertTrue("flashes in an hour: " + starts.size(), starts.size() >= 30 && starts.size() <= 180);
		for (int i = 1; i < starts.size(); i++)
		{
			assertTrue("gap " + (starts.get(i) - starts.get(i - 1)), starts.get(i) - starts.get(i - 1) >= 20);
		}
		assertTrue(max <= Lightning.PEAK + 1e-9);
		assertTrue("peak reached", max > Lightning.PEAK * 0.95);
		assertTrue("biggest per-frame jump " + maxStep, maxStep < 0.06);
	}

	@Test
	public void firstFlashIsNotImmediate()
	{
		Lightning l = new Lightning(new Random(3));
		for (int i = 0; i < 60 * 7; i++)
		{
			assertEquals(0.0, l.update(i * FRAME, true), 0);
		}
	}

	@Test
	public void aFlashThatHasStartedFinishesWhenTheStormEnds()
	{
		Lightning l = new Lightning(new Random(5));
		double t = 0;
		while (l.update(t, true) == 0)
		{
			t += FRAME;
		}
		double during = l.update(t + 0.3, false);
		assertTrue(during > 0);
		double end = 0;
		for (int i = 0; i < 60 * 5; i++)
		{
			end = l.update(t + 0.3 + i * FRAME, false);
		}
		assertEquals(0.0, end, 0);
	}

	@Test
	public void applyOnlyBrightensTheSkyColor()
	{
		assertEquals(SkyColors.RAIN, Lightning.apply(SkyColors.RAIN, 0));
		int flashed = Lightning.apply(SkyColors.RAIN, Lightning.PEAK);
		assertTrue(((flashed >> 16) & 0xFF) > ((SkyColors.RAIN >> 16) & 0xFF));
		assertTrue((flashed & 0xFF) <= 0xFF);
	}

	@Test
	public void engineIgnoresLightningWhenOffAndInCalmWeather()
	{
		SkyEngine off = new SkyEngine(new Random(2));
		SkyEngine calm = new SkyEngine(new Random(2));
		long nowMs = 1_794_830_000_000L;
		for (int i = 0; i < 60 * 300; i++)
		{
			long ns = (long) (i * FRAME * 1e9);
			assertEquals(off.frame(nowMs, ns, STORM, 30, false), off.baseColor());
			assertEquals(calm.frame(nowMs, ns, CALM, 30, true), calm.baseColor());
		}
	}

	@Test
	public void engineFlashesDuringStormWhenOn()
	{
		SkyEngine e = new SkyEngine(new Random(9));
		long nowMs = 1_794_830_000_000L;
		boolean brighter = false;
		for (int i = 0; i < 60 * 300; i++)
		{
			long ns = (long) (i * FRAME * 1e9);
			int c = e.frame(nowMs, ns, STORM, 30, true);
			if (c != e.baseColor())
			{
				brighter = true;
			}
		}
		assertTrue(brighter);
	}
}
