package com.tradingdesk.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.oanda.OandaModels.Account;
import com.tradingdesk.client.oanda.OandaModels.Candle;
import com.tradingdesk.client.oanda.OandaModels.NavPoint;
import com.tradingdesk.client.oanda.OandaModels.Price;
import com.tradingdesk.client.oanda.OandaModels.Trade;

/**
 * Draws the account boards a chart screen can show instead of a market chart: the account summary, positions (open
 * trades added up per market), open trades, a NAV history graph, and a watchlist price board. Tables shrink their text
 * to fit the screen and say how many rows didn't fit.
 */
final class BoardPainter {
	private static final int TEXT = ChartPainter.TEXT;
	private static final int DIM = ChartPainter.DIM_TEXT;
	private static final int UP = ChartPainter.UP;
	private static final int DOWN = ChartPainter.DOWN;
	private static final int STRIPE = 0xFF121821;
	private static final int RULE = 0xFF30363D;
	private static final long NAV_WINDOW_SECONDS = 24 * 60 * 60;

	private BoardPainter() {
	}

	private record Cell(String text, int color) {
	}

	private static Cell cell(String text) {
		return new Cell(text, TEXT);
	}

	private static Cell money(double value) {
		return new Cell(String.format("%+,.2f", value), value >= 0 ? UP : DOWN);
	}

	static String title(String mode) {
		return switch (mode) {
			case "account" -> "Account";
			case "positions" -> "Positions";
			case "trades" -> "Open trades";
			case "nav" -> "NAV";
			case "watchlist" -> "Watchlist";
			case "ticker" -> "Ticker";
			default -> "Chart";
		};
	}

	static void paint(Canvas canvas, float w, float h, float textScale, String mode) {
		OandaData data = OandaData.get();
		canvas.rect(0, 0, w, h, ChartPainter.BACKGROUND, 0);
		if (data.status() != OandaData.Status.CONNECTED) {
			String message = data.message().isEmpty() ? "Connecting to OANDA..." : data.message();
			canvas.centeredLines(message, 0, 0, w, h, textScale * 0.8F, data.status() == OandaData.Status.ERROR ? DOWN : DIM, 3);
			return;
		}
		if (mode.equals("ticker")) {
			ticker(canvas, data, w, h);
			return;
		}
		float pad = 2.0F * textScale;
		float top = pad + 10 * textScale + pad;
		switch (mode) {
			case "account" -> account(canvas, data, w, h, textScale, pad, top);
			case "positions" -> positions(canvas, data, w, h, textScale, pad, top);
			case "trades" -> trades(canvas, data, w, h, textScale, pad, top);
			case "nav" -> nav(canvas, data, w, h, textScale, pad, top);
			case "watchlist" -> watchlist(canvas, data, w, h, textScale, pad, top);
			default -> {
			}
		}
	}

	private static void header(Canvas canvas, float w, float textScale, float pad, String left, @Nullable Cell right) {
		canvas.text(left, pad, pad, textScale, TEXT, 3);
		if (right != null) {
			canvas.rightText(right.text(), w - pad, pad, textScale, right.color(), 3);
		}
	}

	// ---- Boards ----

