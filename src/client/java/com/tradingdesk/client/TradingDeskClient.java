package com.tradingdesk.client;

import com.tradingdesk.ClientHooks;
import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.render.ChartScreenRenderer;
import com.tradingdesk.client.render.TradingDeskRenderer;
import com.tradingdesk.client.screen.ChartSettingsScreen;
import com.tradingdesk.client.screen.DeskScreen;
import com.tradingdesk.registry.ModBlockEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class TradingDeskClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlockEntities.TRADING_DESK, TradingDeskRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.CHART_SCREEN, ChartScreenRenderer::new);
		ClientHooks.openDesk = () -> Minecraft.getInstance().gui.setScreen(new DeskScreen());
		ClientHooks.openChartSettings = pos -> Minecraft.getInstance().gui.setScreen(new ChartSettingsScreen(pos));
		ClientHooks.tradeButton = TradeButtons::pressed;
		ClientTickEvents.END_CLIENT_TICK.register(VoteTrader::tick);
		OandaData.get();
	}
}
