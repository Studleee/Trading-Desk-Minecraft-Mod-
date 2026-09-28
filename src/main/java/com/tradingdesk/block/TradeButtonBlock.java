package com.tradingdesk.block;

import com.tradingdesk.ClientHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A stone button that buys (green) or sells (red) the market on the nearest master chart, using the desk's order
 * ticket. The order goes through the pressing player's own OANDA account, after they confirm it. It still gives a
 * redstone pulse like any button.
 */
public class TradeButtonBlock extends ButtonBlock {
	private final boolean buy;

	public TradeButtonBlock(boolean buy, Properties properties) {
		super(BlockSetType.STONE, 20, properties);
		this.buy = buy;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide() && !state.getValue(POWERED)) {
			ClientHooks.tradeButton.accept(pos, buy);
		}
		return super.useWithoutItem(state, level, pos, player, hitResult);
	}
}
