package com.tradingdesk.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.oanda.OandaModels.Account;
import com.tradingdesk.client.oanda.OandaModels.Price;
import com.tradingdesk.client.oanda.OandaModels.Trade;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The trading terminal: account summary across the top, a watchlist of live prices, a market order ticket, and the
 * open trades with close buttons. Every order and close asks for confirmation first, and says loudly when the account
 * is live.
 */
public class DeskScreen extends Screen {
	private static final int MARGIN = 6;
	private static final int ROW = 11;
	private static final int PANEL = 0xE0161B22;
	private static final int PANEL_EDGE = 0xFF30363D;
	private static final int HIGHLIGHT = 0xFF1F2A3A;
	private static final int TEXT = 0xFFE6EDF3;
	private static final int DIM = 0xFF8B949E;
	private static final int UP = 0xFF3FB950;
	private static final int DOWN = 0xFFF85149;
	private static final int LIVE = 0xFFDA3633;
	private static final int PRACTICE = 0xFF238636;

	/** The market picked in the watchlist; the desk's monitor charts it too. */
	private static @Nullable String selected;

	private String unitsText = "";
	private String stopText = "";
	private String targetText = "";
	private String addText = "";
	private String result = "";
	private boolean resultBad;
	private int watchScroll;
	private int tradeScroll;

	private @Nullable Button buyButton;
	private @Nullable Button sellButton;

	public DeskScreen() {
		super(Component.literal("OANDA Trading Desk"));
		unitsText = Long.toString(OandaData.get().config().defaultUnits);
	}

	public static String selectedInstrument() {
		List<String> watchlist = OandaData.get().config().watchlist;
		if (selected == null || selected.isEmpty()) {
			return watchlist.isEmpty() ? "EUR_USD" : watchlist.getFirst();
		}
		return selected;
	}

	// ---- Layout ----

	private int mainTop() {
		return 50;
	}

	private int mainBottom() {
		return Math.min(mainTop() + 110, height - 70);
	}

	private int watchRight() {
		return MARGIN + Math.min(160, width / 3);
	}

	private int ticketLeft() {
		return watchRight() + 6;
	}

	private int tradesTop() {
		return mainBottom() + 6;
	}

	private int tradesBottom() {
		return height - 18;
	}

	private int watchRows() {
		return Math.max(1, (mainBottom() - mainTop() - 24 - 20) / ROW);
	}

	private int tradeRows() {
		return Math.max(1, (tradesBottom() - tradesTop() - 24) / ROW);
	}

	@Override
	protected void init() {
		OandaData data = OandaData.get();
		addRenderableWidget(Button.builder(Component.literal("Reload"), button -> {
			data.reload();
			showResult("Reloaded config/tradingdesk.json", false);
		}).bounds(width - MARGIN - 50, 4, 50, 14).build());

		int addY = mainBottom() - 18;
		EditBox add = new EditBox(font, MARGIN + 4, addY, watchRight() - MARGIN - 50, 14, Component.literal("Add market"));
		add.setMaxLength(16);
		add.setValue(addText);
		add.setHint(Component.literal("e.g. EUR_USD"));
		add.setResponder(value -> addText = value);
		addRenderableWidget(add);
		addRenderableWidget(Button.builder(Component.literal("Add"), button -> addToWatchlist()).bounds(watchRight() - 42, addY, 38, 14).build());

		int x = ticketLeft() + 70;
		int boxWidth = Math.min(90, width - MARGIN - x - 4);
		addRenderableWidget(box(x, mainTop() + 30, boxWidth, unitsText, "Units", value -> unitsText = value));
		addRenderableWidget(box(x, mainTop() + 48, boxWidth, stopText, "optional", value -> stopText = value));
		addRenderableWidget(box(x, mainTop() + 66, boxWidth, targetText, "optional", value -> targetText = value));

		int ticketWidth = width - MARGIN - ticketLeft() - 8;
		int half = ticketWidth / 2 - 2;
		sellButton = addRenderableWidget(Button.builder(Component.literal("Sell"), button -> confirmOrder(false))
			.bounds(ticketLeft() + 4, mainTop() + 86, half, 18).build());
		buyButton = addRenderableWidget(Button.builder(Component.literal("Buy"), button -> confirmOrder(true))
			.bounds(ticketLeft() + 8 + half, mainTop() + 86, half, 18).build());
		updateButtons();
	}

	private EditBox box(int x, int y, int boxWidth, String value, String hint, java.util.function.Consumer<String> responder) {
		EditBox box = new EditBox(font, x, y, boxWidth, 14, Component.literal(hint));
		box.setMaxLength(20);
		box.setValue(value);
		box.setHint(Component.literal(hint));
		box.setResponder(responder);
		return box;
	}

