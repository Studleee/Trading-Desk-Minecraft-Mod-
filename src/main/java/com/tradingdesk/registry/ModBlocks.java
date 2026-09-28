package com.tradingdesk.registry;

import com.tradingdesk.block.ChartScreenBlock;
import com.tradingdesk.block.TradeButtonBlock;
import com.tradingdesk.block.TradingDeskBlock;
import com.tradingdesk.block.VotePlateBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

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

	/** Buys the market on the nearest master chart, using the desk's order ticket. */
	public static final Block BUY_BUTTON = Register.block(
		"buy_button",
		properties -> new TradeButtonBlock(true, properties),
		buttonProperties(MapColor.COLOR_GREEN)
	);

	/** Sells the market on the nearest master chart, using the desk's order ticket. */
	public static final Block SELL_BUTTON = Register.block(
		"sell_button",
		properties -> new TradeButtonBlock(false, properties),
		buttonProperties(MapColor.COLOR_RED)
	);

	/** Votes yes (+1) on the nearest vote counter while a player stands on it. */
	public static final Block GREEN_VOTE_PLATE = Register.block(
		"green_vote_plate",
		properties -> new VotePlateBlock(true, properties),
		plateProperties(MapColor.COLOR_GREEN)
	);

	/** Votes no (-1) on the nearest vote counter while a player stands on it. */
	public static final Block RED_VOTE_PLATE = Register.block(
		"red_vote_plate",
		properties -> new VotePlateBlock(false, properties),
		plateProperties(MapColor.COLOR_RED)
	);

	private static BlockBehaviour.Properties plateProperties(MapColor color) {
		return BlockBehaviour.Properties.of()
			.mapColor(color)
			.forceSolidOn()
			.instrument(NoteBlockInstrument.BASEDRUM)
			.noCollision()
			.strength(0.5F)
			.pushReaction(PushReaction.POPPED);
	}

	private static BlockBehaviour.Properties buttonProperties(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).noCollision().strength(0.5F).pushReaction(PushReaction.POPPED);
	}

	private ModBlocks() {
	}

	public static void initialize() {
	}
}
