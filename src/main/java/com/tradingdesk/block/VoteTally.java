package com.tradingdesk.block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Counts the votes: twice a second, every mob standing on a vote plate (players, villagers, animals, monsters, bots)
 * counts toward the nearest vote counter within {@link #RANGE} blocks of that plate. Stepping off takes the vote away.
 */
public final class VoteTally {
	/** How far from a vote plate its counter can be, in blocks. */
	public static final int RANGE = 32;
	private static final int INTERVAL_TICKS = 10;
	/** Counters that had votes last time, per level, so they can go back to zero when everyone steps off. */
	private static final Map<ServerLevel, Set<BlockPos>> counted = new WeakHashMap<>();
	/** Vote plates something has stepped on and may still be standing on, per level. */
	private static final Map<ServerLevel, Set<BlockPos>> occupied = new WeakHashMap<>();

	private VoteTally() {
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(VoteTally::tick);
	}

	/** Remembers a vote plate something is standing on, until nothing is. */
	static void pressed(Level level, BlockPos pos) {
		if (level instanceof ServerLevel serverLevel) {
			occupied.computeIfAbsent(serverLevel, l -> new HashSet<>()).add(pos.immutable());
		}
	}

	private static void tick(ServerLevel level) {
		if (level.getGameTime() % INTERVAL_TICKS != 0) {
			return;
		}
		Map<BlockPos, int[]> tallies = new HashMap<>();
		Set<BlockPos> plates = occupied.getOrDefault(level, Set.of());
		for (Iterator<BlockPos> it = plates.iterator(); it.hasNext(); ) {
			BlockPos platePos = it.next();
			int voters = level.isLoaded(platePos) && level.getBlockState(platePos).getBlock() instanceof VotePlateBlock plate
				? plate.voters(level, platePos) : 0;
			if (voters == 0) {
				it.remove();
				continue;
			}
			BlockPos counter = nearestCounter(level, platePos);
			if (counter != null) {
				boolean yes = ((VotePlateBlock) level.getBlockState(platePos).getBlock()).yes();
				tallies.computeIfAbsent(counter, pos -> new int[2])[yes ? 0 : 1] += voters;
			}
		}
		for (BlockPos pos : counted.getOrDefault(level, Set.of())) {
			if (!tallies.containsKey(pos) && level.getBlockEntity(pos) instanceof ChartScreenBlockEntity screen) {
				screen.setVotes(0, 0);
			}
		}
		tallies.forEach((pos, votes) -> {
			if (level.getBlockEntity(pos) instanceof ChartScreenBlockEntity screen) {
				screen.setVotes(votes[0], votes[1]);
			}
		});
		counted.put(level, new HashSet<>(tallies.keySet()));
	}

	/** The anchor of the vote counter with a screen closest to pos, within range, looking only in loaded chunks. */
	private static @Nullable BlockPos nearestCounter(ServerLevel level, BlockPos pos) {
		ChartScreenBlockEntity best = null;
		double bestDistance = (double) RANGE * RANGE;
		int chunkRange = (RANGE >> 4) + 1;
		int centerX = pos.getX() >> 4;
		int centerZ = pos.getZ() >> 4;
		for (int cx = centerX - chunkRange; cx <= centerX + chunkRange; cx++) {
			for (int cz = centerZ - chunkRange; cz <= centerZ + chunkRange; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(cx, cz);
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (blockEntity instanceof ChartScreenBlockEntity screen && screen.isVotes()) {
						double distance = screen.getBlockPos().distSqr(pos);
						if (distance <= bestDistance) {
							best = screen;
							bestDistance = distance;
						}
					}
				}
			}
		}
		if (best == null) {
			return null;
		}
		BlockState state = best.getBlockState();
		if (!(state.getBlock() instanceof ChartScreenBlock)) {
			return null;
		}
		return ChartGroup.of(level, best.getBlockPos(), state.getValue(ChartScreenBlock.FACING)).anchor();
	}
}
