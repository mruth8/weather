package com.mruth8.realweathersky;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import okhttp3.OkHttpClient;

/**
 * Manual smoke test, not run by the build. Wires the real plugin class to a stand-in Client and makes the real
 * Open-Meteo calls through the real OkHttp stack, logged in, so it needs network access.
 * Usage: LiveSmokeRun "Paris, France" [lightning]
 */
public final class LiveSmokeRun
{
	static final class TestConfig implements RealWeatherSkyConfig
	{
		String city;
		boolean lightning;

		@Override
		public String city()
		{
			return city;
		}

		@Override
		public boolean lookupEnabled()
		{
			return true;
		}

		@Override
		public int fadeSeconds()
		{
			return 30;
		}

		@Override
		public boolean lightning()
		{
			return lightning;
		}
	}

	public static void main(String[] args) throws Exception
	{
		TestConfig config = new TestConfig();
		config.city = args[0];
		config.lightning = args.length > 1;

		List<Integer> skybox = new ArrayList<>();
		List<String> chat = new ArrayList<>();
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, a) ->
			{
				switch (method.getName())
				{
					case "isClientThread":
						return true;
					case "getGameState":
						return GameState.LOGGED_IN;
					case "setSkyboxColor":
						skybox.add((Integer) a[0]);
						return null;
					case "addChatMessage":
						chat.add((String) a[2]);
						return null;
					default:
						return method.getReturnType() == boolean.class ? (Object) false
							: method.getReturnType() == int.class ? (Object) 0 : null;
				}
			});
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
		Injector injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(client);
				bind(RealWeatherSkyConfig.class).toInstance(config);
				bind(OkHttpClient.class).toInstance(new OkHttpClient());
				bind(Gson.class).toInstance(new Gson());
				bind(ScheduledExecutorService.class).toInstance(executor);
			}
		});
		RealWeatherSkyPlugin plugin = injector.getInstance(RealWeatherSkyPlugin.class);
		plugin.startUp();
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(login);

		long deadline = System.currentTimeMillis() + 20_000;
		BeforeRender frame = new BeforeRender();
		while (skybox.isEmpty() && chat.isEmpty() && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(100);
			plugin.onBeforeRender(frame);
		}
		if (skybox.isEmpty())
		{
			System.out.println("RESULT: no sky color was set. Chat messages: " + chat);
		}
		else
		{
			for (int i = 0; i < 59; i++)
			{
				Thread.sleep(16);
				plugin.onBeforeRender(frame);
			}
			System.out.printf("RESULT: city=%s first sky color=#%06X after %d frames, last=#%06X, chat=%s%n",
				config.city, skybox.get(0), skybox.size(), skybox.get(skybox.size() - 1), chat);
		}
		plugin.shutDown();
		Thread.sleep(200);
		System.out.printf("After shutDown the last setSkyboxColor value was #%06X%n", skybox.isEmpty() ? 0 : skybox.get(skybox.size() - 1));
		executor.shutdownNow();
		System.exit(0);
	}
}
