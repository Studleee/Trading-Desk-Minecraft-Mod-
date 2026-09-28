package com.tradingdesk.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.tradingdesk.block.ChartGroup;
import com.tradingdesk.block.ChartScreenBlock;
import com.tradingdesk.block.ChartScreenBlockEntity;
import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.oanda.OandaModels.Trade;
import com.tradingdesk.client.screen.OrderTicket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Keeps the player's position on the nearest master chart at the vote: net votes times the counter's units per vote,
 * long when positive, short when negative, flat at zero. Only vote counters with auto-trade on, that this player turned
 * on, and that are near this player count. An order goes in only once the vote has held for {@link #STEADY_MS}, and
 * only once the open trades have been fetched again since the last order, so one change never trades twice.
 */
final class VoteTrader {
	private static final long STEADY_MS = 3000;
	private static final long RETRY_AFTER_FAILURE_MS = 30_000;
	private static final int SEARCH_RANGE = 64;
	private static final int INTERVAL_TICKS = 10;

	private static final Map<BlockPos, Counter> counters = new HashMap<>();

	private static final class Counter {
		int net = Integer.MIN_VALUE;
		long steadySince;
		boolean ordering;
		long orderedAt;
		long retryAt;
		boolean warnedNoMaster;
	}

	private VoteTrader() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			counters.clear();
			return;
		}
		if (level.getGameTime() % INTERVAL_TICKS != 0) {
			return;
		}
		long now = System.currentTimeMillis();
		UUID me = minecraft.player.getUUID();
		Set<BlockPos> seen = new HashSet<>();
		for (ChartScreenBlockEntity screen : myCounters(level, minecraft.player.blockPosition(), me)) {
			BlockPos pos = screen.getBlockPos();
			seen.add(pos);
			Counter counter = counters.computeIfAbsent(pos, p -> new Counter());
			int net = screen.yesVotes() - screen.noVotes();
			if (net != counter.net) {
				counter.net = net;
				counter.steadySince = now;
			}
			update(minecraft, level, screen, counter, now);
		}
		counters.keySet().retainAll(seen);
	}

	private static void update(Minecraft minecraft, ClientLevel level, ChartScreenBlockEntity screen, Counter counter, long now) {
		OandaData data = OandaData.get();
		var trades = data.trades();
		if (counter.ordering || now - counter.steadySince < STEADY_MS || now < counter.retryAt
			|| data.status() != OandaData.Status.CONNECTED || data.tradesUpdated() <= counter.orderedAt) {
			return;
		}
		ChartScreenBlockEntity master = TradeButtons.nearestMaster(level, screen.getBlockPos());
		if (master == null) {
			if (!counter.warnedNoMaster) {
				counter.warnedNoMaster = true;
				TradeButtons.tell(minecraft, "Vote counter: no master chart within " + TradeButtons.RANGE + " blocks to trade.", true);
			}
			return;
		}
		counter.warnedNoMaster = false;
		String instrument = master.instrument();
		long target = (long) counter.net * screen.unitsPerVote();
		long current = 0;
		for (Trade trade : trades) {
			if (trade.instrument().equals(instrument)) {
				current += trade.units();
			}
		}
		long change = target - current;
		if (change == 0) {
			return;
		}
		counter.ordering = true;
		TradeButtons.tell(minecraft, String.format("Vote %+d: %s %,d %s", counter.net, change > 0 ? "buying" : "selling", Math.abs(change), data.displayName(instrument)), false);
		data.marketOrder(instrument, change, null, null).thenAccept(message -> minecraft.execute(() -> {
			counter.ordering = false;
			counter.orderedAt = System.currentTimeMillis();
			boolean failed = OrderTicket.isFailure(message);
			if (failed) {
				counter.retryAt = counter.orderedAt + RETRY_AFTER_FAILURE_MS;
			}
			TradeButtons.tell(minecraft, "Vote counter: " + message, failed);
		}));
	}

	/** The anchors of vote counters near pos, in loaded chunks, with auto-trade turned on by this player. */
	private static Set<ChartScreenBlockEntity> myCounters(ClientLevel level, BlockPos pos, UUID me) {
		Set<ChartScreenBlockEntity> found = new HashSet<>();
		int chunkRange = (SEARCH_RANGE >> 4) + 1;
		int centerX = pos.getX() >> 4;
		int centerZ = pos.getZ() >> 4;
		for (int cx = centerX - chunkRange; cx <= centerX + chunkRange; cx++) {
			for (int cz = centerZ - chunkRange; cz <= centerZ + chunkRange; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(cx, cz);
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof ChartScreenBlockEntity screen) || !screen.autoTrade() || !me.equals(screen.owner())) {
						continue;
					}
					BlockState state = screen.getBlockState();
					if (state.getBlock() instanceof ChartScreenBlock
						&& ChartGroup.of(level, screen.getBlockPos(), state.getValue(ChartScreenBlock.FACING)).anchor().equals(screen.getBlockPos())
						&& screen.getBlockPos().closerThan(pos, SEARCH_RANGE)) {
						found.add(screen);
					}
				}
			}
		}
		return found;
	}
}
