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

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@PluginDescriptor(
	name = "Real Weather Sky",
	description = "Colors the sky from the real weather and time of day in a city you choose",
	tags = {"weather", "sky", "skybox", "time", "day", "night", "rain", "snow"}
)
public class RealWeatherSkyPlugin extends Plugin
{
	private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search";
	private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
	private static final int REFRESH_MINUTES = 10;
	/** On login, skip the fetch if the data is younger than this. */
	private static final long FRESH_ENOUGH_MS = TimeUnit.MINUTES.toMillis(2);
	private static final long MAX_BODY_BYTES = 256 * 1024;
	private static final int MAX_CITY_CHARS = 100;
	/** Delay before one more try when no data has ever arrived. */
	private static final long FIRST_RETRY_SECONDS = 60;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RealWeatherSkyConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	private OkHttpClient http;
	private OpenMeteoParser parser;
	private SkyEngine engine;
	private ScheduledFuture<?> refreshTask;

	private final AtomicBoolean inFlight = new AtomicBoolean();
	private volatile Call currentCall;
	private volatile boolean running;
	private volatile boolean loggedIn;

	private volatile boolean lookupEnabled;
	private volatile boolean lightningEnabled;
	private volatile int fadeSeconds;
	private volatile String city = "";

	private volatile WeatherData weather;
	private volatile long weatherFetchedMs;
	private volatile OpenMeteoParser.Location location;
	private volatile String locationFor = "";
	private volatile String notFoundFor = "";

	/** Client thread only: true while we have set a skybox color that we must hand back on shutdown. */
	private boolean applied;