	private static void account(Canvas canvas, OandaData data, float w, float h, float textScale, float pad, float top) {
		Account account = data.account();
		header(canvas, w, textScale, pad, "Account " + (data.accountId() == null ? "" : data.accountId()), null);
		if (account == null) {
			canvas.centeredLines("Loading...", 0, top, w, h, textScale, DIM, 3);
			return;
		}
		// The NAV, as big as fits.
		String nav = String.format("%,.2f %s", account.nav(), account.currency());
		float navScale = Math.min(textScale * 3.0F, (w - 2 * pad) / Math.max(1, canvas.width(nav, 1.0F)));
		navScale = Math.min(navScale, (h - top) * 0.3F / 9.0F);
		canvas.text("NAV", pad, top, textScale * 0.8F, DIM, 3);
		float navTop = top + 9 * textScale * 0.8F;
		canvas.text(nav, pad, navTop, navScale, TEXT, 3);

		List<Trade> trades = data.trades();
		long markets = trades.stream().map(Trade::instrument).distinct().count();
		double marginLevel = account.marginUsed() > 0 ? account.nav() / account.marginUsed() * 100.0 : 0;
		List<Cell[]> rows = new ArrayList<>();
		rows.add(new Cell[] {new Cell("Balance", DIM), cell(String.format("%,.2f", account.balance()))});
		rows.add(new Cell[] {new Cell("Unrealized P/L", DIM), money(account.unrealizedPl())});
		rows.add(new Cell[] {new Cell("Realized P/L (all time)", DIM), money(account.realizedPl())});
		rows.add(new Cell[] {new Cell("Margin used", DIM), cell(String.format("%,.2f", account.marginUsed()))});
		rows.add(new Cell[] {new Cell("Margin available", DIM), cell(String.format("%,.2f", account.marginAvailable()))});
		rows.add(new Cell[] {new Cell("Margin level", DIM), cell(marginLevel > 0 ? String.format("%,.0f%%", marginLevel) : "-")});
		rows.add(new Cell[] {new Cell("Open trades", DIM), cell(trades.size() + " in " + markets + (markets == 1 ? " market" : " markets"))});
		table(canvas, pad, navTop + 10 * navScale + pad, w - pad, h - pad, textScale, null, new boolean[] {false, true}, rows, null);
	}

	private static void positions(Canvas canvas, OandaData data, float w, float h, float textScale, float pad, float top) {
		Map<String, long[]> units = new LinkedHashMap<>();
		Map<String, double[]> sums = new LinkedHashMap<>();
		for (Trade trade : data.trades()) {
			long[] u = units.computeIfAbsent(trade.instrument(), k -> new long[2]);
			double[] s = sums.computeIfAbsent(trade.instrument(), k -> new double[2]);
			u[0] += trade.units();
			u[1] += Math.abs(trade.units());
			s[0] += trade.entry() * Math.abs(trade.units());
			s[1] += trade.unrealizedPl();
		}
		double total = 0;
		List<Cell[]> rows = new ArrayList<>();
		for (Map.Entry<String, long[]> entry : units.entrySet()) {
			String instrument = entry.getKey();
			long net = entry.getValue()[0];
			long size = entry.getValue()[1];
			double[] s = sums.get(instrument);
			total += s[1];
			Price price = data.price(instrument);
			rows.add(new Cell[] {
				cell(data.displayName(instrument)),
				net > 0 ? new Cell("Long", UP) : net < 0 ? new Cell("Short", DOWN) : new Cell("Flat", DIM),
				cell(String.format("%,d", Math.abs(net))),
				cell(data.formatPrice(instrument, s[0] / Math.max(1, size))),
				cell(price == null ? "-" : data.formatPrice(instrument, price.mid())),
				money(s[1])});
		}
		header(canvas, w, textScale, pad, "Positions (" + rows.size() + ")", rows.isEmpty() ? null : money(total));
		if (rows.isEmpty()) {
			canvas.centeredLines("No open positions", 0, top, w, h, textScale, DIM, 3);
			return;
		}
		Cell[] footer = {new Cell("Total", DIM), cell(""), cell(""), cell(""), cell(""), money(total)};
		table(canvas, pad, top, w - pad, h - pad, textScale,
			new String[] {"Market", "Side", "Units", "Avg price", "Current", "P/L"},
			new boolean[] {false, false, true, true, true, true}, rows, footer);
	}

	private static void trades(Canvas canvas, OandaData data, float w, float h, float textScale, float pad, float top) {
		List<Trade> trades = data.trades();
		double total = 0;
		List<Cell[]> rows = new ArrayList<>();
		for (Trade trade : trades) {
			String instrument = trade.instrument();
			total += trade.unrealizedPl();
			rows.add(new Cell[] {
				cell(data.displayName(instrument)),
				trade.units() > 0 ? new Cell("Buy", UP) : new Cell("Sell", DOWN),
				cell(String.format("%,d", Math.abs(trade.units()))),
				cell(data.formatPrice(instrument, trade.entry())),
				new Cell(trade.stopLoss() == null ? "-" : data.formatPrice(instrument, trade.stopLoss()), trade.stopLoss() == null ? DIM : 0xFFEF9A9A),
				new Cell(trade.takeProfit() == null ? "-" : data.formatPrice(instrument, trade.takeProfit()), trade.takeProfit() == null ? DIM : 0xFFA5D6A7),
				money(trade.unrealizedPl())});
		}
		header(canvas, w, textScale, pad, "Open trades (" + trades.size() + ")", trades.isEmpty() ? null : money(total));
		if (rows.isEmpty()) {
			canvas.centeredLines("No open trades", 0, top, w, h, textScale, DIM, 3);
			return;
		}
		Cell[] footer = {new Cell("Total", DIM), cell(""), cell(""), cell(""), cell(""), cell(""), money(total)};
		table(canvas, pad, top, w - pad, h - pad, textScale,
			new String[] {"Market", "Side", "Units", "Entry", "Stop loss", "Take profit", "P/L"},
			new boolean[] {false, false, true, true, true, true, true}, rows, footer);
	}

