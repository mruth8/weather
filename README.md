# Real Weather Sky

A RuneLite plugin that colors the in-game sky background from the real weather and time of day in a city you choose.

- Night, sunrise, clear, cloudy, rain, snow, and sunset each have a color, and the sky fades smoothly between them.
- Rain gets darker as it gets heavier.
- An optional, gentle lightning flash brightens the sky color during thunderstorms.
- It only changes the sky background color. It adds no overlay and does not tint the ground, players, or anything else.

| State | Color |
| --- | --- |
| Night | `#1C2433` |
| Sunrise | `#CFA27C` |
| Clear | `#8AA6B6` |
| Cloudy | `#A4A49C` |
| Rain | `#6A7076` (up to 30% darker when heavy) |
| Snow | `#D6DDE4` |
| Sunset | `#C48462` |

Around sunrise the sky blends night, sunrise, then the day color over about 80 minutes. Around sunset it blends the day color, sunset, then night.

## Setup

1. Turn off RuneLite's built-in **Skybox** plugin. Both plugins set the sky color every frame, so running both makes them fight over it.
2. If you use the **117 HD** plugin, open its settings and set **Default sky** to **RuneLite Skybox**. This plugin cannot change other plugins' settings, so you do this yourself. It only works in areas that do not have their own sky.
3. Open this plugin's settings, type your **City** (for example `London` or `Paris, France`), then turn on **Enable weather lookup**.

Nothing happens until you do step 3. The lookup is off by default.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| City | empty | The city to follow. Text after a comma, such as a country or region, helps pick the right place. |
| Enable weather lookup | off | Turns the lookup on. See the privacy note below. |
| Fade time (seconds) | 30 | How long a change of weather takes to blend in. |
| Lightning flash | off | Brief, gentle flash of the sky color during thunderstorms, at most once every 20 to 60 seconds. Sky only. Leave it off if you are sensitive to flashing. |

## Privacy and network use

When the lookup is on, the plugin talks to the free [Open-Meteo](https://open-meteo.com) service, a third party that can see your IP address.

- Once per city change, it sends the city text to Open-Meteo's geocoding API to get coordinates.
- Every 10 minutes while you are logged in, it asks Open-Meteo's forecast API for the current conditions and sunrise and sunset times at those coordinates, rounded to two decimals (about 1 km).
- It sends no account, character, or world data. It pauses while you are logged out, and fetches again when you log in.
- Requests use RuneLite's own HTTP client, off the game thread. If a request fails, the last sky color stays.
- If the city cannot be found, the plugin says so once in the chat box and stops asking until you change the city.

Weather data by [Open-Meteo.com](https://open-meteo.com/), licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), free for non-commercial use.

## Performance

The per-frame work is a handful of arithmetic operations with no allocation. On a development machine the whole color path measured about 35 to 50 ns per frame (see the pull request for the numbers). The only network traffic is one small request every 10 minutes.

## Building and testing

```
./gradlew build
```

Unit tests cover the color selection, the fade, the lightning timing, and the JSON parsing. To run the client with the plugin loaded, run `RealWeatherSkyPluginTest.main` from your IDE with the `--developer-mode` argument.

## Disclaimer

This is an unofficial fan project and is not affiliated with, endorsed by, or sponsored by Jagex Ltd. or RuneLite. Old School RuneScape and RuneScape are trademarks of Jagex Ltd.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
