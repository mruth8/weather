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
 * The whole per-frame color path: pick the target for the real time and conditions, ease toward it,
 * then add the optional lightning flash. Pure Java, so tests and benchmarks run exactly what the plugin runs.
 */
public final class SkyEngine
{
	private final SkyFader fader = new SkyFader();
	private final Lightning lightning;
	private long lastFrameNanos;
	private boolean hasLastFrame;

	public SkyEngine(Random random)
	{
		this.lightning = new Lightning(random);
	}

	/**
	 * @param nowMillis       wall-clock time, used to compare against sunrise and sunset
	 * @param frameNanos      a monotonic clock, used for fade and flash timing
	 * @param fadeSeconds     time for a weather change to be about 95 percent complete
	 * @param lightningOn     whether the optional flash is enabled
	 * @return the RGB color (0xRRGGBB) to give the client as the skybox color
	 */
	public int frame(long nowMillis, long frameNanos, WeatherData data, double fadeSeconds, boolean lightningOn)
	{
		double dt = hasLastFrame ? (frameNanos - lastFrameNanos) / 1e9 : 0;
		lastFrameNanos = frameNanos;
		hasLastFrame = true;

		int target = SkyColors.target(Math.floorDiv(nowMillis, 1000L), data);
		int base = fader.update(target, dt, fadeSeconds);

		boolean stormy = lightningOn && SkyColors.isThunderstorm(data);
		double flash = lightning.update(frameNanos / 1e9, stormy);
		return Lightning.apply(base, flash);
	}

	/** The faded color with no flash applied. */
	public int baseColor()
	{
		return fader.current();
	}
}
