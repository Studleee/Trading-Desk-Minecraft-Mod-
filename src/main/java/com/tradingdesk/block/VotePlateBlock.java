package com.tradingdesk.block;

import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A stone pressure plate that votes on the nearest vote counter while a player stands on it: green for yes (+1), red
 * for no (-1). See {@link VoteTally}. It still gives a redstone signal like a stone pressure plate.
 */
public class VotePlateBlock extends PressurePlateBlock {
	private final boolean yes;

	public VotePlateBlock(boolean yes, Properties properties) {
		super(BlockSetType.STONE, properties);
		this.yes = yes;
	}

	public boolean yes() {
		return yes;
	}
}
