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
 * Eases the displayed color toward a target. Keeps float channels so slow fades never stall on rounding.
 */
public final class SkyFader
{
	/** Longest frame gap honoured, so a long pause (minimised window, hop) cannot cause a jump past the target. */
	static final double MAX_DT_SECONDS = 5.0;

	private float r;
	private float g;
	private float b;
	private boolean started;

	/**
	 * Moves toward the target. After {@code fadeSeconds} about 95 percent of the gap is closed.
	 * The first call snaps to the target so a fresh login does not fade up from black.
	 */
	public int update(int target, double dtSeconds, double fadeSeconds)
	{
		float tr = (target >> 16) & 0xFF;
		float tg = (target >> 8) & 0xFF;
		float tb = target & 0xFF;
		if (!started)
		{
			started = true;
			r = tr;
			g = tg;
			b = tb;
		}
		else
		{
			double dt = Math.min(Math.max(dtSeconds, 0), MAX_DT_SECONDS);
			float a = (float) (1.0 - Math.exp(-3.0 * dt / Math.max(fadeSeconds, 0.1)));
			r += (tr - r) * a;
			g += (tg - g) * a;
			b += (tb - b) * a;
		}
		return current();
	}

	public int current()
	{
		return (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b);
	}

	public boolean isStarted()
	{
		return started;
	}
}