	private static void watchlist(Canvas canvas, OandaData data, float w, float h, float textScale, float pad, float top) {
		List<String> watchlist = data.config().watchlist;
		header(canvas, w, textScale, pad, "Watchlist", null);
		if (watchlist.isEmpty()) {
			canvas.centeredLines("Add markets to the watchlist at the desk", 0, top, w, h, textScale, DIM, 3);
			return;
		}
		List<Cell[]> rows = new ArrayList<>();
		for (String instrument : watchlist) {
			Price price = data.price(instrument);
			if (price == null) {
				rows.add(new Cell[] {cell(data.displayName(instrument)), new Cell("-", DIM), new Cell("-", DIM), new Cell("-", DIM)});
				continue;
			}
			double pip = Math.pow(10, -Math.max(0, data.precision(instrument) - 1));
			rows.add(new Cell[] {
				new Cell(data.displayName(instrument), price.tradeable() ? TEXT : DIM),
				new Cell(data.formatPrice(instrument, price.bid()), DOWN),
				new Cell(data.formatPrice(instrument, price.ask()), UP),
				new Cell(String.format("%.1f", (price.ask() - price.bid()) / pip), DIM)});
		}
		table(canvas, pad, top, w - pad, h - pad, textScale,
			new String[] {"Market", "Bid", "Ask", "Spread"}, new boolean[] {false, true, true, true}, rows, null);
	}

	/**
	 * The watchlist scrolling right to left in one line as tall as the screen allows: each market's name, live price,
	 * and change since the day's open. Text is clipped a character at a time at the screen's edges.
	 */
	private static void ticker(Canvas canvas, OandaData data, float w, float h) {
		List<String> watchlist = data.config().watchlist;
		float scale = Math.min(h * 0.55F / 9.0F, 4.0F);
		float y = h / 2 - 4 * scale;
		if (watchlist.isEmpty()) {
			clippedText(canvas, "Add markets to the watchlist at the desk", 2, y, scale, DIM, w);
			return;
		}
		List<Cell[]> items = new ArrayList<>();
		float total = 0;
		float gap = canvas.width("   ", scale);
		for (String instrument : watchlist) {
			Price price = data.price(instrument);
			List<Candle> daily = data.candles(instrument, "D");
			Cell name = new Cell(data.displayName(instrument) + " ", TEXT);
			Cell last = price == null ? new Cell("-", DIM) : new Cell(data.formatPrice(instrument, price.mid()) + " ", TEXT);
			Cell change = new Cell("", DIM);
			if (price != null && daily != null && !daily.isEmpty() && daily.getLast().open() != 0) {
				double open = daily.getLast().open();
				double percent = (price.mid() - open) / open * 100;
				change = new Cell((percent >= 0 ? "\u25B2" : "\u25BC") + String.format("%.2f%%", Math.abs(percent)), percent >= 0 ? UP : DOWN);
			}
			Cell[] item = {name, last, change};
			items.add(item);
			for (Cell part : item) {
				total += canvas.width(part.text(), scale);
			}
			total += gap;
		}
		if (total <= 0) {
			return;
		}
		float speed = 12.0F * scale;
		float offset = (float) ((System.nanoTime() / 1.0E9 * speed) % total);
		float x = -offset;
		while (x < w) {
			for (Cell[] item : items) {
				for (Cell part : item) {
					clippedText(canvas, part.text(), x, y, scale, part.color(), w);
					x += canvas.width(part.text(), scale);
				}
				float divider = x + gap / 2;
				if (divider > 0.5F && divider < w - 0.5F) {
					canvas.rect(divider - 0.3F * scale, y, divider + 0.3F * scale, y + 7 * scale, RULE, 1);
				}
				x += gap;
				if (x >= w) {
					break;
				}
			}
		}
	}

