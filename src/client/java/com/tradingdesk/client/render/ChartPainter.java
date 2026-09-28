package com.tradingdesk.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.oanda.OandaModels.Candle;
import com.tradingdesk.client.oanda.OandaModels.Price;
import com.tradingdesk.client.oanda.OandaModels.Trade;

/**
 * Draws a candlestick chart for one instrument: a header with the market and latest price, candles with the newest
 * one following the live price, a dashed line at the current price, and optionally the player's open trades on that
 * market with their stop loss and take profit.
 */
final class ChartPainter {
	static final int BACKGROUND = 0xFF0D1117;
	static final int TEXT = 0xFFE6EDF3;
	static final int DIM_TEXT = 0xFF8B949E;
	static final int GRID = 0xFF1C2330;
	static final int UP = 0xFF26A69A;
	static final int DOWN = 0xFFEF5350;
	private static final int PRICE_LINE = 0xFF6E7681;
	private static final int BUY = 0xFF42A5F5;
	private static final int SELL = 0xFFFFA726;
	private static final int STOP = 0xFFEF5350;
	private static final int TARGET = 0xFF66BB6A;

	private ChartPainter() {
	}

	/** Header text to use instead of the market name and price, e.g. the account on the desk's monitor. */
	record Header(String left, String right, int rightColor) {
	}

	static String granularityLabel(String granularity) {
		return switch (granularity) {
			case "M1" -> "1m";
			case "M5" -> "5m";
			case "M15" -> "15m";
			case "H1" -> "1h";
			case "H4" -> "4h";
			case "D" -> "1D";
			default -> granularity;
		};
	}

	/**
	 * Draws the chart over a w by h area. {@code textScale} is how big text is (1 is 8 units tall). Price labels down
	 * the right side only appear when there's room.
	 */
	static void paint(
		Canvas canvas, float w, float h, float textScale, String instrument, String granularity, boolean showTrades, @Nullable Header header
	) {
		OandaData data = OandaData.get();
		canvas.rect(0, 0, w, h, BACKGROUND, 0);

		float pad = 2.0F * textScale;
		if (instrument.isEmpty()) {
			canvas.centeredLines("Right-click to choose a market", 0, 0, w, h, textScale, DIM_TEXT, 3);
			return;
		}
		if (data.status() != OandaData.Status.CONNECTED) {
			String message = data.message().isEmpty() ? "Connecting to OANDA..." : data.message();
			canvas.centeredLines(message, 0, 0, w, h, textScale * 0.8F, data.status() == OandaData.Status.ERROR ? DOWN : DIM_TEXT, 3);
			return;
		}
		if (!data.instruments().containsKey(instrument)) {
			canvas.centeredLines("Unknown market: " + instrument, 0, 0, w, h, textScale, DOWN, 3);
			return;
		}

		List<Candle> loaded = data.candles(instrument, granularity);
		Price price = data.price(instrument);
		List<Trade> trades = new ArrayList<>();
		if (showTrades) {
			for (Trade trade : data.trades()) {
				if (trade.instrument().equals(instrument)) {
					trades.add(trade);
				}
			}
		}

		// Header: market and timeframe on the left, latest price on the right, colored by the move since last close.
		float headerHeight = 10.0F * textScale;
		if (header != null) {
			canvas.text(header.left(), pad, pad, textScale, TEXT, 3);
			canvas.rightText(header.right(), w - pad, pad, textScale, header.rightColor(), 3);
		} else {
			canvas.text(data.displayName(instrument) + "  " + granularityLabel(granularity), pad, pad, textScale, TEXT, 3);
		}

		if (loaded == null || loaded.isEmpty()) {
			canvas.centeredLines("Loading " + data.displayName(instrument) + "...", 0, headerHeight, w, h, textScale, DIM_TEXT, 3);
			return;
		}

		List<Candle> candles = new ArrayList<>(loaded);
		if (price != null) {
			Candle last = candles.getLast();
			double mid = price.mid();
			candles.set(candles.size() - 1, new Candle(last.open(), Math.max(last.high(), mid), Math.min(last.low(), mid), mid));
		}
		double current = candles.getLast().close();
		double previous = candles.size() > 1 ? candles.get(candles.size() - 2).close() : candles.getLast().open();
		int moveColor = current >= previous ? UP : DOWN;
		if (header == null) {
			canvas.rightText(data.formatPrice(instrument, current), w - pad, pad, textScale, moveColor, 3);
		}

		float labelScale = textScale * 0.75F;
		boolean axis = w >= 100;
		float axisWidth = axis ? canvas.width(data.formatPrice(instrument, current), labelScale) + 4 * textScale : 0;
		float left = pad;
		float right = w - pad - axisWidth;
		float top = headerHeight + pad * 1.5F;
		float bottom = h - pad;
		if (right - left < 8 || bottom - top < 8) {
			return;
		}

		int count = Math.max(1, Math.min(candles.size(), Math.max(10, (int) ((right - left) / 2.5F))));
		List<Candle> visible = candles.subList(candles.size() - count, candles.size());
		double high = Double.NEGATIVE_INFINITY;
		double low = Double.POSITIVE_INFINITY;
		for (Candle candle : visible) {
			high = Math.max(high, candle.high());
			low = Math.min(low, candle.low());
		}
		for (Trade trade : trades) {
			high = Math.max(high, trade.entry());
			low = Math.min(low, trade.entry());
		}
		double margin = Math.max((high - low) * 0.06, Math.abs(current) * 0.0001);
		high += margin;
		low -= margin;
		Scale scale = new Scale(high, low, top, bottom);

		// Grid lines with prices beside them.
		for (int i = 1; i <= 3; i++) {
			float y = top + (bottom - top) * i / 4.0F;
			canvas.hLine(left, right, y, 0.3F, GRID, 1);
			if (axis) {
				canvas.text(data.formatPrice(instrument, scale.price(y)), right + 2 * textScale, y - 4 * labelScale, labelScale, DIM_TEXT, 3);
			}
		}

		// Candles.
		float spacing = (right - left) / count;
		float bodyWidth = Math.max(0.5F, spacing * 0.65F);
		float wickWidth = Math.max(0.25F, spacing * 0.12F);
		for (int i = 0; i < visible.size(); i++) {
			Candle candle = visible.get(i);
			float x = left + spacing * (i + 0.5F);
			int color = candle.close() >= candle.open() ? UP : DOWN;
			canvas.rect(x - wickWidth / 2, scale.y(candle.high()), x + wickWidth / 2, scale.y(candle.low()), color, 2);
			float bodyTop = scale.y(Math.max(candle.open(), candle.close()));
			float bodyBottom = Math.max(bodyTop + 0.3F, scale.y(Math.min(candle.open(), candle.close())));
			canvas.rect(x - bodyWidth / 2, bodyTop, x + bodyWidth / 2, bodyBottom, color, 2);
		}

		// Open trades: entry, stop loss, and take profit.
		for (Trade trade : trades) {
			int color = trade.units() > 0 ? BUY : SELL;
			float y = scale.y(trade.entry());
			canvas.hLine(left, right, y, 0.45F, color, 3);
			String label = (trade.units() > 0 ? "BUY " : "SELL ") + Math.abs(trade.units()) + "  " + String.format("%+.2f", trade.unrealizedPl());
			float labelWidth = canvas.width(label, labelScale);
			canvas.rect(left, y - 9 * labelScale, left + labelWidth + 2 * labelScale, y - 0.3F, color, 4);
			canvas.text(label, left + labelScale, y - 8.5F * labelScale, labelScale, 0xFF000000, 5);
			markLevel(canvas, scale, trade.stopLoss(), "SL", STOP, left, right, labelScale);
			markLevel(canvas, scale, trade.takeProfit(), "TP", TARGET, left, right, labelScale);
		}

		if (!trades.isEmpty()) {
			drawPosition(canvas, data, instrument, trades, left, top, textScale);
		}

		// The current price.
		float priceY = scale.y(current);
		canvas.dashedLine(left, right, priceY, 0.3F, 1.5F, PRICE_LINE, 3);
		if (axis) {
			String label = data.formatPrice(instrument, current);
			canvas.rect(right + textScale, priceY - 5 * labelScale, w - pad * 0.5F, priceY + 5 * labelScale, moveColor, 4);
			canvas.text(label, right + 2 * textScale, priceY - 4 * labelScale, labelScale, 0xFF000000, 5);
		}
	}

