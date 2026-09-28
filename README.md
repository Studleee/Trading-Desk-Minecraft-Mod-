# Trading Desk

A trading desk and wall chart screens for Minecraft 26.3 (Fabric), connected to your OANDA account.

- **Trading desk:** right-click it to open the terminal: balance, NAV, unrealized P/L, and margin across the top; a watchlist of live bid/ask prices; a market order ticket (units, optional stop loss and take profit); and your open trades with close buttons. Every order and every close asks you to confirm first. The desk's monitor shows your NAV and P/L over a chart of the market you last picked.
- **Chart screens:** hang them on a wall like paintings. Screens touching side by side or stacked (facing the same way) merge into one big chart, up to 12 x 12. Right-click to pick the market, the timeframe (1m, 5m, 15m, 1h, 4h, 1D), and whether your open trades show on it (entry line with P/L, stop loss, take profit). The newest candle follows the live price.

- **Buy and sell buttons:** green and red stone buttons. Turn on **Master chart** in a chart's settings (it shows a gold MASTER tag), and pressing a button buys or sells that chart's market using the units, stop loss, and take profit last typed in the desk's order ticket. The nearest master chart within 32 blocks is used. Every press asks you to confirm, and the result shows above your hotbar. They still give a redstone pulse like any button.

## Connecting your account

1. Launch the game once. It creates `run/config/tradingdesk.json` (in a normal install, `.minecraft/config/tradingdesk.json`).
2. On OANDA's website, generate an API token (My Account > Manage API Access). Practice and live accounts have separate tokens.
3. Paste it into the config and save:

```json
{
  "environment": "practice",
  "practiceToken": "paste your practice token here",
  "liveToken": "",
  "accountId": "",
  "watchlist": ["EUR_USD", "GBP_USD", "USD_JPY", "XAU_USD"],
  "defaultUnits": 1000
}
```

4. At the desk, press **Reload**.

- `environment` is `practice` or `live`. It stays on practice unless you write `live`. The terminal shows a green PRACTICE or red LIVE badge, and live confirmations say so in red.
- `accountId` blank uses the first account on the token. Set it to pick a sub-account, like `101-001-1234567-002`.
- The token stays in that file on your computer and is only ever sent to OANDA. It isn't in your world save and isn't sent to a server, so on a multiplayer server other players can't see it or trade on your account. They see charts with their own accounts, if they have the mod set up.

You don't need to open your world to LAN; the mod talks to OANDA directly.

**Crafting**

```
Trading desk:  R G R     R = redstone, G = glass pane
               P P P     P = dark oak planks
               I   I     I = iron ingot

Chart screen (makes 2):  N G N     N = iron nugget
                         G R G
                         N G N

Buy button:   stone button + green dye (shapeless)
Sell button:  stone button + red dye (shapeless)
```

## Quick start

Double-click `run.bat`. Everything is in the **Trading Desk** creative tab.

## Where things live

```
src/main/java/com/tradingdesk/
  block/TradingDeskBlock.java        the desk; right-click opens the terminal
  block/ChartScreenBlock.java        the wall screen
  block/ChartScreenBlockEntity.java  what a chart shows (saved in the world)
  block/ChartGroup.java              how touching screens merge into one chart
  network/                           the message a player sends when changing a chart's settings

src/client/java/com/tradingdesk/client/
  oanda/OandaConfig.java             reads config/tradingdesk.json
  oanda/OandaApi.java                calls OANDA's v20 REST API
  oanda/OandaData.java               background polling, only for what's on screen; orders and closes
  render/ChartPainter.java           draws candlestick charts
  render/ChartScreenRenderer.java    charts on wall screens
  render/TradingDeskRenderer.java    the desk's monitor
  screen/DeskScreen.java             the trading terminal
  screen/ChartSettingsScreen.java    picking a chart's market and timeframe
```

Textures, models, recipes, and names come from `tools/gen-assets.ps1`:

```
powershell -ExecutionPolicy Bypass -File tools\gen-assets.ps1
```

## Sharing the mod

Run `build.bat`. The mod is `build/libs/tradingdesk-1.0.0.jar`. Players need Fabric Loader and Fabric API for Minecraft 26.3.
