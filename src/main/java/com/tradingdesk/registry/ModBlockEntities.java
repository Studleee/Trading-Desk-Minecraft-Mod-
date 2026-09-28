package com.tradingdesk.registry;

import java.util.Set;

import com.tradingdesk.TradingDesk;
import com.tradingdesk.block.ChartScreenBlockEntity;
import com.tradingdesk.block.TradingDeskBlockEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<TradingDeskBlockEntity> TRADING_DESK = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		TradingDesk.id("trading_desk"),
		new BlockEntityType<>(TradingDeskBlockEntity::new, Set.of(ModBlocks.TRADING_DESK))
	);

	public static final BlockEntityType<ChartScreenBlockEntity> CHART_SCREEN = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		TradingDesk.id("chart_screen"),
		new BlockEntityType<>(ChartScreenBlockEntity::new, Set.of(ModBlocks.CHART_SCREEN))
	);

	private ModBlockEntities() {
	}

	public static void initialize() {
	}
}
