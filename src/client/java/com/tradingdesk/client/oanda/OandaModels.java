package com.tradingdesk.client.oanda;

import org.jspecify.annotations.Nullable;

/** What the desk and charts show, parsed from OANDA's replies. */
public final class OandaModels {
	private OandaModels() {
	}

	/** An account summary. Realized P/L is the account's lifetime total. */
	public record Account(
		String id, String currency, double balance, double nav, double unrealizedPl, double realizedPl, double marginUsed, double marginAvailable
	) {
	}

	/** The account's NAV at a moment, in seconds since 1970. */
	public record NavPoint(long time, double nav) {
	}

	public record Price(String instrument, double bid, double ask, boolean tradeable) {
		public double mid() {
			return (bid + ask) / 2.0;
		}
	}

	/** An open trade; units are negative for a sell. */
	public record Trade(
		String id, String instrument, long units, double entry, double unrealizedPl, @Nullable Double stopLoss, @Nullable Double takeProfit
	) {
	}

	public record Candle(double open, double high, double low, double close) {
	}

	/** A tradeable instrument: its API name like {@code EUR_USD}, its display name, and how many decimals prices have. */
	public record Instrument(String name, String displayName, int precision) {
	}
}
