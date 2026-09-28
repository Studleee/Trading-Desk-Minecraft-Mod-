package com.tradingdesk.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rectangle of chart screens facing the same way, drawn as one chart. The anchor is the top-left screen as seen from
 * the front; it draws the whole chart and its settings are the chart's. Any screen in a filled rectangle finds the
 * same anchor. Screens that stick out of a rectangle show as their own small chart.
 */
public record ChartGroup(BlockPos anchor, int width, int height, Direction facing) {
	public static final int MAX_SIZE = 12;

	/** The viewer's right, looking at a screen that faces {@code facing}. */
	public static Direction right(Direction facing) {
		return facing.getCounterClockWise();
	}

	public static boolean isScreen(BlockGetter level, BlockPos pos, Direction facing) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof ChartScreenBlock && state.getValue(ChartScreenBlock.FACING) == facing;
	}

	public static ChartGroup of(BlockGetter level, BlockPos pos, Direction facing) {
		Direction right = right(facing);
		Direction left = right.getOpposite();
		BlockPos anchor = pos;
		for (int pass = 0; pass < 3; pass++) {
			for (int i = 0; i < MAX_SIZE && isScreen(level, anchor.relative(left), facing); i++) {
				anchor = anchor.relative(left);
			}
			for (int i = 0; i < MAX_SIZE && isScreen(level, anchor.above(), facing); i++) {
				anchor = anchor.above();
			}
		}
		int width = 1;
		while (width < MAX_SIZE && isScreen(level, anchor.relative(right, width), facing)) {
			width++;
		}
		int height = 1;
		while (height < MAX_SIZE && isScreen(level, anchor.below(height), facing)) {
			height++;
		}
		for (int row = 1; row < height; row++) {
			for (int column = 0; column < width; column++) {
				if (!isScreen(level, anchor.relative(right, column).below(row), facing)) {
					height = row;
					break;
				}
			}
		}
		ChartGroup group = new ChartGroup(anchor, width, height, facing);
		return group.contains(pos) ? group : new ChartGroup(pos, 1, 1, facing);
	}

	public boolean contains(BlockPos pos) {
		Direction right = right(facing);
		int along = (pos.getX() - anchor.getX()) * right.getStepX() + (pos.getZ() - anchor.getZ()) * right.getStepZ();
		int down = anchor.getY() - pos.getY();
		BlockPos expected = anchor.relative(right, along).below(down);
		return expected.equals(pos) && along >= 0 && along < width && down >= 0 && down < height;
	}

	public List<BlockPos> members() {
		Direction right = right(facing);
		List<BlockPos> members = new ArrayList<>();
		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				members.add(anchor.relative(right, column).below(row));
			}
		}
		return members;
	}
}