	@Override
	public void tick() {
		updateButtons();
	}

	private void updateButtons() {
		OandaData data = OandaData.get();
		String instrument = selectedInstrument();
		Price price = data.price(instrument);
		boolean canTrade = data.status() == OandaData.Status.CONNECTED && price != null && price.tradeable();
		if (sellButton != null && buyButton != null) {
			sellButton.active = canTrade;
			buyButton.active = canTrade;
			sellButton.setMessage(Component.literal(price == null ? "Sell" : "Sell  " + data.formatPrice(instrument, price.bid())));
			buyButton.setMessage(Component.literal(price == null ? "Buy" : "Buy  " + data.formatPrice(instrument, price.ask())));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- Drawing ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		OandaData data = OandaData.get();
		boolean live = data.config().live();

		// Title, environment, and connection status.
		graphics.text(font, title, MARGIN, 8, TEXT);
		int badgeX = MARGIN + font.width(title) + 6;
		String badge = live ? "LIVE" : "PRACTICE";
		graphics.fill(badgeX, 6, badgeX + font.width(badge) + 6, 18, live ? LIVE : PRACTICE);
		graphics.text(font, badge, badgeX + 3, 8, 0xFFFFFFFF);
		String accountId = data.accountId();
		int statusX = badgeX + font.width(badge) + 12;
		switch (data.status()) {
			case CONNECTED -> graphics.text(font, "Account " + accountId, statusX, 8, DIM);
			case CONNECTING -> graphics.text(font, "Connecting...", statusX, 8, DIM);
			case NO_TOKEN, ERROR -> graphics.text(font, "Not connected", statusX, 8, DOWN);
		}

		drawAccount(graphics, data);
		drawWatchlist(graphics, data, mouseX, mouseY);
		drawTicket(graphics, data);
		drawTrades(graphics, data, mouseX, mouseY);

		if (!result.isEmpty()) {
			graphics.text(font, result, MARGIN, height - 13, resultBad ? DOWN : UP);
		} else if (data.status() != OandaData.Status.CONNECTED && !data.message().isEmpty()) {
			graphics.text(font, data.message(), MARGIN, height - 13, data.status() == OandaData.Status.ERROR ? DOWN : DIM);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void panel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0, y0, x1, y1, PANEL);
		graphics.outline(x0, y0, x1 - x0, y1 - y0, PANEL_EDGE);
	}

	private void drawAccount(GuiGraphicsExtractor graphics, OandaData data) {
		int top = 22;
		panel(graphics, MARGIN, top, width - MARGIN, top + 24);
		Account account = data.account();
		String[] labels = {"Balance", "NAV", "Unrealized P/L", "Margin used", "Margin available"};
		String[] values = new String[5];
		int plColor = TEXT;
		if (account != null) {
			values[0] = money(account.balance()) + " " + account.currency();
			values[1] = money(account.nav());
			values[2] = String.format("%+,.2f", account.unrealizedPl());
			values[3] = money(account.marginUsed());
			values[4] = money(account.marginAvailable());
			plColor = account.unrealizedPl() >= 0 ? UP : DOWN;
		}
		int columnWidth = (width - 2 * MARGIN) / 5;
		for (int i = 0; i < 5; i++) {
			int x = MARGIN + 4 + i * columnWidth;
			graphics.text(font, labels[i], x, top + 3, DIM);
			graphics.text(font, values[i] == null ? "-" : values[i], x, top + 13, i == 2 ? plColor : TEXT);
		}
	}

	private void drawWatchlist(GuiGraphicsExtractor graphics, OandaData data, int mouseX, int mouseY) {
		int x0 = MARGIN;
		int x1 = watchRight();
		int top = mainTop();
		panel(graphics, x0, top, x1, mainBottom());
		graphics.text(font, "Watchlist", x0 + 4, top + 3, TEXT);
		int bidX = x1 - 94;
		int askX = x1 - 52;
		graphics.text(font, "Bid", bidX, top + 13, DIM);
		graphics.text(font, "Ask", askX, top + 13, DIM);

		List<String> watchlist = data.config().watchlist;
		watchScroll = Math.clamp(watchScroll, 0, Math.max(0, watchlist.size() - watchRows()));
		String current = selectedInstrument();
		for (int row = 0; row < watchRows() && row + watchScroll < watchlist.size(); row++) {
			String instrument = watchlist.get(row + watchScroll);
			int y = top + 24 + row * ROW;
			boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
			if (instrument.equals(current)) {
				graphics.fill(x0 + 1, y - 1, x1 - 1, y + ROW - 1, HIGHLIGHT);
			}
			graphics.text(font, data.displayName(instrument), x0 + 4, y, TEXT);
			Price price = data.price(instrument);
			if (price != null) {
				graphics.text(font, data.formatPrice(instrument, price.bid()), bidX, y, DOWN);
				graphics.text(font, data.formatPrice(instrument, price.ask()), askX, y, UP);
			}
			if (hover) {
				graphics.text(font, "x", x1 - 8, y, DIM);
			}
		}
	}

	private void drawTicket(GuiGraphicsExtractor graphics, OandaData data) {
		int x0 = ticketLeft();
		int top = mainTop();
		panel(graphics, x0, top, width - MARGIN, mainBottom());
		String instrument = selectedInstrument();
		graphics.text(font, "Market order", x0 + 4, top + 3, DIM);
		graphics.text(font, data.displayName(instrument), x0 + 70, top + 3, TEXT);
		Price price = data.price(instrument);
		if (price != null) {
			double pip = Math.pow(10, -Math.max(0, data.precision(instrument) - 1));
			String spread = String.format("Spread %.1f", (price.ask() - price.bid()) / pip);
			graphics.text(font, spread + (price.tradeable() ? "" : "  (market closed)"), x0 + 70, top + 15, price.tradeable() ? DIM : DOWN);
		}
		graphics.text(font, "Units", x0 + 4, top + 33, TEXT);
		graphics.text(font, "Stop loss", x0 + 4, top + 51, TEXT);
		graphics.text(font, "Take profit", x0 + 4, top + 69, TEXT);
	}

	private void drawTrades(GuiGraphicsExtractor graphics, OandaData data, int mouseX, int mouseY) {
		int x0 = MARGIN;
		int x1 = width - MARGIN;
		int top = tradesTop();
		panel(graphics, x0, top, x1, tradesBottom());
		List<Trade> trades = data.trades();
		graphics.text(font, "Open trades (" + trades.size() + ")", x0 + 4, top + 3, TEXT);
		int[] columns = tradeColumns();
		String[] headers = {"Market", "Side", "Units", "Entry", "Stop loss", "Take profit", "P/L"};
		for (int i = 0; i < headers.length; i++) {
			graphics.text(font, headers[i], columns[i], top + 13, DIM);
		}
		tradeScroll = Math.clamp(tradeScroll, 0, Math.max(0, trades.size() - tradeRows()));
		for (int row = 0; row < tradeRows() && row + tradeScroll < trades.size(); row++) {
			Trade trade = trades.get(row + tradeScroll);
			int y = top + 24 + row * ROW;
			String instrument = trade.instrument();
			graphics.text(font, data.displayName(instrument), columns[0], y, TEXT);
			graphics.text(font, trade.units() > 0 ? "Buy" : "Sell", columns[1], y, trade.units() > 0 ? UP : DOWN);
			graphics.text(font, String.format("%,d", Math.abs(trade.units())), columns[2], y, TEXT);
			graphics.text(font, data.formatPrice(instrument, trade.entry()), columns[3], y, TEXT);
			graphics.text(font, trade.stopLoss() == null ? "-" : data.formatPrice(instrument, trade.stopLoss()), columns[4], y, TEXT);
			graphics.text(font, trade.takeProfit() == null ? "-" : data.formatPrice(instrument, trade.takeProfit()), columns[5], y, TEXT);
			graphics.text(font, String.format("%+,.2f", trade.unrealizedPl()), columns[6], y, trade.unrealizedPl() >= 0 ? UP : DOWN);
			int closeX = x1 - 36;
			boolean hover = mouseX >= closeX && mouseX < x1 - 4 && mouseY >= y - 1 && mouseY < y + ROW - 1;
			graphics.fill(closeX, y - 1, x1 - 4, y + ROW - 2, hover ? 0xFF6E2A2A : 0xFF3A1D1D);
			graphics.text(font, "Close", closeX + 3, y, TEXT);
		}
		if (trades.isEmpty()) {
			graphics.text(font, "No open trades", x0 + 4, top + 24, DIM);
		}
	}

	private int[] tradeColumns() {
		int x = MARGIN + 4;
		int usable = width - 2 * MARGIN - 48;
		int[] columns = new int[7];
		double[] share = {0, 0.2, 0.3, 0.42, 0.56, 0.72, 0.88};
		for (int i = 0; i < 7; i++) {
			columns[i] = x + (int) (usable * share[i]);
		}
		return columns;
	}

	private static String money(double value) {
		return String.format("%,.2f", value);
	}

	// ---- Input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mouseX = event.x();
		double mouseY = event.y();
		OandaData data = OandaData.get();

		List<String> watchlist = data.config().watchlist;
		for (int row = 0; row < watchRows() && row + watchScroll < watchlist.size(); row++) {
			int y = mainTop() + 24 + row * ROW;
			if (mouseX >= MARGIN && mouseX < watchRight() && mouseY >= y && mouseY < y + ROW) {
				String instrument = watchlist.get(row + watchScroll);
				if (mouseX >= watchRight() - 10) {
					List<String> updated = new ArrayList<>(watchlist);
					updated.remove(instrument);
					data.setWatchlist(updated);
				} else {
					selected = instrument;
					updateButtons();
				}
				return true;
			}
		}

		List<Trade> trades = data.trades();
		int x1 = width - MARGIN;
		for (int row = 0; row < tradeRows() && row + tradeScroll < trades.size(); row++) {
			int y = tradesTop() + 24 + row * ROW;
			if (mouseX >= x1 - 36 && mouseX < x1 - 4 && mouseY >= y - 1 && mouseY < y + ROW - 1) {
				confirmClose(trades.get(row + tradeScroll));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int step = scrollY > 0 ? -1 : 1;
		if (y >= mainTop() && y < mainBottom() && x < watchRight()) {
			watchScroll += step;
			return true;
		}
		if (y >= tradesTop() && y < tradesBottom()) {
			tradeScroll += step;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void addToWatchlist() {
		OandaData data = OandaData.get();
		String instrument = addText.trim().toUpperCase(Locale.ROOT).replace('/', '_');
		if (instrument.isEmpty()) {
			return;
		}
		if (!data.instruments().isEmpty() && !data.instruments().containsKey(instrument)) {
			showResult("Unknown market: " + instrument, true);
			return;
		}
		List<String> watchlist = new ArrayList<>(data.config().watchlist);
		if (!watchlist.contains(instrument)) {
			watchlist.add(instrument);
			data.setWatchlist(watchlist);
		}
		selected = instrument;
		addText = "";
		rebuildWidgets();
	}

	// ---- Orders ----

	private void confirmOrder(boolean buy) {
		OandaData data = OandaData.get();
		String instrument = selectedInstrument();
		long units;
		try {
			units = Long.parseLong(unitsText.trim().replace(",", ""));
		} catch (NumberFormatException e) {
			units = 0;
		}
		if (units <= 0) {
			showResult("Units must be a whole number above 0", true);
			return;
		}
		String stop;
		String target;
		try {
			stop = priceOrNull(data, instrument, stopText);
			target = priceOrNull(data, instrument, targetText);
		} catch (NumberFormatException e) {
			showResult("Stop loss and take profit must be prices, or blank", true);
			return;
		}
		Price price = data.price(instrument);
		String at = price == null ? "" : " (about " + data.formatPrice(instrument, buy ? price.ask() : price.bid()) + ")";
		boolean live = data.config().live();
		long signed = buy ? units : -units;
		String message = (buy ? "BUY " : "SELL ") + String.format("%,d", units) + " " + data.displayName(instrument) + " at market" + at
			+ "\nStop loss: " + (stop == null ? "none" : stop) + "    Take profit: " + (target == null ? "none" : target)
			+ "\nAccount " + data.accountId() + (live ? " (LIVE)" : " (practice)");
		Component heading = live
			? Component.literal("LIVE ACCOUNT: place this order with real money?").withColor(LIVE)
			: Component.literal("Place this order on your practice account?");
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			minecraft.gui.setScreen(this);
			if (yes) {
				showResult("Sending order...", false);
				data.marketOrder(instrument, signed, stop, target).thenAccept(this::showResultLater);
			}
		}, heading, Component.literal(message)));
	}

	private void confirmClose(Trade trade) {
		OandaData data = OandaData.get();
		boolean live = data.config().live();
		String message = "Close " + (trade.units() > 0 ? "BUY " : "SELL ") + String.format("%,d", Math.abs(trade.units())) + " "
			+ data.displayName(trade.instrument()) + " (P/L " + String.format("%+,.2f", trade.unrealizedPl()) + ")?";
		Component heading = live
			? Component.literal("LIVE ACCOUNT: close this trade?").withColor(LIVE)
			: Component.literal("Close this trade?");
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			minecraft.gui.setScreen(this);
			if (yes) {
				showResult("Closing trade " + trade.id() + "...", false);
				data.closeTrade(trade.id()).thenAccept(this::showResultLater);
			}
		}, heading, Component.literal(message)));
	}

	private static @Nullable String priceOrNull(OandaData data, String instrument, String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return data.formatPrice(instrument, Double.parseDouble(trimmed));
	}

	private void showResultLater(String message) {
		minecraft.execute(() -> showResult(message, message.startsWith("Failed") || message.startsWith("Order cancelled") || message.startsWith("Not connected")));
	}

	private void showResult(String message, boolean bad) {
		result = message;
		resultBad = bad;
	}
}
