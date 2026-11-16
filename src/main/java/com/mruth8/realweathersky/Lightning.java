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

import java.util.Random;

/**
 * An occasional, gentle flash of the sky color during thunderstorms. It only produces a blend amount
 * and a brightened color for the sky background. Nothing here touches the scene, the ground, or players.
 *
 * <p>Photosensitivity: at most one flash every {@link #MIN_GAP_SECONDS} seconds (the common safe limit is
 * three flashes per second), a soft rise over {@link #RISE_SECONDS}, a slow decay, and a capped strength.
 */
public final class Lightning
{
	static final double MIN_GAP_SECONDS = 20;
	static final double MAX_GAP_SECONDS = 60;
	static final double FIRST_DELAY_SECONDS = 8;
	static final double RISE_SECONDS = 0.25;
	static final double DECAY_SECONDS = 0.5;
	static final double TOTAL_SECONDS = 2.2;
	/** Strongest blend toward the flash color. */
	static final double PEAK = 0.40;
	static final int FLASH_COLOR = 0xE6ECF5;

	private final Random random;
	private double nextFlashAt = Double.NaN;
	private double flashStart = Double.NaN;

	public Lightning(Random random)
	{
		this.random = random;
	}

	/**
	 * Advances the scheduler and returns the current blend toward the flash color, from 0 to {@link #PEAK}.
	 * New flashes are scheduled only while stormy. A flash already under way always finishes.
	 */
	public double update(double nowSeconds, boolean stormy)
	{
		if (!stormy)
		{
			nextFlashAt = Double.NaN;
		}
		else if (Double.isNaN(nextFlashAt) && Double.isNaN(flashStart))
		{
			nextFlashAt = nowSeconds + FIRST_DELAY_SECONDS + random.nextDouble() * (MAX_GAP_SECONDS - FIRST_DELAY_SECONDS);
		}

		if (!Double.isNaN(nextFlashAt) && nowSeconds >= nextFlashAt)
		{
			flashStart = nowSeconds;
			nextFlashAt = nowSeconds + TOTAL_SECONDS + MIN_GAP_SECONDS
				+ random.nextDouble() * (MAX_GAP_SECONDS - MIN_GAP_SECONDS);
		}

		if (Double.isNaN(flashStart))
		{
			return 0;
		}
		double t = nowSeconds - flashStart;
		if (t >= TOTAL_SECONDS)
		{
			flashStart = Double.NaN;
			return 0;
		}
		if (t < RISE_SECONDS)
		{
			return PEAK * SkyColors.smooth(t / RISE_SECONDS);
		}
		double level = PEAK * Math.exp(-(t - RISE_SECONDS) / DECAY_SECONDS);
		return level < 0.005 ? 0 : level;
	}

	public static int apply(int base, double amount)
	{
		return amount <= 0 ? base : SkyColors.lerp(base, FLASH_COLOR, amount);
	}
}
