package com.tradingdesk.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tradingdesk.block.TradingDeskBlock;
import com.tradingdesk.block.TradingDeskBlockEntity;
import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.oanda.OandaModels.Account;
import com.tradingdesk.client.screen.DeskScreen;

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
 * Shows the account's NAV and open P/L on the desk's monitor, over a small chart of the market last picked in the
 * terminal.
 */
public class TradingDeskRenderer implements BlockEntityRenderer<TradingDeskBlockEntity, TradingDeskRenderer.State> {
	private static final float UNITS = 64.0F;
	private static final float PIXEL = 1.0F / 16.0F;

	private final Font font;

	public TradingDeskRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(
		TradingDeskBlockEntity desk, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(desk, state, partialTicks, cameraPosition, breakProgress);
		if (desk.getBlockState().getBlock() instanceof TradingDeskBlock) {
			state.facing = desk.getBlockState().getValue(TradingDeskBlock.FACING);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
		// The monitor's screen: 13 by 8 pixels, its front 3 pixels behind the block's middle.
		poseStack.translate(-6.5F * PIXEL, 15.5F * PIXEL, -3.0F * PIXEL + 0.003F);
		poseStack.scale(1.0F / UNITS, -1.0F / UNITS, 1.0F / UNITS);

		OandaData data = OandaData.get();
		Account account = data.account();
		ChartPainter.Header header = null;
		if (account != null) {
			header = new ChartPainter.Header(
				"NAV " + String.format("%,.2f", account.nav()),
				String.format("%+,.2f", account.unrealizedPl()),
				account.unrealizedPl() >= 0 ? ChartPainter.UP : ChartPainter.DOWN);
		}
		ChartPainter.paint(
			new Canvas(poseStack, collector, font, ChartScreenRenderer.FULL_BRIGHT),
			13 * PIXEL * UNITS,
			8 * PIXEL * UNITS,
			0.42F,
			DeskScreen.selectedInstrument(),
			"M15",
			true,
			false,
			header);
		poseStack.popPose();
	}

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
	}
}
