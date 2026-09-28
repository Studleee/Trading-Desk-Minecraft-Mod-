package com.tradingdesk.client.oanda;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;

import com.tradingdesk.TradingDesk;
import com.tradingdesk.client.oanda.OandaApi.OandaException;
import com.tradingdesk.client.oanda.OandaModels.Account;
import com.tradingdesk.client.oanda.OandaModels.Candle;
import com.tradingdesk.client.oanda.OandaModels.Instrument;
import com.tradingdesk.client.oanda.OandaModels.NavPoint;
import com.tradingdesk.client.oanda.OandaModels.Price;
import com.tradingdesk.client.oanda.OandaModels.Trade;

/**
 * Everything the desk and charts know about the player's OANDA account, kept fresh by a background thread. Nothing is
 * fetched unless something asked for it recently: screens and charts call the getters every frame, which marks what
 * they're showing as wanted, and the poller only refreshes what's wanted. Orders and closes run on their own thread
 * so they don't wait behind polling.
 */
public final class OandaData {
	public enum Status {
		NO_TOKEN, CONNECTING, CONNECTED, ERROR
	}

	private static final long WANTED_FOR_MS = 5_000;
	private static final long ACCOUNT_EVERY_MS = 2_000;
	private static final long RETRY_AFTER_MS = 15_000;
	private static final int CANDLE_COUNT = 150;
	private static final long NAV_EVERY_SECONDS = 30;
	private static final long NAV_KEEP_SECONDS = 30L * 24 * 60 * 60;

	private static final OandaData INSTANCE = new OandaData();

	private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(daemon("TradingDesk OANDA poller"));
	private final ExecutorService actions = Executors.newSingleThreadExecutor(daemon("TradingDesk OANDA orders"));

	private volatile OandaConfig config = OandaConfig.load();
	private volatile @Nullable OandaApi api;
	private volatile Status status = Status.CONNECTING;
	private volatile String message = "";
	private volatile @Nullable String accountId;
	private volatile long retryAt;

	private volatile @Nullable Account account;
	private volatile List<Trade> trades = List.of();
	private volatile long tradesUpdated;
	private volatile List<NavPoint> navHistory = List.of();
	private volatile Map<String, Instrument> instruments = Map.of();
	private final Map<String, Price> prices = new ConcurrentHashMap<>();
	private final Map<String, List<Candle>> candles = new ConcurrentHashMap<>();

	private volatile long accountWanted;
	private volatile long accountFetched;
	private final Map<String, Long> pricesWanted = new ConcurrentHashMap<>();
	private final Map<String, Long> candlesWanted = new ConcurrentHashMap<>();
	private final Map<String, Long> candlesFetched = new ConcurrentHashMap<>();

	private OandaData() {
		poller.scheduleWithFixedDelay(this::poll, 1, 1, TimeUnit.SECONDS);
	}

	public static OandaData get() {
		return INSTANCE;
	}

