package com.tradingdesk;

import com.tradingdesk.block.VoteTally;
import com.tradingdesk.network.ModNetworking;
import com.tradingdesk.registry.ModBlockEntities;
import com.tradingdesk.registry.ModBlocks;
import com.tradingdesk.registry.ModCreativeTab;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradingDesk implements ModInitializer {
	public static final String MOD_ID = "tradingdesk";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModCreativeTab.initialize();
		ModNetworking.initialize();
		VoteTally.initialize();
		LOGGER.info("{} loaded", MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
