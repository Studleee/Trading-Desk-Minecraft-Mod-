package com.tradingdesk.client.oanda;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.tradingdesk.TradingDesk;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The player's OANDA settings, kept in {@code config/tradingdesk.json} on their own computer. The tokens are only ever
 * sent to OANDA. {@code environment} picks the practice (demo) account or the live one; it's practice unless the
 * player writes "live".
 */
public final class OandaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public String environment = "practice";
	public String practiceToken = "";
	public String liveToken = "";
	/** Blank means the first account the token can see. */
	public String accountId = "";
	public List<String> watchlist = new ArrayList<>(List.of("EUR_USD", "GBP_USD", "USD_JPY", "XAU_USD"));
	public long defaultUnits = 1000;

	public boolean live() {
		return "live".equalsIgnoreCase(environment == null ? "" : environment.trim());
	}

	public String token() {
		String token = live() ? liveToken : practiceToken;
		return token == null ? "" : token.trim();
	}

	public String baseUrl() {
		return live() ? "https://api-fxtrade.oanda.com" : "https://api-fxpractice.oanda.com";
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(TradingDesk.MOD_ID + ".json");
	}

	/** Reads the config, writing a blank one to fill in if there isn't one yet. */
	public static OandaConfig load() {
		Path path = path();
		OandaConfig config = null;
		if (Files.exists(path)) {
			try {
				config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), OandaConfig.class);
			} catch (IOException | JsonParseException e) {
				TradingDesk.LOGGER.warn("Couldn't read {}: {}", path, e.getMessage());
			}
		}
		if (config == null) {
			config = new OandaConfig();
			if (!Files.exists(path)) {
				config.save();
			}
		}
		if (config.watchlist == null) {
			config.watchlist = new ArrayList<>();
		}
		if (config.defaultUnits <= 0) {
			config.defaultUnits = 1000;
		}
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TradingDesk.LOGGER.warn("Couldn't save {}: {}", path(), e.getMessage());
		}
	}
}