	/**
	 * A box in the chart's top-left corner summing up the player's position in this market: net size, average entry,
	 * and total unrealized P/L across all its open trades.
	 */
	private static void drawPosition(Canvas canvas, OandaData data, String instrument, List<Trade> trades, float left, float top, float textScale) {
		long net = 0;
		long size = 0;
		double weightedEntry = 0;
		double pl = 0;
		for (Trade trade : trades) {
			net += trade.units();
			size += Math.abs(trade.units());
			weightedEntry += trade.entry() * Math.abs(trade.units());
			pl += trade.unrealizedPl();
		}
		String side = net > 0 ? "LONG " : net < 0 ? "SHORT " : "FLAT ";
		String position = side + String.format("%,d", Math.abs(net)) + " @ " + data.formatPrice(instrument, weightedEntry / Math.max(1, size));
		var account = data.account();
		String plText = String.format("%+,.2f", pl) + (account != null ? " " + account.currency() : "");
		float scale = textScale * 0.85F;
		float gap = 4 * scale;
		float width = canvas.width(position, scale) + gap + canvas.width(plText, scale) + 4 * scale;
		float height = 11 * scale;
		canvas.rect(left + scale, top + scale, left + scale + width, top + scale + height, 0xE0161B22, 4);
		canvas.rect(left + scale, top + scale, left + scale + 0.8F * scale, top + scale + height, pl >= 0 ? UP : DOWN, 5);
		float textY = top + scale + 2 * scale;
		canvas.text(position, left + 3 * scale, textY, scale, TEXT, 5);
		canvas.text(plText, left + 3 * scale + canvas.width(position, scale) + gap, textY, scale, pl >= 0 ? UP : DOWN, 5);
	}

	/** A stop loss or take profit, if it's within the chart's price range. */
	private static void markLevel(Canvas canvas, Scale scale, @Nullable Double level, String label, int color, float left, float right, float labelScale) {
		if (level == null || !scale.contains(level)) {
			return;
		}
		float y = scale.y(level);
		canvas.dashedLine(left, right, y, 0.35F, 2.0F, color, 3);
		canvas.rightText(label, right - labelScale, y - 9 * labelScale, labelScale, color, 5);
	}

	/** Maps prices to heights on the chart and back. */
	private record Scale(double high, double low, float top, float bottom) {
		float y(double price) {
			return (float) (top + (high - price) / (high - low) * (bottom - top));
		}

		double price(float y) {
			return high - (y - top) / (bottom - top) * (high - low);
		}

		boolean contains(double price) {
			return price <= high && price >= low;
		}
	}
}
