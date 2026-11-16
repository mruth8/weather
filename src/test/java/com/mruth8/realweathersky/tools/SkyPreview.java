package com.mruth8.realweathersky.tools;

import com.mruth8.realweathersky.Lightning;
import com.mruth8.realweathersky.SkyColors;
import com.mruth8.realweathersky.SkyEngine;
import com.mruth8.realweathersky.SkyFader;
import com.mruth8.realweathersky.WeatherData;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Renders the colors the plugin would give the sky, using the real engine classes. Not part of the plugin.
 * Usage: SkyPreview OUT_DIR
 */
public final class SkyPreview
{
	private static final long SUNRISE = 6 * 3600 + 30 * 60;
	private static final long SUNSET = 18 * 3600 + 30 * 60;
	private static final long DAY0 = 1_794_700_800L;

	private static WeatherData weather(int code, double mm, double snow, int cloud)
	{
		return new WeatherData(code, mm, snow, cloud, true,
			new long[]{DAY0 + SUNRISE - 86400, DAY0 + SUNRISE, DAY0 + SUNRISE + 86400},
			new long[]{DAY0 + SUNSET - 86400, DAY0 + SUNSET, DAY0 + SUNSET + 86400});
	}

	public static void main(String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		File out = new File(args[0]);
		out.mkdirs();
		dayStrips(new File(out, "1-sky-over-a-day.png"));
		fade(new File(out, "2-fade-rain-to-clear.png"));
		lightning(new File(out, "3-lightning-flash-frames.png"));
		fadeDawn(new File(out, "4-night-to-sunrise-to-day.png"));
	}

	private static Graphics2D g(BufferedImage img)
	{
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		g.setColor(new Color(0x20, 0x20, 0x20));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		return g;
	}

	private static void label(Graphics2D g, String s, int x, int y)
	{
		g.setColor(Color.WHITE);
		g.drawString(s, x, y);
	}

