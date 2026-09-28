package com.tradingdesk.network;

import com.tradingdesk.block.ChartGroup;
import com.tradingdesk.block.ChartScreenBlock;
import com.tradingdesk.block.ChartScreenBlockEntity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(SetChartPayload.TYPE, SetChartPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetChartPayload.TYPE, (payload, context) -> setChart(context.player(), payload));
	}

	/**
	 * Applies new settings to every screen in the chart, if the player can reach the one they used. Whoever saves a
	 * vote counter with auto-trade on becomes the player whose account it trades.
	 */
	private static void setChart(ServerPlayer player, SetChartPayload payload) {
		BlockPos pos = payload.pos();
		ServerLevel level = player.level();
		ChartScreenBlockEntity.Settings settings = payload.settings();
		if (!player.isWithinBlockInteractionRange(pos, 4.0) || !settings.isValid()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ChartScreenBlock)) {
			return;
		}
		for (BlockPos member : ChartGroup.of(level, pos, state.getValue(ChartScreenBlock.FACING)).members()) {
			if (level.getBlockEntity(member) instanceof ChartScreenBlockEntity screen) {
				screen.apply(settings, player.getUUID());
			}
		}
	}
}
