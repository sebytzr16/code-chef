# Stock Widget

A clean, Material 3 Android app with a **home-screen widget** that tracks the stocks you
own. It works **out of the box** — no API key, no setup. You just open the app, search for
the stocks you want, and they appear in the widget. Each widget entry shows the name and
price (big), the day's open/close (small), and a **line chart that's green when the price
is at/above the open and red when it's below.**

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Widgets** | A multi-stock list widget **and** a compact single-stock widget (pick the stock when you place it). Dark-grey background; each entry shows the name + price (big), open/close (small), and a green/red line chart on the side. Charts rendered to a `Bitmap` |
| **Data** | Yahoo Finance public endpoints — **no API key required** |
| **Refresh** | `WorkManager` periodic refresh + a refresh button on the widget |
| **Min SDK** | 26 (Android 8.0) · **Target SDK** 35 |

## Using it

1. Open the project in **Android Studio** (Ladybug or newer) and let Gradle sync.
2. Run the app on a device/emulator — no configuration needed.
3. The app opens on a **search box**. Type a name or ticker (e.g. `AAPL`, `Tesla`,
   `MSFT`) and tap a result to add it.
4. Tap any stock to open a **detail screen** with open / previous close / day high / low /
   change — served instantly from the last refresh, **no network needed to open it**.
5. **Long-press your home screen → Widgets → Stock Widget** and choose either the **list
   widget** or the **single-stock widget** (the latter asks which stock to show).

## How the data & chart work

The app uses Yahoo Finance's public **chart endpoint**
(`v8/finance/chart/{symbol}?range=1d&interval=5m`), which needs no key and returns, in a
single call, the live price, the day's open, the previous close, and the full intraday
series used to draw the sparkline. A browser-like `User-Agent` is sent because these are
unofficial endpoints. The opening price is drawn as a faint baseline, and the line/fill
color is **green if current ≥ open, red otherwise**.

A full snapshot + the intraday series are persisted per symbol on every refresh, so the
app and both widgets render complete data **instantly and offline** when opened.

> Note: these are unofficial endpoints. They've been stable for personal use for years, but
> Yahoo could change them without notice.

## Project layout

```
app/src/main/java/com/stockwidget/app/
├── MainActivity.kt              # Compose host
├── StockApplication.kt          # schedules periodic refresh
├── data/
│   ├── model/Models.kt          # Stock, StockQuote, PricePoint, SearchResult
│   ├── remote/                  # Yahoo Finance Retrofit client
│   ├── PreferencesStore.kt      # SharedPreferences + Gson persistence
│   └── StockRepository.kt       # quotes + history orchestration
├── ui/                          # Compose screens (search-first), ViewModel, Sparkline
└── widget/
    ├── StockWidgetProvider.kt   # the list AppWidget
    ├── StockWidgetService.kt    # RemoteViews list factory
    ├── SingleStockWidgetProvider.kt + SingleStockConfigActivity.kt
    ├── ChartBitmap.kt           # draws the line chart to a Bitmap
    └── WidgetUpdater.kt
```

## Notes

- The widget and the in-app list share the same color logic and chart renderer for a
  consistent look.
- Works for US stocks, ETFs, indices and crypto symbols that Yahoo supports.
