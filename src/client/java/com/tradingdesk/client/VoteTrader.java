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
 * At the end of each voting round, sets the player's position on the nearest master chart to the vote: net votes times
 * the counter's units per vote, long when positive, short when negative, flat at zero. Only vote counters with
 * auto-trade on, that this player turned on, and that are near this player count. A round's order waits until the
 * open trades have been fetched again since the last order, so it's always sized from the real position.
 */
final class VoteTrader {
	private static final int SEARCH_RANGE = 64;
	private static final int INTERVAL_TICKS = 10;

	private static final Map<BlockPos, Counter> counters = new HashMap<>();

	private static final class Counter {
		long roundTicks;
		long round = -1;
		/** A round has ended and its order hasn't gone in yet. */
		boolean due;
		/** The vote when that round ended. */
		int dueNet;
		boolean ordering;
		long orderedAt;
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
		long gameTime = level.getGameTime();
		UUID me = minecraft.player.getUUID();
		Set<BlockPos> seen = new HashSet<>();
		for (ChartScreenBlockEntity screen : myCounters(level, minecraft.player.blockPosition(), me)) {
			BlockPos pos = screen.getBlockPos();
			seen.add(pos);
			Counter counter = counters.computeIfAbsent(pos, p -> new Counter());
			long roundTicks = screen.settings().roundTicks();
			long round = screen.round(gameTime);
			if (counter.round < 0 || counter.roundTicks != roundTicks) {
				counter.roundTicks = roundTicks;
				counter.round = round;
			} else if (round != counter.round) {
				counter.round = round;
				counter.due = true;
				counter.dueNet = screen.yesVotes() - screen.noVotes();
			}
			if (counter.due) {
				trade(minecraft, level, screen, counter);
			}
		}
		counters.keySet().retainAll(seen);
	}

	private static void trade(Minecraft minecraft, ClientLevel level, ChartScreenBlockEntity screen, Counter counter) {
		OandaData data = OandaData.get();
		var trades = data.trades();
		if (counter.ordering || data.status() != OandaData.Status.CONNECTED || data.tradesUpdated() <= counter.orderedAt) {
			return;
		}
		counter.due = false;
		ChartScreenBlockEntity master = TradeButtons.nearestMaster(level, screen.getBlockPos());
		if (master == null) {
			TradeButtons.tell(minecraft, "Vote round over, but there's no master chart within " + TradeButtons.RANGE + " blocks to trade.", true);
			return;
		}
		String instrument = master.instrument();
		long target = (long) counter.dueNet * screen.unitsPerVote();
		long current = 0;
		for (Trade trade : trades) {
			if (trade.instrument().equals(instrument)) {
				current += trade.units();
			}
		}
		long change = target - current;
		String vote = String.format("Vote round over (%+d)", counter.dueNet).replace("+0", "0");
		if (change == 0) {
			TradeButtons.tell(minecraft, vote + ": position already " + describe(target) + " " + data.displayName(instrument), false);
			return;
		}
		counter.ordering = true;
		TradeButtons.tell(minecraft, String.format("%s: %s %,d %s", vote, change > 0 ? "buying" : "selling", Math.abs(change), data.displayName(instrument)), false);
		data.marketOrder(instrument, change, null, null).thenAccept(message -> minecraft.execute(() -> {
			counter.ordering = false;
			counter.orderedAt = System.currentTimeMillis();
			TradeButtons.tell(minecraft, "Vote round: " + message, OrderTicket.isFailure(message));
		}));
	}

	private static String describe(long units) {
		return units == 0 ? "flat" : String.format("%s %,d", units > 0 ? "long" : "short", Math.abs(units));
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
