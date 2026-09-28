package com.tradingdesk.block;

import com.tradingdesk.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing; it's only there so the desk's monitor can show live account info. */
public class TradingDeskBlockEntity extends BlockEntity {
	public TradingDeskBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TRADING_DESK, pos, state);
	}
}
