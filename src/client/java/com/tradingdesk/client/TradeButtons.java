package com.tradingdesk.client;

import org.jspecify.annotations.Nullable;

import com.tradingdesk.block.ChartScreenBlockEntity;
import com.tradingdesk.client.screen.OrderTicket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** What happens on the pressing player's side when they press a buy or sell button. */
final class TradeButtons {
	/** How far from a button its master chart can be, in blocks. */
	private static final int RANGE = 32;

	private TradeButtons() {
	}

	static void pressed(BlockPos pos, boolean buy) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		ChartScreenBlockEntity master = nearestMaster(minecraft.level, pos);
		if (master == null) {
			tell(minecraft, "No master chart within " + RANGE + " blocks. Right-click a chart screen and turn on Master chart.", true);
			return;
		}
		String problem = OrderTicket.confirm(minecraft, master.instrument(), buy, null, message -> tell(minecraft, message, OrderTicket.isFailure(message)));
		if (problem != null) {
			tell(minecraft, problem, true);
		}
	}

	/** The closest set-up master chart screen within range, looking through the loaded chunks around pos. */
	private static @Nullable ChartScreenBlockEntity nearestMaster(ClientLevel level, BlockPos pos) {
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
					if (blockEntity instanceof ChartScreenBlockEntity screen && screen.master() && screen.isSetUp()) {
						double distance = screen.getBlockPos().distSqr(pos);
						if (distance <= bestDistance) {
							best = screen;
							bestDistance = distance;
						}
					}
				}
			}
		}
		return best;
	}

	private static void tell(Minecraft minecraft, String message, boolean bad) {
		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(Component.literal(message).withColor(bad ? 0xFFF85149 : 0xFF3FB950));
		}
	}
}