	private static java.util.concurrent.ThreadFactory daemon(String name) {
		return runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		};
	}

	// ---- What screens read ----

	public OandaConfig config() {
		return config;
	}

	public Status status() {
		return status;
	}

	public String message() {
		return message;
	}

	public @Nullable String accountId() {
		return accountId;
	}

	public @Nullable Account account() {
		accountWanted = System.currentTimeMillis();
		return account;
	}

	public List<Trade> trades() {
		accountWanted = System.currentTimeMillis();
		return trades;
	}

	/** When the request for the current open trades was sent, in milliseconds since the epoch, or 0 if there isn't one yet. */
	public long tradesUpdated() {
		return tradesUpdated;
	}

	/** The NAV recorded while the game was running and something showed account info, oldest first. */
	public List<NavPoint> navHistory() {
		accountWanted = System.currentTimeMillis();
		return navHistory;
	}

	public @Nullable Price price(String instrument) {
		pricesWanted.put(instrument, System.currentTimeMillis());
		return prices.get(instrument);
	}

	public @Nullable List<Candle> candles(String instrument, String granularity) {
		String key = instrument + "/" + granularity;
		candlesWanted.put(key, System.currentTimeMillis());
		return candles.get(key);
	}

	public Map<String, Instrument> instruments() {
		return instruments;
	}

	public int precision(String instrument) {
		Instrument info = instruments.get(instrument);
		return info != null ? info.precision() : instrument.contains("JPY") ? 3 : 5;
	}

	public String displayName(String instrument) {
		Instrument info = instruments.get(instrument);
		return info != null ? info.displayName() : instrument.replace('_', '/');
	}

	public String formatPrice(String instrument, double price) {
		return String.format("%." + precision(instrument) + "f", price);
	}

	// ---- Changes ----

	/** Re-reads the config file and reconnects, e.g. after the player pasted in a token. */
	public void reload() {
		poller.execute(() -> {
			config = OandaConfig.load();
			disconnect();
		});
	}

	public void setWatchlist(List<String> watchlist) {
		OandaConfig current = config;
		current.watchlist = new ArrayList<>(watchlist);
		poller.execute(current::save);
	}

	public CompletableFuture<String> marketOrder(String instrument, long units, @Nullable String stopLoss, @Nullable String takeProfit) {
		return act(api -> api.marketOrder(accountId, instrument, units, stopLoss, takeProfit));
	}

	public CompletableFuture<String> closeTrade(String tradeId) {
		return act(api -> api.closeTrade(accountId, tradeId));
	}

	private interface Action {
		String run(OandaApi api) throws OandaException;
	}

	private CompletableFuture<String> act(Action action) {
		return CompletableFuture.supplyAsync(() -> {
			OandaApi current = api;
			if (current == null || accountId == null) {
				return "Not connected to OANDA";
			}
			try {
				return action.run(current);
			} catch (OandaException e) {
				return "Failed: " + e.getMessage();
			} finally {
				accountFetched = 0;
			}
		}, actions);
	}

	// ---- Polling ----

	private void disconnect() {
		api = null;
		accountId = null;
		account = null;
		trades = List.of();
		tradesUpdated = 0;
		navHistory = List.of();
		instruments = Map.of();
		prices.clear();
		candles.clear();
		candlesFetched.clear();
		retryAt = 0;
		status = Status.CONNECTING;
		message = "";
	}

	private void poll() {
		try {
			long now = System.currentTimeMillis();
			boolean wanted = now - accountWanted < WANTED_FOR_MS
				|| pricesWanted.values().stream().anyMatch(t -> now - t < WANTED_FOR_MS)
				|| candlesWanted.values().stream().anyMatch(t -> now - t < WANTED_FOR_MS);
			if (!wanted) {
				return;
			}
			OandaConfig current = config;
			if (current.token().isEmpty()) {
				status = Status.NO_TOKEN;
				message = "Add your " + (current.live() ? "live" : "practice") + " token to config/tradingdesk.json, then press Reload";
				return;
			}
			if (api == null) {
				if (now < retryAt) {
					return;
				}
				connect(current);
				if (api == null) {
					return;
				}
			}
			refresh(now);
		} catch (RuntimeException e) {
			TradingDesk.LOGGER.warn("Trading desk poll failed", e);
		}
	}

	private void connect(OandaConfig current) {
		status = Status.CONNECTING;
		message = "Connecting to OANDA " + (current.live() ? "live" : "practice") + "...";
		OandaApi candidate = new OandaApi(current.baseUrl(), current.token());
		try {
			List<String> ids = candidate.accountIds();
			String id = current.accountId == null ? "" : current.accountId.trim();
			if (id.isEmpty()) {
				if (ids.isEmpty()) {
					throw new OandaException("This token has no accounts", false);
				}
				id = ids.getFirst();
			} else if (!ids.contains(id)) {
				throw new OandaException("Account " + id + " isn't one this token can use", false);
			}
			Map<String, Instrument> byName = new LinkedHashMap<>();
			for (Instrument instrument : candidate.instruments(id)) {
				byName.put(instrument.name(), instrument);
			}
			instruments = Map.copyOf(byName);
			navHistory = loadNav(id);
			accountId = id;
			api = candidate;
			status = Status.CONNECTED;
			message = "";
		} catch (OandaException e) {
			status = Status.ERROR;
			message = e.getMessage();
			retryAt = System.currentTimeMillis() + RETRY_AFTER_MS;
		}
	}

	private void refresh(long now) {
		OandaApi current = api;
		String id = accountId;
		if (current == null || id == null) {
			return;
		}
		try {
			if (now - accountWanted < WANTED_FOR_MS && now - accountFetched >= ACCOUNT_EVERY_MS) {
				long requested = System.currentTimeMillis();
				Account fetched = current.account(id);
				account = fetched;
				trades = List.copyOf(current.openTrades(id));
				tradesUpdated = requested;
				accountFetched = now;
				recordNav(id, fetched.nav(), now / 1000);
			}

			List<String> wantedPrices = new ArrayList<>();
			pricesWanted.forEach((instrument, time) -> {
				if (now - time < WANTED_FOR_MS && instruments.containsKey(instrument)) {
					wantedPrices.add(instrument);
				}
			});
			if (!wantedPrices.isEmpty()) {
				for (Price price : current.prices(id, wantedPrices)) {
					prices.put(price.instrument(), price);
				}
			}

			for (Map.Entry<String, Long> entry : candlesWanted.entrySet()) {
				String key = entry.getKey();
				if (now - entry.getValue() >= WANTED_FOR_MS) {
					continue;
				}
				String[] parts = key.split("/");
				if (!instruments.containsKey(parts[0])) {
					continue;
				}
				// The live price moves the newest candle in between, so whole refreshes can be spaced out.
				pricesWanted.put(parts[0], now);
				if (now - candlesFetched.getOrDefault(key, 0L) >= refreshEvery(parts[1])) {
					candles.put(key, List.copyOf(current.candles(parts[0], parts[1], CANDLE_COUNT)));
					candlesFetched.put(key, now);
				}
			}
			if (status != Status.CONNECTED) {
				status = Status.CONNECTED;
				message = "";
			}
		} catch (OandaException e) {
			message = e.getMessage();
			if (e.unauthorized) {
				status = Status.ERROR;
				api = null;
				retryAt = now + RETRY_AFTER_MS;
			}
		}
	}

	// ---- NAV history, kept in config/tradingdesk_nav.csv as "account,seconds,nav" lines ----

	private static Path navPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(TradingDesk.MOD_ID + "_nav.csv");
	}

	/** Reads this account's history, and rewrites the file without anything older than {@link #NAV_KEEP_SECONDS}. */
	private static List<NavPoint> loadNav(String id) {
		Path path = navPath();
		if (!Files.exists(path)) {
			return List.of();
		}
		long cutoff = System.currentTimeMillis() / 1000 - NAV_KEEP_SECONDS;
		List<String> kept = new ArrayList<>();
		List<NavPoint> points = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				String[] parts = line.split(",");
				if (parts.length != 3) {
					continue;
				}
				try {
					long time = Long.parseLong(parts[1]);
					if (time < cutoff) {
						continue;
					}
					kept.add(line);
					if (parts[0].equals(id)) {
						points.add(new NavPoint(time, Double.parseDouble(parts[2])));
					}
				} catch (NumberFormatException ignored) {
				}
			}
			Files.write(path, kept, StandardCharsets.UTF_8);
		} catch (IOException e) {
			TradingDesk.LOGGER.warn("Couldn't read NAV history: {}", e.getMessage());
		}
		return List.copyOf(points);
	}

	private void recordNav(String id, double nav, long time) {
		List<NavPoint> history = navHistory;
		if (!history.isEmpty() && time - history.getLast().time() < NAV_EVERY_SECONDS) {
			return;
		}
		List<NavPoint> updated = new ArrayList<>(history);
		updated.add(new NavPoint(time, nav));
		long cutoff = time - NAV_KEEP_SECONDS;
		updated.removeIf(point -> point.time() < cutoff);
		navHistory = List.copyOf(updated);
		try {
			Files.writeString(navPath(), id + "," + time + "," + nav + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			TradingDesk.LOGGER.warn("Couldn't save NAV history: {}", e.getMessage());
		}
	}

	private static long refreshEvery(String granularity) {
		return switch (granularity) {
			case "M1" -> 10_000;
			case "M5" -> 20_000;
			case "M15" -> 30_000;
			default -> 60_000;
		};
	}
}