	/** Text starting at x, leaving out any character that isn't wholly between 0 and {@code right}. */
	private static void clippedText(Canvas canvas, String text, float x, float y, float scale, int color, float right) {
		if (x >= 0 && x + canvas.width(text, scale) <= right) {
			canvas.text(text, x, y, scale, color, 3);
			return;
		}
		float cursor = x;
		for (int i = 0; i < text.length(); ) {
			int end = text.offsetByCodePoints(i, 1);
			String glyph = text.substring(i, end);
			float glyphWidth = canvas.width(glyph, scale);
			if (cursor >= 0 && cursor + glyphWidth <= right) {
				canvas.text(glyph, cursor, y, scale, color, 3);
			}
			cursor += glyphWidth;
			i = end;
		}
	}

	/**
	 * The NAV over the last day, as a stepped line with the area under it shaded, colored by whether it's up or down
	 * over the period shown.
	 */
	private static void nav(Canvas canvas, OandaData data, float w, float h, float textScale, float pad, float top) {
		List<NavPoint> history = data.navHistory();
		Account account = data.account();
		long now = System.currentTimeMillis() / 1000;
		List<NavPoint> points = new ArrayList<>();
		for (NavPoint point : history) {
			if (point.time() >= now - NAV_WINDOW_SECONDS) {
				points.add(point);
			}
		}
		if (account != null && (points.isEmpty() || points.getLast().time() < now)) {
			points.add(new NavPoint(now, account.nav()));
		}
		if (points.size() < 2) {
			header(canvas, w, textScale, pad, "NAV", null);
			canvas.centeredLines("Recording NAV... the graph fills in while the game runs", 0, top, w, h, textScale, DIM, 3);
			return;
		}
		double first = points.getFirst().nav();
		double last = points.getLast().nav();
		double change = last - first;
		int color = change >= 0 ? UP : DOWN;
		header(canvas, w, textScale, pad, "NAV  " + String.format("%,.2f", last),
			new Cell(String.format("%+,.2f (%+.2f%%)", change, first != 0 ? change / first * 100 : 0), color));

		double high = Double.NEGATIVE_INFINITY;
		double low = Double.POSITIVE_INFINITY;
		for (NavPoint point : points) {
			high = Math.max(high, point.nav());
			low = Math.min(low, point.nav());
		}
		double margin = Math.max((high - low) * 0.1, Math.abs(last) * 0.0005);
		high += margin;
		low -= margin;

		float labelScale = textScale * 0.75F;
		boolean axis = w >= 100;
		float axisWidth = axis ? canvas.width(String.format("%,.0f", high), labelScale) + 4 * textScale : 0;
		float left = pad;
		float right = w - pad - axisWidth;
		float bottom = h - pad - (axis ? 9 * labelScale : 0);
		long start = points.getFirst().time();
		long span = Math.max(1, now - start);

		for (int i = 1; i <= 3; i++) {
			float y = top + (bottom - top) * i / 4.0F;
			canvas.hLine(left, right, y, 0.3F, ChartPainter.GRID, 1);
			if (axis) {
				double value = high - (high - low) * i / 4.0;
				canvas.text(String.format("%,.0f", value), right + 2 * textScale, y - 4 * labelScale, labelScale, DIM, 3);
			}
		}

		int shade = (color & 0x00FFFFFF) | 0x33000000;
		float lineWidth = Math.max(0.5F, 0.5F * textScale);
		for (int i = 1; i < points.size(); i++) {
			NavPoint a = points.get(i - 1);
			NavPoint b = points.get(i);
			float x0 = left + (right - left) * (a.time() - start) / span;
			float x1 = left + (right - left) * (b.time() - start) / span;
			float y0 = (float) (top + (high - a.nav()) / (high - low) * (bottom - top));
			float y1 = (float) (top + (high - b.nav()) / (high - low) * (bottom - top));
			canvas.rect(x0, y0, x1, bottom, shade, 1);
			canvas.rect(x0, y0 - lineWidth / 2, x1 + lineWidth / 2, y0 + lineWidth / 2, color, 2);
			canvas.rect(x1 - lineWidth / 2, Math.min(y0, y1) - lineWidth / 2, x1 + lineWidth / 2, Math.max(y0, y1) + lineWidth / 2, color, 2);
		}
		if (axis) {
			canvas.text(duration(span) + " ago", left, bottom + 2 * labelScale, labelScale, DIM, 3);
			canvas.rightText("now", right, bottom + 2 * labelScale, labelScale, DIM, 3);
		}
	}

