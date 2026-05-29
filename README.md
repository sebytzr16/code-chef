# Stock Widget

A clean, Material 3 Android app with a **home-screen widget** that tracks the stocks you
own. Each row shows the symbol, the live price, the day's open and previous close, and a
**line chart on the right that's green when the price is at/above the open and red when
it's below.**

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 (dynamic color on Android 12+) |
| **Widgets** | A multi-stock list widget **and** a compact single-stock widget (pick the stock when you place it). Charts rendered to a `Bitmap` |
| **Data** | [Finnhub](https://finnhub.io) `/quote` endpoint (free tier) |
| **Refresh** | `WorkManager` periodic refresh + a refresh button on the widget |
| **Min SDK** | 26 (Android 8.0) · **Target SDK** 35 |

## Setup

1. **Get a free Finnhub API key** — sign up at <https://finnhub.io>, copy the key from your
   dashboard.
2. Open the project in **Android Studio** (Ladybug or newer) and let Gradle sync.
3. Run the app on a device/emulator.
4. In the app, open **Settings** (gear icon) and paste your API key.
5. Tap **Add stock**, search by name/ticker (search needs the API key) or type a ticker
   (e.g. `AAPL`, `MSFT`, `TSLA`), and add it.
6. Tap any stock to open a **detail screen** with open / previous close / day high / low /
   change — all served instantly from the last refresh, **no network needed to open it**.
7. **Long-press your home screen → Widgets → Stock Widget** and choose either the **list
   widget** or the **single-stock widget** (the latter asks which stock to show).

## How the chart works

Finnhub's intraday *candle* endpoint is premium-only, so the app builds the sparkline
itself: on every refresh it samples the current price and stores a rolling, same-day
history per symbol (`PreferencesStore`). Over the trading day the line fills in. The
opening price is drawn as a faint baseline, and the line/fill color is **green if current ≥
open, red otherwise** — matching the up/down accent used throughout the app.

## Project layout

```
app/src/main/java/com/stockwidget/app/
├── MainActivity.kt              # Compose host
├── StockApplication.kt          # schedules periodic refresh
├── data/
│   ├── model/Models.kt          # Stock, StockQuote, PricePoint
│   ├── remote/                  # Finnhub Retrofit client
│   ├── PreferencesStore.kt      # SharedPreferences + Gson persistence
│   └── StockRepository.kt       # quotes + history orchestration
├── ui/                          # Compose screens, ViewModel, Sparkline
└── widget/
    ├── StockWidgetProvider.kt   # the AppWidget
    ├── StockWidgetService.kt    # RemoteViews list factory
    ├── ChartBitmap.kt           # draws the line chart to a Bitmap
    └── WidgetUpdater.kt
```

## Notes

- Finnhub's free tier covers US stocks and ~60 requests/minute — plenty for a personal
  watchlist.
- The widget and the in-app list share the same color logic and chart renderer for a
  consistent look.
