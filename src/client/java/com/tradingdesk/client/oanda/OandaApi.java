package com.tradingdesk.client.oanda;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tradingdesk.client.oanda.OandaModels.Account;
import com.tradingdesk.client.oanda.OandaModels.Candle;
import com.tradingdesk.client.oanda.OandaModels.Instrument;
import com.tradingdesk.client.oanda.OandaModels.Price;
import com.tradingdesk.client.oanda.OandaModels.Trade;

/**
 * Calls OANDA's v20 REST API. Blocking; run it off the game thread.
 */
final class OandaApi {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private final String baseUrl;
	private final String token;

	OandaApi(String baseUrl, String token) {
		this.baseUrl = baseUrl;
		this.token = token;
	}

	/** An error from OANDA, or a failure to reach it, worded for the player. */
	static final class OandaException extends Exception {
		final boolean unauthorized;

		OandaException(String message, boolean unauthorized) {
			super(message);
			this.unauthorized = unauthorized;
		}
	}

	// ---- Reading ----

	List<String> accountIds() throws OandaException {
		List<String> ids = new ArrayList<>();
		for (JsonElement account : request("GET", "/v3/accounts", null).getAsJsonArray("accounts")) {
			ids.add(account.getAsJsonObject().get("id").getAsString());
		}
		return ids;
	}

	List<Instrument> instruments(String accountId) throws OandaException {
		List<Instrument> instruments = new ArrayList<>();
		for (JsonElement element : request("GET", "/v3/accounts/" + accountId + "/instruments", null).getAsJsonArray("instruments")) {
			JsonObject instrument = element.getAsJsonObject();
			instruments.add(new Instrument(
				instrument.get("name").getAsString(),
				instrument.get("displayName").getAsString(),
				instrument.get("displayPrecision").getAsInt()));
		}
		instruments.sort((a, b) -> a.name().compareTo(b.name()));
		return instruments;
	}

	Account account(String accountId) throws OandaException {
		JsonObject account = request("GET", "/v3/accounts/" + accountId + "/summary", null).getAsJsonObject("account");
		return new Account(
			accountId,
			account.get("currency").getAsString(),
			number(account, "balance"),
			number(account, "NAV"),
			number(account, "unrealizedPL"),
			number(account, "pl"),
			number(account, "marginUsed"),
			number(account, "marginAvailable"));
	}

	List<Trade> openTrades(String accountId) throws OandaException {
		List<Trade> trades = new ArrayList<>();
		for (JsonElement element : request("GET", "/v3/accounts/" + accountId + "/openTrades", null).getAsJsonArray("trades")) {
			JsonObject trade = element.getAsJsonObject();
			trades.add(new Trade(
				trade.get("id").getAsString(),
				trade.get("instrument").getAsString(),
				(long) number(trade, "currentUnits"),
				number(trade, "price"),
				number(trade, "unrealizedPL"),
				orderPrice(trade, "stopLossOrder"),
				orderPrice(trade, "takeProfitOrder")));
		}
		return trades;
	}

	List<Price> prices(String accountId, List<String> instruments) throws OandaException {
		String query = URLEncoder.encode(String.join(",", instruments), StandardCharsets.UTF_8);
		List<Price> prices = new ArrayList<>();
		for (JsonElement element : request("GET", "/v3/accounts/" + accountId + "/pricing?instruments=" + query, null).getAsJsonArray("prices")) {
			JsonObject price = element.getAsJsonObject();
			JsonArray bids = price.getAsJsonArray("bids");
			JsonArray asks = price.getAsJsonArray("asks");
			if (bids == null || asks == null || bids.isEmpty() || asks.isEmpty()) {
				continue;
			}
			prices.add(new Price(
				price.get("instrument").getAsString(),
				number(bids.get(0).getAsJsonObject(), "price"),
				number(asks.get(0).getAsJsonObject(), "price"),
				price.has("tradeable") && price.get("tradeable").getAsBoolean()));
		}
		return prices;
	}

