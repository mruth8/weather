package com.mruth8.realweathersky;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkyFaderTest
{
	@Test
	public void firstCallSnapsToTarget()
	{
		SkyFader f = new SkyFader();
		assertEquals(SkyColors.RAIN, f.update(SkyColors.RAIN, 0, 30));
	}

	@Test
	public void closesAboutNinetyFivePercentInTheFadeTime()
	{
		SkyFader f = new SkyFader();
		f.update(SkyColors.NIGHT, 0, 30);
		int c = 0;
		for (int i = 0; i < 30 * 50; i++)
		{
			c = f.update(SkyColors.SNOW, 0.02, 30);
		}
		double gap = Math.abs(((c >> 16) & 0xFF) - 0xD6) / (double) (0xD6 - 0x1C);
		assertTrue("gap " + gap, gap > 0.03 && gap < 0.07);
	}

	@Test
	public void movesMonotonicallyWithoutOvershoot()
	{
		SkyFader f = new SkyFader();
		f.update(SkyColors.NIGHT, 0, 20);
		int prev = SkyColors.NIGHT & 0xFF;
		for (int i = 0; i < 6000; i++)
		{
			int blue = f.update(SkyColors.SNOW, 0.016, 20) & 0xFF;
			assertTrue(blue >= prev);
			assertTrue(blue <= (SkyColors.SNOW & 0xFF));
			prev = blue;
		}
		assertEquals(SkyColors.SNOW, f.current());
	}

	@Test
	public void changesAreGradualPerFrame()
	{
		SkyFader f = new SkyFader();
		f.update(SkyColors.NIGHT, 0, 30);
		int before = f.current();
		int after = f.update(SkyColors.SNOW, 1.0 / 60, 30);
		int step = Math.abs(((after >> 16) & 0xFF) - ((before >> 16) & 0xFF));
		assertTrue("one frame moved " + step, step <= 2);
	}

	@Test
	public void longPauseIsCappedAndNeverOvershoots()
	{
		SkyFader f = new SkyFader();
		f.update(SkyColors.NIGHT, 0, 120);
		int c = f.update(SkyColors.SNOW, 10_000, 120);
		assertTrue((c & 0xFF) < (SkyColors.SNOW & 0xFF));
		assertTrue((c & 0xFF) > (SkyColors.NIGHT & 0xFF));
	}
}
