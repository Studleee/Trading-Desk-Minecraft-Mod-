package com.tradingdesk.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A stone pressure plate that votes on the nearest vote counter for every mob standing on it (players, villagers,
 * animals, monsters, bots): green for yes (+1 each), red for no (-1 each). See {@link VoteTally}. It still gives a
 * redstone signal like a stone pressure plate.
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

	/** How many mobs are standing on the plate at pos. */
	public int voters(Level level, BlockPos pos) {
		return getEntityCount(level, TOUCH_AABB.move(pos), LivingEntity.class);
	}

	/** Called whenever something steps on the plate and while it stays pressed, which is how the tally finds it. */
	@Override
	protected int getSignalStrength(Level level, BlockPos pos) {
		int signal = super.getSignalStrength(level, pos);
		if (signal > 0) {
			VoteTally.pressed(level, pos);
		}
		return signal;
	}
}
