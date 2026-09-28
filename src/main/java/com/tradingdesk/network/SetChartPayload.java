package com.tradingdesk.network;

import com.tradingdesk.TradingDesk;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by a player who changed a chart's settings: the screen they used, and what it should show. */
public record SetChartPayload(BlockPos pos, String instrument, String granularity, boolean showTrades, boolean master) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetChartPayload> TYPE = new CustomPacketPayload.Type<>(TradingDesk.id("set_chart"));
	public static final StreamCodec<ByteBuf, SetChartPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, SetChartPayload::pos,
		ByteBufCodecs.stringUtf8(32), SetChartPayload::instrument,
		ByteBufCodecs.stringUtf8(8), SetChartPayload::granularity,
		ByteBufCodecs.BOOL, SetChartPayload::showTrades,
		ByteBufCodecs.BOOL, SetChartPayload::master,
		SetChartPayload::new
	);

	@Override
	public Type<SetChartPayload> type() {
		return TYPE;
	}
}
