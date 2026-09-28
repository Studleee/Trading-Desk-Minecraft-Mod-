package com.tradingdesk.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tradingdesk.block.ChartGroup;
import com.tradingdesk.block.ChartScreenBlock;
import com.tradingdesk.block.ChartScreenBlockEntity;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a chart across a whole group of screens. Only the group's top-left screen draws; the others leave it to it.
 */
public class ChartScreenRenderer implements BlockEntityRenderer<ChartScreenBlockEntity, ChartScreenRenderer.State> {
	static final int FULL_BRIGHT = 0xF000F0;
	/** Drawing units per block. */
	private static final float UNITS = 64.0F;
	private static final float BEZEL = 1.5F;

	private final Font font;

	public ChartScreenRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(
		ChartScreenBlockEntity screen, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(screen, state, partialTicks, cameraPosition, breakProgress);
		state.draw = false;
		if (screen.getLevel() == null || !(screen.getBlockState().getBlock() instanceof ChartScreenBlock)) {
			return;
		}
		Direction facing = screen.getBlockState().getValue(ChartScreenBlock.FACING);
		ChartGroup group = ChartGroup.of(screen.getLevel(), screen.getBlockPos(), facing);
		if (!group.anchor().equals(screen.getBlockPos())) {
			return;
		}
		state.draw = true;
		state.facing = facing;
		state.width = group.width();
		state.height = group.height();
		state.mode = screen.mode();
		state.instrument = screen.instrument();
		state.granularity = screen.granularity();
		state.showTrades = screen.showTrades();
		state.master = screen.master();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.draw) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
		// The screen's front, one pixel out from the wall, starting at the top-left corner.
		poseStack.translate(-0.5F, 0.5F, -0.5F + 1.0F / 16.0F + 0.002F);
		poseStack.scale(1.0F / UNITS, -1.0F / UNITS, 1.0F / UNITS);
		poseStack.translate(BEZEL, BEZEL, 0.0F);
		float textScale = Math.clamp(0.35F * Math.min(state.width, state.height) + 0.35F, 0.7F, 2.0F);
		Canvas canvas = new Canvas(poseStack, collector, font, FULL_BRIGHT);
		float w = state.width * UNITS - 2 * BEZEL;
		float h = state.height * UNITS - 2 * BEZEL;
		if (state.mode.equals("chart")) {
			ChartPainter.paint(canvas, w, h, textScale, state.instrument, state.granularity, state.showTrades, state.master, null);
		} else {
			BoardPainter.paint(canvas, w, h, textScale, state.mode);
		}
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	public static class State extends BlockEntityRenderState {
		boolean draw;
		Direction facing = Direction.NORTH;
		int width = 1;
		int height = 1;
		String mode = "chart";
		String instrument = "";
		String granularity = "M15";
		boolean showTrades;
		boolean master;
	}
}
