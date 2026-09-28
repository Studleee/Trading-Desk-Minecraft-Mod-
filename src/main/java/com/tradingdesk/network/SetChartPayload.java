package com.tradingdesk.network;

import com.tradingdesk.TradingDesk;
import com.tradingdesk.block.ChartScreenBlockEntity.Settings;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by a player who changed a chart screen's settings: the screen they used, and what it should show. */
public record SetChartPayload(
	BlockPos pos, String mode, String instrument, String granularity, boolean showTrades, boolean master, int unitsPerVote, int roundMinutes,
	boolean autoTrade
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetChartPayload> TYPE = new CustomPacketPayload.Type<>(TradingDesk.id("set_chart"));
	public static final StreamCodec<ByteBuf, SetChartPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, SetChartPayload::pos,
		ByteBufCodecs.stringUtf8(16), SetChartPayload::mode,
		ByteBufCodecs.stringUtf8(32), SetChartPayload::instrument,
		ByteBufCodecs.stringUtf8(8), SetChartPayload::granularity,
		ByteBufCodecs.BOOL, SetChartPayload::showTrades,
		ByteBufCodecs.BOOL, SetChartPayload::master,
		ByteBufCodecs.VAR_INT, SetChartPayload::unitsPerVote,
		ByteBufCodecs.VAR_INT, SetChartPayload::roundMinutes,
		ByteBufCodecs.BOOL, SetChartPayload::autoTrade,
		SetChartPayload::new
	);

	public SetChartPayload(BlockPos pos, Settings settings) {
		this(pos, settings.mode(), settings.instrument(), settings.granularity(), settings.showTrades(), settings.master(),
			settings.unitsPerVote(), settings.roundMinutes(), settings.autoTrade());
	}

	public Settings settings() {
		return new Settings(mode, instrument, granularity, showTrades, master, unitsPerVote, roundMinutes, autoTrade);
	}

	@Override
	public Type<SetChartPayload> type() {
		return TYPE;
	}
}
