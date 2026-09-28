package com.tradingdesk.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Flat drawing on a surface in the world: colored rectangles and text, in units where x runs right and y runs down
 * from the pose's origin. Later layers sit slightly in front of earlier ones so they don't flicker into each other.
 */
final class Canvas {
	private static final float LAYER_DEPTH = 0.06F;

	private final PoseStack pose;
	private final SubmitNodeCollector collector;
	final Font font;
	private final int light;

	Canvas(PoseStack pose, SubmitNodeCollector collector, Font font, int light) {
		this.pose = pose;
		this.collector = collector;
		this.font = font;
		this.light = light;
	}

	void rect(float x0, float y0, float x1, float y1, int color, int layer) {
		pose.pushPose();
		pose.translate(0.0F, 0.0F, layer * LAYER_DEPTH);
		collector.submitTextBackground(pose, Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), color, Font.DisplayMode.POLYGON_OFFSET, light);
		pose.popPose();
	}

	/** A horizontal line, {@code thickness} tall, centered on y. */
	void hLine(float x0, float x1, float y, float thickness, int color, int layer) {
		rect(x0, y - thickness / 2, x1, y + thickness / 2, color, layer);
	}

	void dashedLine(float x0, float x1, float y, float thickness, float dash, int color, int layer) {
		for (float x = x0; x < x1; x += dash * 1.8F) {
			hLine(x, Math.min(x1, x + dash), y, thickness, color, layer);
		}
	}

	float width(String text, float scale) {
		return font.width(text) * scale;
	}

	void text(String text, float x, float y, float scale, int color, int layer) {
		text(Component.literal(text).getVisualOrderText(), x, y, scale, color, layer);
	}

	void text(FormattedCharSequence text, float x, float y, float scale, int color, int layer) {
		pose.pushPose();
		pose.translate(x, y, layer * LAYER_DEPTH);
		pose.scale(scale, scale, 1.0F);
		collector.submitText(pose, 0.0F, 0.0F, text, false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
		pose.popPose();
	}

	void rightText(String text, float right, float y, float scale, int color, int layer) {
		text(text, right - width(text, scale), y, scale, color, layer);
	}

	/** Word-wrapped lines centered in a box. */
	void centeredLines(String message, float x0, float y0, float x1, float y1, float scale, int color, int layer) {
		float maxWidth = (x1 - x0) * 0.9F / scale;
		List<FormattedCharSequence> lines = font.split(Component.literal(message), Math.max(20, (int) maxWidth));
		float lineHeight = 10 * scale;
		float top = (y0 + y1) / 2 - lines.size() * lineHeight / 2;
		for (int i = 0; i < lines.size(); i++) {
			float lineWidth = font.width(lines.get(i)) * scale;
			text(lines.get(i), (x0 + x1) / 2 - lineWidth / 2, top + i * lineHeight, scale, color, layer);
		}
	}
}
