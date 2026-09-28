package com.tradingdesk.registry;

import com.tradingdesk.block.ChartScreenBlock;
import com.tradingdesk.block.TradingDeskBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	/** The desk with a monitor on it. Right-click to open the trading terminal. */
	public static final Block TRADING_DESK = Register.block(
		"trading_desk",
		TradingDeskBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(2.5F)
			.sound(SoundType.WOOD)
			.noOcclusion()
	);

	/** A flat screen hung on a wall that shows a live chart. Screens side by side merge into one big chart. */
	public static final Block CHART_SCREEN = Register.block(
		"chart_screen",
		ChartScreenBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(1.0F)
			.sound(SoundType.METAL)
			.noOcclusion()
			.noCollision()
			.lightLevel(state -> 6)
	);

	private ModBlocks() {
	}

	public static void initialize() {
	}
}