	@Provides
	RealWeatherSkyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RealWeatherSkyConfig.class);
	}

	@Override
	protected void startUp()
	{
		running = true;
		engine = new SkyEngine(new Random());
		parser = new OpenMeteoParser(gson);
		http = okHttpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build();
		readConfig();
		weather = null;
		location = null;
		locationFor = "";
		notFoundFor = "";

		clientThread.invoke(() -> loggedIn = client.getGameState() == GameState.LOGGED_IN);
		refreshTask = executor.scheduleWithFixedDelay(this::refresh, 0, REFRESH_MINUTES, TimeUnit.MINUTES);
	}

	@Override
	protected void shutDown()
	{
		running = false;
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
		Call call = currentCall;
		if (call != null)
		{
			call.cancel();
		}
		inFlight.set(false);
		weather = null;
		clientThread.invoke(this::releaseSky);
	}

	private void readConfig()
	{
		lookupEnabled = config.lookupEnabled();
		lightningEnabled = config.lightning();
		fadeSeconds = config.fadeSeconds();
		city = config.city() == null ? "" : config.city().trim();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!RealWeatherSkyConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		readConfig();
		if (!lookupEnabled)
		{
			clientThread.invoke(this::releaseSky);
		}
		else if ("city".equals(event.getKey()) || "lookupEnabled".equals(event.getKey()))
		{
			executor.execute(this::refresh);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		boolean nowLoggedIn = event.getGameState() == GameState.LOGGED_IN;
		boolean justLoggedIn = nowLoggedIn && !loggedIn;
		loggedIn = nowLoggedIn;
		if (justLoggedIn && System.currentTimeMillis() - weatherFetchedMs > FRESH_ENOUGH_MS)
		{
			executor.execute(this::refresh);
		}
	}

	/** Runs once per frame on the client thread. No allocation, no config proxy calls, no network. */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		WeatherData data = weather;
		if (!lookupEnabled || !loggedIn || data == null)
		{
			return;
		}
		int rgb = engine.frame(System.currentTimeMillis(), System.nanoTime(), data, fadeSeconds, lightningEnabled);
		client.setSkyboxColor(rgb);
		applied = true;
	}

	/** Client thread: give the sky back to the game (or to another plugin) by clearing our color. */
	private void releaseSky()
	{
		if (applied)
		{
			client.setSkyboxColor(0);
			applied = false;
		}
	}

	// Everything below runs on the executor or OkHttp threads, never the client thread.

	private void refresh()
	{
		String typed = city;
		if (!running || !lookupEnabled || !loggedIn || typed.isEmpty() || typed.equals(notFoundFor))
		{
			return;
		}
		if (!inFlight.compareAndSet(false, true))
		{
			return;
		}
		OpenMeteoParser.Location known = location;
		if (known != null && typed.equals(locationFor))
		{
			fetchForecast(known, typed);
		}
		else
		{
			fetchGeocode(typed);
		}
	}

	private void fetchGeocode(String typed)
	{
		String[] parts = (typed.length() > MAX_CITY_CHARS ? typed.substring(0, MAX_CITY_CHARS) : typed).split(",", 2);
		String name = parts[0].trim();
		String hint = parts.length > 1 ? parts[1].trim() : "";
		if (name.isEmpty())
		{
			inFlight.set(false);
			return;
		}
		HttpUrl url = HttpUrl.parse(GEOCODE_URL).newBuilder()
			.addQueryParameter("name", name)
			.addQueryParameter("count", hint.isEmpty() ? "1" : "10")
			.addQueryParameter("language", "en")
			.addQueryParameter("format", "json")
			.build();
		call(url, typed, body ->
		{
			OpenMeteoParser.Location found = parser.parseGeocode(body, hint);
			if (found == null)
			{
				notFoundFor = typed;
				tellNotFound(typed);
				return false;
			}
			location = found;
			locationFor = typed;
			// Keep the in-flight flag and go straight on to the forecast.
			fetchForecast(found, typed);
			return true;
		});
	}

	private void fetchForecast(OpenMeteoParser.Location loc, String typed)
	{
		HttpUrl url = HttpUrl.parse(FORECAST_URL).newBuilder()
			.addQueryParameter("latitude", Double.toString(loc.getLatitude()))
			.addQueryParameter("longitude", Double.toString(loc.getLongitude()))
			.addQueryParameter("current", "weather_code,precipitation,snowfall,cloud_cover,is_day")
			.addQueryParameter("daily", "sunrise,sunset")
			.addQueryParameter("timezone", "auto")
			.addQueryParameter("timeformat", "unixtime")
			.addQueryParameter("past_days", "1")
			.addQueryParameter("forecast_days", "2")
			.build();
		call(url, typed, body ->
		{
			WeatherData parsed = parser.parseForecast(body);
			if (parsed == null)
			{
				log.debug("Open-Meteo forecast response was not in the expected shape; keeping the last color");
				return false;
			}
			weather = parsed;
			weatherFetchedMs = System.currentTimeMillis();
			return false;
		});
	}

	private interface BodyHandler
	{
		/** @return true if the handler started another call, which then owns releasing the in-flight flag */
		boolean handle(String body);
	}

	/**
	 * Sends one GET with enqueue so the calling thread never waits. The in-flight flag is released here unless the
	 * handler chains another call, in which case that call releases it.
	 */
	private void call(HttpUrl url, String typed, BodyHandler handler)
	{
		Request request = new Request.Builder().url(url).get().build();
		Call call = http.newCall(request);
		currentCall = call;
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Open-Meteo request failed; keeping the last color: {}", e.toString());
				done(typed);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				boolean chained = false;
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null || !running)
					{
						log.debug("Open-Meteo returned HTTP {}; keeping the last color", r.code());
						return;
					}
					ResponseBody peeked = r.peekBody(MAX_BODY_BYTES);
					chained = handler.handle(peeked.string());
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Could not read Open-Meteo response; keeping the last color: {}", e.toString());
				}
				finally
				{
					if (!chained)
					{
						done(typed);
					}
				}
			}
		});
	}

	private void done(String typed)
	{
		inFlight.set(false);
		// If the city was edited while this call was running, start over for the new text.
		if (running && !typed.equals(city))
		{
			executor.execute(this::refresh);
		}
		else if (running && weather == null && !typed.equals(notFoundFor))
		{
			// Nothing to show yet, so do not make the player wait a whole cycle after one failed request.
			executor.schedule(this::refresh, FIRST_RETRY_SECONDS, TimeUnit.SECONDS);
		}
	}

	private void tellNotFound(String typed)
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"Real Weather Sky: could not find a city called \"" + typed + "\".", null);
			}
		});
	}
}
