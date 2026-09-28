package com.tradingdesk.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.tradingdesk.block.ChartGroup;
import com.tradingdesk.block.ChartScreenBlock;
import com.tradingdesk.block.ChartScreenBlockEntity;
import com.tradingdesk.client.oanda.OandaData;
import com.tradingdesk.client.oanda.OandaModels.Instrument;
import com.tradingdesk.network.SetChartPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Picks what a chart shows: the market (searchable), the timeframe, and whether the player's trades are drawn on it.
 * The choice applies to every screen in the chart.
 */
public class ChartSettingsScreen extends Screen {
	private static final int ROW = 11;
	private static final int TEXT = 0xFFE6EDF3;
	private static final int DIM = 0xFF8B949E;
	private static final int HIGHLIGHT = 0xFF1F6FEB;
	private static final String[] LABELS = {"1m", "5m", "15m", "1h", "4h", "1D"};

	private final BlockPos pos;
	private final String size;
	private String instrument;
	private String granularity;
	private boolean showTrades;
	private String search = "";
	private int scroll;
	private final List<Button> granularityButtons = new ArrayList<>();
	private Button tradesButton;

	public ChartSettingsScreen(BlockPos pos) {
		super(Component.literal("Chart settings"));
		this.pos = pos;
		String groupSize = "1 x 1";
		String currentInstrument = "";
		String currentGranularity = "M15";
		boolean currentShowTrades = true;
		var level = net.minecraft.client.Minecraft.getInstance().level;
		if (level != null) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof ChartScreenBlock) {
				ChartGroup group = ChartGroup.of(level, pos, state.getValue(ChartScreenBlock.FACING));
				groupSize = group.width() + " x " + group.height();
				if (level.getBlockEntity(group.anchor()) instanceof ChartScreenBlockEntity anchor) {
					currentInstrument = anchor.instrument();
					currentGranularity = anchor.granularity();
					currentShowTrades = anchor.showTrades();
				}
			}
		}
		this.size = groupSize;
		this.instrument = currentInstrument;
		this.granularity = currentGranularity;
		this.showTrades = currentShowTrades;
	}

	private int listLeft() {
		return width / 2 - 150;
	}

	private int listRight() {
		return width / 2 + 10;
	}

	private int listTop() {
		return 58;
	}

	private int listRows() {
		return Math.max(3, (height - 40 - listTop()) / ROW);
	}

	@Override
	protected void init() {
		EditBox searchBox = new EditBox(font, listLeft(), 40, listRight() - listLeft(), 14, Component.literal("Search"));
		searchBox.setMaxLength(24);
		searchBox.setValue(search);
		searchBox.setHint(Component.literal("Search markets"));
		searchBox.setResponder(value -> {
			search = value;
			scroll = 0;
		});
		addRenderableWidget(searchBox);
		setInitialFocus(searchBox);

		int x = listRight() + 10;
		granularityButtons.clear();
		for (int i = 0; i < LABELS.length; i++) {
			String code = ChartScreenBlockEntity.GRANULARITIES.get(i);
			Button button = Button.builder(Component.literal(LABELS[i]), b -> {
				granularity = code;
				updateButtons();
			}).bounds(x + (i % 3) * 44, 52 + (i / 3) * 20, 42, 18).build();
			granularityButtons.add(addRenderableWidget(button));
		}
		tradesButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			showTrades = !showTrades;
			updateButtons();
		}).bounds(x, 106, 130, 18).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(x, height - 50, 64, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 68, height - 50, 64, 20).build());
		updateButtons();
	}

	private void updateButtons() {
		for (int i = 0; i < granularityButtons.size(); i++) {
			granularityButtons.get(i).active = !ChartScreenBlockEntity.GRANULARITIES.get(i).equals(granularity);
		}
		tradesButton.setMessage(Component.literal("Show my trades: " + (showTrades ? "On" : "Off")));
	}

	/** Markets matching the search: from OANDA's list when connected, otherwise the watchlist and what's typed. */
	private List<String> matches() {
		OandaData data = OandaData.get();
		String query = search.trim().toUpperCase(Locale.ROOT).replace('/', '_');
		Set<String> names = new LinkedHashSet<>();
		if (data.instruments().isEmpty()) {
			names.addAll(data.config().watchlist);
			if (query.matches("[A-Z0-9_]{3,32}")) {
				names.add(query);
			}
		} else {
			names.addAll(data.config().watchlist);
			for (Instrument info : data.instruments().values()) {
				names.add(info.name());
			}
		}
		List<String> matches = new ArrayList<>();
		for (String name : names) {
			if (query.isEmpty() || name.contains(query) || data.displayName(name).toUpperCase(Locale.ROOT).contains(query)) {
				matches.add(name);
			}
		}
		return matches;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		OandaData data = OandaData.get();
		graphics.centeredText(font, title.getString() + "  (" + size + " screens)", width / 2, 12, TEXT);
		String chosen = instrument.isEmpty() ? "none yet" : data.displayName(instrument);
		graphics.centeredText(font, "Showing: " + chosen, width / 2, 24, DIM);

		int x0 = listLeft();
		int x1 = listRight();
		graphics.fill(x0, listTop() - 2, x1, listTop() + listRows() * ROW + 2, 0xC0161B22);
		List<String> matches = matches();
		scroll = Math.clamp(scroll, 0, Math.max(0, matches.size() - listRows()));
		for (int row = 0; row < listRows() && row + scroll < matches.size(); row++) {
			String name = matches.get(row + scroll);
			int y = listTop() + row * ROW;
			boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
			if (name.equals(instrument)) {
				graphics.fill(x0, y - 1, x1, y + ROW - 1, HIGHLIGHT);
			} else if (hover) {
				graphics.fill(x0, y - 1, x1, y + ROW - 1, 0xFF30363D);
			}
			graphics.text(font, data.displayName(name), x0 + 4, y + 1, TEXT);
			graphics.text(font, name, x1 - 4 - font.width(name), y + 1, DIM);
		}
		if (matches.isEmpty()) {
			graphics.text(font, "No markets match", x0 + 4, listTop() + 1, DIM);
		}
		if (data.status() != OandaData.Status.CONNECTED && !data.message().isEmpty()) {
			graphics.textWithWordWrap(font, Component.literal(data.message()), listRight() + 10, 130, width - listRight() - 20, DIM);
		}

		graphics.text(font, "Timeframe", listRight() + 10, 40, TEXT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		List<String> matches = matches();
		for (int row = 0; row < listRows() && row + scroll < matches.size(); row++) {
			int y = listTop() + row * ROW;
			if (event.x() >= listLeft() && event.x() < listRight() && event.y() >= y && event.y() < y + ROW) {
				instrument = matches.get(row + scroll);
				if (doubleClick) {
					save();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= listLeft() && x < listRight()) {
			scroll += scrollY > 0 ? -3 : 3;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void save() {
		if (!instrument.isEmpty() && ChartScreenBlockEntity.isValid(instrument, granularity)) {
			ClientPlayNetworking.send(new SetChartPayload(pos, instrument, granularity, showTrades));
		}
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