	/** One row per condition, 24 hours left to right, with the sun times marked. */
	private static void dayStrips(File f) throws Exception
	{
		Object[][] rows = {
			{"Clear (code 0, 0% cloud)", weather(0, 0, 0, 0)},
			{"Partly cloudy (code 2, 50%)", weather(2, 0, 0, 50)},
			{"Overcast (code 3, 100%)", weather(3, 0, 0, 100)},
			{"Light rain (code 61, 1 mm/h)", weather(61, 1, 0, 100)},
			{"Heavy rain (code 65, 10 mm/h)", weather(65, 10, 0, 100)},
			{"Snow (code 73)", weather(73, 0, 0.8, 100)},
			{"Thunderstorm (code 95, no flash)", weather(95, 6, 0, 100)},
		};
		int w = 1440;
		int rowH = 56;
		int top = 52;
		BufferedImage img = new BufferedImage(w + 260, top + rows.length * (rowH + 22) + 34, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = g(img);
		label(g, "Sky color over 24 hours (sunrise 06:30, sunset 18:30 city time; 40 min blends each side)", 8, 22);
		for (int r = 0; r < rows.length; r++)
		{
			int y = top + r * (rowH + 22);
			WeatherData d = (WeatherData) rows[r][1];
			label(g, (String) rows[r][0], 8, y - 6);
			for (int m = 0; m < 1440; m++)
			{
				g.setColor(new Color(SkyColors.target(DAY0 + m * 60L, d)));
				g.fillRect(m, y, 1, rowH);
			}
			g.setColor(Color.WHITE);
			g.drawRect(0, y, 1439, rowH);
			// color of the right-hand legend: noon color
			g.setColor(new Color(SkyColors.target(DAY0 + 12 * 3600, d)));
			g.fillRect(w + 10, y, 80, rowH);
			label(g, String.format("noon #%06X", SkyColors.target(DAY0 + 12 * 3600, d)), w + 98, y + 32);
		}
		int ay = top + rows.length * (rowH + 22);
		for (int h = 0; h <= 24; h += 3)
		{
			label(g, String.format("%02d:00", h), h * 60 - (h == 24 ? 34 : 0), ay);
		}
		ImageIO.write(img, "png", f);
	}

	/** Frame sequence: rain to clear with a 30 s fade, sampled every 5 s. */
	private static void fade(File f) throws Exception
	{
		long noon = (DAY0 + 12 * 3600) * 1000;
		WeatherData rain = weather(65, 10, 0, 100);
		WeatherData clear = weather(0, 0, 0, 0);
		SkyEngine e = new SkyEngine(new Random(1));
		int cols = 13;
		int cw = 100;
		BufferedImage img = new BufferedImage(cols * cw + 16, 150, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = g(img);
		label(g, "Heavy rain turns to clear sky at t=0 s, fade time 30 s (real engine, 60 FPS frames, sampled every 5 s)", 8, 20);
		long ns = 0;
		int col = 0;
		for (int frame = 0; frame <= 60 * 60; frame++)
		{
			ns = frame * 16_666_667L;
			WeatherData d = frame < 60 * 5 ? rain : clear;
			int c = e.frame(noon, ns, d, 30, false);
			if (frame % 300 == 0 && col < cols)
			{
				int x = 8 + col * cw;
				g.setColor(new Color(c));
				g.fillRect(x, 34, cw - 4, 70);
				label(g, String.format("t=%ds", frame / 60 - 5), x + 4, 124);
				label(g, String.format("#%06X", c), x + 4, 142);
				col++;
			}
		}
		ImageIO.write(img, "png", f);
	}

	/** Night, dawn, sunrise, and morning for clear weather, every 10 minutes from 05:50 to 07:50. */
	private static void fadeDawn(File f) throws Exception
	{
		WeatherData d = weather(1, 0, 0, 10);
		int n = 13;
		int cw = 100;
		BufferedImage img = new BufferedImage(n * cw + 16, 150, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = g(img);
		label(g, "Clear morning, every 10 min from 05:30 (sunrise 06:30): night -> sunrise -> day", 8, 20);
		for (int i = 0; i < n; i++)
		{
			long t = DAY0 + 5 * 3600 + 30 * 60 + i * 600L;
			int c = SkyColors.target(t, d);
			int x = 8 + i * cw;
			g.setColor(new Color(c));
			g.fillRect(x, 34, cw - 4, 70);
			label(g, String.format("%02d:%02d", t % 86400 / 3600, t % 3600 / 60), x + 4, 124);
			label(g, String.format("#%06X", c), x + 4, 142);
		}
		ImageIO.write(img, "png", f);
	}

	/** One gentle flash at 20 FPS over 2.4 s, shown as swatches with the blend amount, over a thunderstorm. */
	private static void lightning(File f) throws Exception
	{
		WeatherData storm = weather(95, 6, 0, 100);
		long noon = (DAY0 + 12 * 3600) * 1000;
		SkyEngine e = new SkyEngine(new Random(11));
		int[] frames = new int[48];
		long start = -1;
		int idx = 0;
		int cols = 24;
		int cw = 54;
		BufferedImage img = new BufferedImage(cols * cw + 16, 230, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = g(img);
		label(g, "Thunderstorm sky with lightning on: one flash, a frame every 0.1 s (sky color only, nothing else changes)", 8, 20);
		for (int frame = 0; frame < 60 * 120 && idx < frames.length; frame++)
		{
			long ns = frame * 16_666_667L;
			int c = e.frame(noon, ns, storm, 30, true);
			boolean flashing = c != e.baseColor();
			if (start < 0 && flashing)
			{
				start = frame;
			}
			if (start >= 0 && (frame - start) % 6 == 0)
			{
				frames[idx++] = c;
			}
		}
		for (int i = 0; i < Math.min(idx, 24); i++)
		{
			int x = 8 + i * cw;
			g.setColor(new Color(frames[i]));
			g.fillRect(x, 34, cw - 3, 90);
			label(g, String.format("%.1fs", i * 0.1), x + 2, 144);
		}
		// brightness plot
		g.setColor(new Color(0x303030));
		g.fillRect(8, 160, cols * cw, 56);
		g.setColor(new Color(0xE6ECF5));
		int prevX = -1;
		int prevY = -1;
		for (int i = 0; i < Math.min(idx, 24); i++)
		{
			int c = frames[i];
			double lum = (0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF)) / 255.0;
			int x = 8 + i * cw + cw / 2;
			int y = (int) (214 - lum * 52);
			if (prevX >= 0)
			{
				g.drawLine(prevX, prevY, x, y);
			}
			prevX = x;
			prevY = y;
		}
		label(g, "brightness", 12, 176);
		ImageIO.write(img, "png", f);
	}
}
