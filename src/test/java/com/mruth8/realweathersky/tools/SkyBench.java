package com.mruth8.realweathersky.tools;

import com.mruth8.realweathersky.SkyEngine;
import com.mruth8.realweathersky.WeatherData;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;

/**
 * Simple nanoTime microbenchmark of the per-frame color path (target, fade, lightning).
 * Not JMH: it warms up, then reports several timed rounds so the spread is visible.
 */
public final class SkyBench
{
	private static final int WARMUP = 3_000_000;
	private static final int FRAMES = 5_000_000;
	private static final int ROUNDS = 7;
	private static volatile int sink;

	public static void main(String[] args)
	{
		long dawn = 1_794_812_463L;
		long[] sunrises = {dawn - 86400, dawn, dawn + 86400};
		long[] sunsets = {dawn - 43200, dawn + 43200, dawn + 129600};
		bench("clear day, lightning off", new WeatherData(0, 0, 0, 5, true, sunrises, sunsets), (dawn + 21600) * 1000, false);
		bench("sunrise blend, fading", new WeatherData(3, 0, 0, 90, true, sunrises, sunsets), (dawn - 600) * 1000, false);
		bench("heavy rain, lightning off", new WeatherData(65, 10, 0, 100, true, sunrises, sunsets), (dawn + 21600) * 1000, false);
		bench("thunderstorm, lightning ON", new WeatherData(95, 6, 0, 100, true, sunrises, sunsets), (dawn + 21600) * 1000, true);
	}

	private static void bench(String name, WeatherData data, long nowMs, boolean lightning)
	{
		SkyEngine e = new SkyEngine(new Random(1));
		long t = 0;
		for (int i = 0; i < WARMUP; i++)
		{
			t += 6_944_444L;
			sink ^= e.frame(nowMs, t, data, 30, lightning);
		}
		com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long alloc0 = mx.getThreadAllocatedBytes(Thread.currentThread().getId());
		double[] ns = new double[ROUNDS];
		for (int r = 0; r < ROUNDS; r++)
		{
			long s = System.nanoTime();
			for (int i = 0; i < FRAMES; i++)
			{
				t += 6_944_444L;
				sink ^= e.frame(nowMs, t, data, 30, lightning);
			}
			ns[r] = (System.nanoTime() - s) / (double) FRAMES;
		}
		long alloc = mx.getThreadAllocatedBytes(Thread.currentThread().getId()) - alloc0;
		Arrays.sort(ns);
		double med = ns[ROUNDS / 2];
		System.out.printf("%-30s median %6.1f ns/frame (min %.1f, max %.1f) | %.5f%% of a 20 ms frame (50 FPS) | %.5f%% of 6.94 ms (144 FPS) | alloc %.2f B/frame%n",
			name, med, ns[0], ns[ROUNDS - 1], med / 20_000_000.0 * 100, med / 6_944_444.0 * 100,
			alloc / (double) ((long) FRAMES * ROUNDS));
	}
}
