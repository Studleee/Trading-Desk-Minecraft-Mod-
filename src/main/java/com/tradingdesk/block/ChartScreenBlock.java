package com.tradingdesk.block;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.tradingdesk.ClientHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin screen hung on a wall, like a painting, that shows a live chart. Screens touching side by side or above each
 * other (facing the same way) merge into one big chart; see {@link ChartGroup}. FACING is the way the screen faces.
 */
public class ChartScreenBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(0, 0, 15, 16, 16, 16));

	public ChartScreenBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Faces out from the wall it's placed on; placed on a floor or ceiling, it faces the player. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing);
	}

	/** A new screen added to an existing chart picks up its settings, so the chart doesn't reset. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ChartScreenBlockEntity placed)) {
			return;
		}
		Direction facing = state.getValue(FACING);
		Direction right = ChartGroup.right(facing);
		for (Direction side : new Direction[] {right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
			BlockPos next = pos.relative(side);
			if (ChartGroup.isScreen(level, next, facing) && level.getBlockEntity(next) instanceof ChartScreenBlockEntity other && other.isSetUp()) {
				placed.apply(other.settings(), other.owner());
				return;
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			ClientHooks.openChartSettings.accept(pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChartScreenBlockEntity(pos, state);
	}
}