	private static String duration(long seconds) {
		if (seconds < 3600) {
			return Math.max(1, seconds / 60) + "m";
		}
		long hours = seconds / 3600;
		long minutes = seconds % 3600 / 60;
		return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
	}

	// ---- Tables ----

	/**
	 * Columns of text between x0 and x1, starting at y0, with headers (if any), striped rows, and an optional footer
	 * under a rule. Text is as big as {@code maxScale} but shrinks so every column fits across; rows that don't fit
	 * down become a "+N more" line.
	 */
	private static void table(
		Canvas canvas, float x0, float y0, float x1, float y1, float maxScale,
		String @Nullable [] headers, boolean[] rightAlign, List<Cell[]> rows, Cell @Nullable [] footer
	) {
		int columns = rightAlign.length;
		float gap = 8.0F;
		float[] widths = new float[columns];
		for (int c = 0; c < columns; c++) {
			if (headers != null) {
				widths[c] = canvas.width(headers[c], 1.0F);
			}
			for (Cell[] row : rows) {
				widths[c] = Math.max(widths[c], canvas.width(row[c].text(), 1.0F));
			}
			if (footer != null) {
				widths[c] = Math.max(widths[c], canvas.width(footer[c].text(), 1.0F));
			}
		}
		float natural = gap * (columns - 1);
		for (float width : widths) {
			natural += width;
		}
		float scale = Math.max(0.3F, Math.min(maxScale, (x1 - x0) / natural));
		float lineHeight = 11.0F * scale;
		float spare = Math.max(0, (x1 - x0) / scale - natural);
		float spacing = columns > 1 ? gap + spare / (columns - 1) : gap;

		float[] starts = new float[columns];
		float x = x0;
		for (int c = 0; c < columns; c++) {
			starts[c] = x;
			x += (widths[c] + spacing) * scale;
		}

		float y = y0;
		if (headers != null) {
			for (int c = 0; c < columns; c++) {
				cellText(canvas, new Cell(headers[c], DIM), starts[c], widths[c], rightAlign[c], y, scale);
			}
			y += lineHeight;
			canvas.hLine(x0, x1, y - 1.5F * scale, 0.3F, RULE, 1);
		}

		int reserved = footer != null ? 1 : 0;
		int fit = Math.max(0, (int) ((y1 - y) / lineHeight) - reserved);
		int shown = rows.size() <= fit ? rows.size() : Math.max(0, fit - 1);
		for (int r = 0; r < shown; r++) {
			if (r % 2 == 1) {
				canvas.rect(x0, y - 1.5F * scale, x1, y + lineHeight - 1.5F * scale, STRIPE, 1);
			}
			for (int c = 0; c < columns; c++) {
				cellText(canvas, rows.get(r)[c], starts[c], widths[c], rightAlign[c], y, scale);
			}
			y += lineHeight;
		}
		if (shown < rows.size()) {
			canvas.text("+" + (rows.size() - shown) + " more", x0, y, scale, DIM, 3);
			y += lineHeight;
		}
		if (footer != null && y + lineHeight <= y1 + lineHeight) {
			canvas.hLine(x0, x1, y - 1.5F * scale, 0.3F, RULE, 1);
			for (int c = 0; c < columns; c++) {
				cellText(canvas, footer[c], starts[c], widths[c], rightAlign[c], y, scale);
			}
		}
	}

	private static void cellText(Canvas canvas, Cell cell, float start, float width, boolean rightAlign, float y, float scale) {
		float x = rightAlign ? start + (width * scale - canvas.width(cell.text(), scale)) : start;
		canvas.text(cell.text(), x, y, scale, cell.color(), 3);
	}
}
