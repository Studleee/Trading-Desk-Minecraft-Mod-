package com.tradingdesk;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

/**
 * Lets blocks open client-only screens without referring to client classes. The client entrypoint fills these in;
 * on a dedicated server they stay as do-nothing defaults.
 */
public final class ClientHooks {
	public static Runnable openDesk = () -> {
	};
	public static Consumer<BlockPos> openChartSettings = pos -> {
	};
	/** A buy (true) or sell (false) button was pressed at pos. */
	public static BiConsumer<BlockPos, Boolean> tradeButton = (pos, buy) -> {
	};

	private ClientHooks() {
	}
}