	/** The most recent candles, oldest first, priced at the middle between bid and ask. */
	List<Candle> candles(String instrument, String granularity, int count) throws OandaException {
		String path = "/v3/instruments/" + instrument + "/candles?price=M&granularity=" + granularity + "&count=" + count;
		List<Candle> candles = new ArrayList<>();
		for (JsonElement element : request("GET", path, null).getAsJsonArray("candles")) {
			JsonObject mid = element.getAsJsonObject().getAsJsonObject("mid");
			if (mid != null) {
				candles.add(new Candle(number(mid, "o"), number(mid, "h"), number(mid, "l"), number(mid, "c")));
			}
		}
		return candles;
	}

	// ---- Trading ----

	/**
	 * Places a market order (negative units sell) with an optional stop loss and take profit, and describes what
	 * happened.
	 */
	String marketOrder(String accountId, String instrument, long units, @Nullable String stopLoss, @Nullable String takeProfit) throws OandaException {
		JsonObject order = new JsonObject();
		order.addProperty("type", "MARKET");
		order.addProperty("instrument", instrument);
		order.addProperty("units", Long.toString(units));
		order.addProperty("timeInForce", "FOK");
		order.addProperty("positionFill", "DEFAULT");
		if (stopLoss != null) {
			JsonObject details = new JsonObject();
			details.addProperty("price", stopLoss);
			order.add("stopLossOnFill", details);
		}
		if (takeProfit != null) {
			JsonObject details = new JsonObject();
			details.addProperty("price", takeProfit);
			order.add("takeProfitOnFill", details);
		}
		JsonObject body = new JsonObject();
		body.add("order", order);
		JsonObject reply = request("POST", "/v3/accounts/" + accountId + "/orders", body);
		if (reply.has("orderFillTransaction")) {
			JsonObject fill = reply.getAsJsonObject("orderFillTransaction");
			return "Filled " + (units > 0 ? "buy " : "sell ") + Math.abs(units) + " " + instrument + " at " + fill.get("price").getAsString();
		}
		if (reply.has("orderCancelTransaction")) {
			return "Order cancelled: " + reply.getAsJsonObject("orderCancelTransaction").get("reason").getAsString();
		}
		return "Order sent";
	}

	String closeTrade(String accountId, String tradeId) throws OandaException {
		JsonObject body = new JsonObject();
		body.addProperty("units", "ALL");
		JsonObject reply = request("PUT", "/v3/accounts/" + accountId + "/trades/" + tradeId + "/close", body);
		if (reply.has("orderFillTransaction")) {
			JsonObject fill = reply.getAsJsonObject("orderFillTransaction");
			String pl = fill.has("pl") ? fill.get("pl").getAsString() : "?";
			return "Closed trade " + tradeId + " at " + fill.get("price").getAsString() + " (P/L " + pl + ")";
		}
		return "Close sent for trade " + tradeId;
	}

	// ---- Plumbing ----

	private JsonObject request(String method, String path, @Nullable JsonObject body) throws OandaException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.timeout(Duration.ofSeconds(15))
			.header("Authorization", "Bearer " + token)
			.header("Accept-Datetime-Format", "UNIX")
			.header("Content-Type", "application/json");
		builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
		HttpResponse<String> response;
		try {
			response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new OandaException("Can't reach OANDA (" + e.getClass().getSimpleName() + ")", false);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new OandaException("Interrupted", false);
		}
		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(response.body());
			json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (JsonParseException e) {
			json = new JsonObject();
		}
		int status = response.statusCode();
		if (status == 401 || status == 403) {
			throw new OandaException("OANDA rejected the token. Check it's for the " + (baseUrl.contains("fxtrade") ? "live" : "practice") + " environment.", true);
		}
		if (status >= 400) {
			String message = json.has("errorMessage") ? json.get("errorMessage").getAsString() : "HTTP " + status;
			throw new OandaException(message, false);
		}
		return json;
	}

	private static double number(JsonObject object, String name) {
		JsonElement element = object.get(name);
		return element == null || element.isJsonNull() ? 0.0 : element.getAsDouble();
	}

	private static @Nullable Double orderPrice(JsonObject trade, String name) {
		JsonObject order = trade.getAsJsonObject(name);
		return order != null && order.has("price") ? order.get("price").getAsDouble() : null;
	}
}
