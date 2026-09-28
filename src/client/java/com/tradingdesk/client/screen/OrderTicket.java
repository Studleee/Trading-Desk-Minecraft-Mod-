package com.tradingdesk.client.screen;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.oanda.OandaModels.Price;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The market order ticket: units, stop loss, and take profit, as last typed at the desk. It outlives the terminal so
 * buy and sell buttons can use it too. Every order is confirmed before it's sent.
 */
public final class OrderTicket {
	static final int LIVE_COLOR = 0xFFDA3633;

	static @Nullable String units;
	static String stop = "";
	static String target = "";

	private OrderTicket() {
	}

	static String units() {
		if (units == null) {
			units = Long.toString(OandaData.get().config().defaultUnits);
		}
		return units;
	}

	public static boolean isFailure(String message) {
		return message.startsWith("Failed") || message.startsWith("Order cancelled") || message.startsWith("Not connected")
			|| message.startsWith("Units") || message.startsWith("Stop loss");
	}

	/**
	 * Checks the ticket and opens a confirmation for a market order. Returns what's wrong with the ticket instead if it
	 * can't be sent. {@code onResult} hears (on the game thread) that the order is sending and then how it went.
	 * Afterwards the player goes back to {@code returnTo}, or to the game if null.
	 */
	public static @Nullable String confirm(Minecraft minecraft, String instrument, boolean buy, @Nullable Screen returnTo, Consumer<String> onResult) {
		OandaData data = OandaData.get();
		if (data.status() != OandaData.Status.CONNECTED) {
			return "Not connected to OANDA";
		}
		long amount;
		try {
			amount = Long.parseLong(units().trim().replace(",", ""));
		} catch (NumberFormatException e) {
			amount = 0;
		}
		if (amount <= 0) {
			return "Units must be a whole number above 0 (set them at the desk)";
		}
		String stopPrice;
		String targetPrice;
		try {
			stopPrice = priceOrNull(data, instrument, stop);
			targetPrice = priceOrNull(data, instrument, target);
		} catch (NumberFormatException e) {
			return "Stop loss and take profit must be prices, or blank";
		}
		Price price = data.price(instrument);
		String at = price == null ? "" : " (about " + data.formatPrice(instrument, buy ? price.ask() : price.bid()) + ")";
		boolean live = data.config().live();
		long signed = buy ? amount : -amount;
		String message = (buy ? "BUY " : "SELL ") + String.format("%,d", amount) + " " + data.displayName(instrument) + " at market" + at
			+ "\nStop loss: " + (stopPrice == null ? "none" : stopPrice) + "    Take profit: " + (targetPrice == null ? "none" : targetPrice)
			+ "\nAccount " + data.accountId() + (live ? " (LIVE)" : " (practice)");
		Component heading = live
			? Component.literal("LIVE ACCOUNT: place this order with real money?").withColor(LIVE_COLOR)
			: Component.literal("Place this order on your practice account?");
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			minecraft.gui.setScreen(returnTo);
			if (yes) {
				onResult.accept("Sending order...");
				data.marketOrder(instrument, signed, stopPrice, targetPrice).thenAccept(result -> minecraft.execute(() -> onResult.accept(result)));
			}
		}, heading, Component.literal(message)));
		return null;
	}

	private static @Nullable String priceOrNull(OandaData data, String instrument, String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return data.formatPrice(instrument, Double.parseDouble(trimmed));
	}
}
