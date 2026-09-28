package com.tradingdesk;

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

	private ClientHooks() {
	}
}
