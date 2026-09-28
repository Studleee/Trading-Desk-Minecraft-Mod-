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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Picks what a chart screen shows: a market chart (with a searchable market, the timeframe, whether the player's trades
 * are drawn on it, and whether it's the master chart), or one of the account boards. The choice applies to every
 * screen in the group.
 */
public class ChartSettingsScreen extends Screen {
	private static final int ROW = 11;
	private static final int TEXT = 0xFFE6EDF3;
	private static final int DIM = 0xFF8B949E;
	private static final int HIGHLIGHT = 0xFF1F6FEB;
	private static final String[] GRANULARITY_LABELS = {"1m", "5m", "15m", "1h", "4h", "1D"};
	private static final String[] MODE_LABELS = {"Chart", "Account", "Positions", "Trades", "NAV", "Watchlist"};
	private static final String[] MODE_DESCRIPTIONS = {
		"",
		"Your NAV in big numbers, with balance, unrealized and realized P/L, margin, margin level, and how many trades are open.",
		"Your open trades added up per market: net side and size, average price, current price, and P/L, with a total.",
		"Every open trade: market, side, units, entry, stop loss, take profit, and P/L, with a total.",
		"A graph of your NAV over the last day. OANDA doesn't keep NAV history, so it's recorded every 30 seconds while the game runs and something shows your account.",
		"Live bid, ask, and spread for your watchlist. Add markets at the desk."
	};

	private final BlockPos pos;
	private final String size;
	private String mode;
	private String instrument;
	private String granularity;
	private boolean showTrades;
	private boolean master;
	private String search = "";
	private int scroll;
	private final List<Button> modeButtons = new ArrayList<>();
	private final List<Button> granularityButtons = new ArrayList<>();
	private final List<AbstractWidget> chartWidgets = new ArrayList<>();
	private Button tradesButton;
	private Button masterButton;

	public ChartSettingsScreen(BlockPos pos) {
		super(Component.literal("Screen settings"));
		this.pos = pos;
		String groupSize = "1 x 1";
		String currentMode = "chart";
		String currentInstrument = "";
		String currentGranularity = "M15";
		boolean currentShowTrades = true;
		boolean currentMaster = false;
		var level = Minecraft.getInstance().level;
		if (level != null) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof ChartScreenBlock) {
				ChartGroup group = ChartGroup.of(level, pos, state.getValue(ChartScreenBlock.FACING));
				groupSize = group.width() + " x " + group.height();
				if (level.getBlockEntity(group.anchor()) instanceof ChartScreenBlockEntity anchor) {
					currentMode = anchor.mode();
					currentInstrument = anchor.instrument();
					currentGranularity = anchor.granularity();
					currentShowTrades = anchor.showTrades();
					currentMaster = anchor.master();
				}
			}
		}
		this.size = groupSize;
		this.mode = currentMode;
		this.instrument = currentInstrument;
		this.granularity = currentGranularity;
		this.showTrades = currentShowTrades;
		this.master = currentMaster;
	}

	private boolean isChart() {
		return mode.equals("chart");
	}

	private int listLeft() {
		return width / 2 - 150;
	}

	private int listRight() {
		return width / 2 + 10;
	}

	private int listTop() {
		return 76;
	}

	private int listRows() {
		return Math.max(3, (height - 40 - listTop()) / ROW);
	}

	@Override
	protected void init() {
		modeButtons.clear();
		chartWidgets.clear();
		granularityButtons.clear();

		int modeWidth = Math.min(62, (width - 20) / MODE_LABELS.length);
		int modeLeft = width / 2 - modeWidth * MODE_LABELS.length / 2;
		for (int i = 0; i < MODE_LABELS.length; i++) {
			String code = ChartScreenBlockEntity.MODES.get(i);
			modeButtons.add(addRenderableWidget(Button.builder(Component.literal(MODE_LABELS[i]), b -> {
				mode = code;
				updateButtons();
			}).bounds(modeLeft + i * modeWidth, 22, modeWidth - 2, 18).build()));
		}

		EditBox searchBox = new EditBox(font, listLeft(), 58, listRight() - listLeft(), 14, Component.literal("Search"));
		searchBox.setMaxLength(24);
		searchBox.setValue(search);
		searchBox.setHint(Component.literal("Search markets"));
		searchBox.setResponder(value -> {
			search = value;
			scroll = 0;
		});
		chartWidgets.add(addRenderableWidget(searchBox));

		int x = listRight() + 10;
		for (int i = 0; i < GRANULARITY_LABELS.length; i++) {
			String code = ChartScreenBlockEntity.GRANULARITIES.get(i);
			Button button = Button.builder(Component.literal(GRANULARITY_LABELS[i]), b -> {
				granularity = code;
				updateButtons();
			}).bounds(x + (i % 3) * 44, 70 + (i / 3) * 20, 42, 18).build();
			granularityButtons.add(addRenderableWidget(button));
			chartWidgets.add(button);
		}
		tradesButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			showTrades = !showTrades;
			updateButtons();
		}).bounds(x, 116, 130, 18).build());
		masterButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			master = !master;
			updateButtons();
		}).bounds(x, 138, 130, 18).build());
		chartWidgets.add(tradesButton);
		chartWidgets.add(masterButton);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(x, height - 30, 64, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 68, height - 30, 64, 20).build());
		updateButtons();
		if (isChart()) {
			setInitialFocus(searchBox);
		}
	}

	private void updateButtons() {
		for (int i = 0; i < modeButtons.size(); i++) {
			modeButtons.get(i).active = !ChartScreenBlockEntity.MODES.get(i).equals(mode);
		}
		for (AbstractWidget widget : chartWidgets) {
			widget.visible = isChart();
		}
		for (int i = 0; i < granularityButtons.size(); i++) {
			granularityButtons.get(i).active = !ChartScreenBlockEntity.GRANULARITIES.get(i).equals(granularity);
		}
		tradesButton.setMessage(Component.literal("Show my trades: " + (showTrades ? "On" : "Off")));
		masterButton.setMessage(Component.literal("Master chart: " + (master ? "On" : "Off")));
	}

	/** Markets matching the search: from OANDA's list when connected, otherwise the watchlist and what's typed. */
	private List<String> matches() {
		OandaData data = OandaData.get();
		String query = search.trim().toUpperCase(Locale.ROOT).replace('/', '_');
		Set<String> names = new LinkedHashSet<>(data.config().watchlist);
		if (data.instruments().isEmpty()) {
			if (query.matches("[A-Z0-9_]{3,32}")) {
				names.add(query);
			}
		} else {
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
		graphics.centeredText(font, title.getString() + "  (" + size + " screens)", width / 2, 8, TEXT);

		if (!isChart()) {
			int index = ChartScreenBlockEntity.MODES.indexOf(mode);
			graphics.textWithWordWrap(font, Component.literal(MODE_DESCRIPTIONS[index]), listLeft(), 58, width - 20 - listLeft(), DIM);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}

		String chosen = instrument.isEmpty() ? "none yet" : data.displayName(instrument);
		graphics.centeredText(font, "Market: " + chosen, width / 2, 45, DIM);

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
		int x = listRight() + 10;
		int textWidth = width - listRight() - 20;
		graphics.text(font, "Timeframe", x, 58, TEXT);
		graphics.textWithWordWrap(font, Component.literal("Buy and sell buttons trade the nearest master chart's market."), x, 162, textWidth, DIM);
		if (data.status() != OandaData.Status.CONNECTED && !data.message().isEmpty()) {
			graphics.textWithWordWrap(font, Component.literal(data.message()), x, 190, textWidth, DIM);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (!isChart()) {
			return false;
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
		if (isChart() && x >= listLeft() && x < listRight()) {
			scroll += scrollY > 0 ? -3 : 3;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void save() {
		boolean ready = !isChart() || !instrument.isEmpty();
		if (ready && ChartScreenBlockEntity.isValid(mode, instrument, granularity)) {
			ClientPlayNetworking.send(new SetChartPayload(pos, mode, instrument, granularity, showTrades, master));
		}
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
