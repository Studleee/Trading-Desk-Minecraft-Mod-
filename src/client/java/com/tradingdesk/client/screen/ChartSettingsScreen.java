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
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Picks what a chart screen shows: a market chart (with a searchable market, the timeframe, whether the player's trades
 * are drawn on it, and whether it's the master chart), one of the account boards, or a vote counter (with the units
 * each vote is worth and whether the position follows the vote). The choice applies to every screen in the group.
 */
public class ChartSettingsScreen extends Screen {
	private static final int ROW = 11;
	private static final int TEXT = 0xFFE6EDF3;
	private static final int DIM = 0xFF8B949E;
	private static final int WARNING = 0xFFE3B341;
	private static final int ERROR = 0xFFF85149;
	private static final int HIGHLIGHT = 0xFF1F6FEB;
	private static final int MODES_PER_ROW = 4;
	private static final String[] GRANULARITY_LABELS = {"1m", "5m", "15m", "1h", "4h", "1D"};
	private static final String[] MODE_LABELS = {"Chart", "Account", "Positions", "Trades", "NAV", "Watchlist", "Ticker", "Votes"};
	private static final String[] MODE_DESCRIPTIONS = {
		"",
		"Your NAV in big numbers, with balance, unrealized and realized P/L, margin, margin level, and how many trades are open.",
		"Your open trades added up per market: net side and size, average price, current price, and P/L, with a total.",
		"Every open trade: market, side, units, entry, stop loss, take profit, and P/L, with a total.",
		"A graph of your NAV over the last day. OANDA doesn't keep NAV history, so it's recorded every 30 seconds while the game runs and something shows your account.",
		"Live bid, ask, and spread for your watchlist. Add markets at the desk.",
		"Your watchlist scrolling past in one line: price and change since the day's open. Best on a long row of screens one high, like above a doorway.",
		"Counts everything standing on green vote plates (+1) and red vote plates (-1) within 32 blocks: players, villagers, animals, monsters, and bots. Stepping off takes the vote away."
	};

	private final BlockPos pos;
	private final String size;
	private String mode;
	private String instrument;
	private String granularity;
	private boolean showTrades;
	private boolean master;
	private String unitsPerVote;
	private boolean autoTrade;
	private String search = "";
	private String problem = "";
	private int scroll;
	private final List<Button> modeButtons = new ArrayList<>();
	private final List<Button> granularityButtons = new ArrayList<>();
	private final List<AbstractWidget> chartWidgets = new ArrayList<>();
	private final List<AbstractWidget> voteWidgets = new ArrayList<>();
	private Button tradesButton;
	private Button masterButton;
	private Button autoTradeButton;

	public ChartSettingsScreen(BlockPos pos) {
		super(Component.literal("Screen settings"));
		this.pos = pos;
		String groupSize = "1 x 1";
		ChartScreenBlockEntity.Settings current = ChartScreenBlockEntity.Settings.DEFAULT;
		boolean currentAutoTrade = false;
		var level = Minecraft.getInstance().level;
		if (level != null) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof ChartScreenBlock) {
				ChartGroup group = ChartGroup.of(level, pos, state.getValue(ChartScreenBlock.FACING));
				groupSize = group.width() + " x " + group.height();
				if (level.getBlockEntity(group.anchor()) instanceof ChartScreenBlockEntity anchor) {
					current = anchor.settings();
					currentAutoTrade = anchor.autoTrade();
				}
			}
		}
		this.size = groupSize;
		this.mode = current.mode();
		this.instrument = current.instrument();
		this.granularity = current.granularity();
		this.showTrades = current.showTrades();
		this.master = current.master();
		this.unitsPerVote = String.valueOf(current.unitsPerVote());
		this.autoTrade = currentAutoTrade;
	}

	private boolean isChart() {
		return mode.equals("chart");
	}

	private boolean isVotes() {
		return mode.equals("votes");
	}

	private int listLeft() {
		return width / 2 - 150;
	}

	private int listRight() {
		return width / 2 + 10;
	}

	private int listTop() {
		return 96;
	}

	private int listRows() {
		return Math.max(3, (height - 40 - listTop()) / ROW);
	}

	@Override
	protected void init() {
		modeButtons.clear();
		chartWidgets.clear();
		voteWidgets.clear();
		granularityButtons.clear();

		int modeWidth = Math.min(76, (width - 20) / MODES_PER_ROW);
		int modeLeft = width / 2 - modeWidth * MODES_PER_ROW / 2;
		for (int i = 0; i < MODE_LABELS.length; i++) {
			String code = ChartScreenBlockEntity.MODES.get(i);
			int column = i % MODES_PER_ROW;
			int row = i / MODES_PER_ROW;
			modeButtons.add(addRenderableWidget(Button.builder(Component.literal(MODE_LABELS[i]), b -> {
				mode = code;
				problem = "";
				updateButtons();
			}).bounds(modeLeft + column * modeWidth, 20 + row * 20, modeWidth - 2, 18).build()));
		}

		EditBox searchBox = new EditBox(font, listLeft(), 78, listRight() - listLeft(), 14, Component.literal("Search"));
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
			}).bounds(x + (i % 3) * 44, 90 + (i / 3) * 20, 42, 18).build();
			granularityButtons.add(addRenderableWidget(button));
			chartWidgets.add(button);
		}
		tradesButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			showTrades = !showTrades;
			updateButtons();
		}).bounds(x, 136, 130, 18).build());
		masterButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			master = !master;
			updateButtons();
		}).bounds(x, 158, 130, 18).build());
		chartWidgets.add(tradesButton);
		chartWidgets.add(masterButton);

		EditBox unitsBox = new EditBox(font, listLeft() + 90, 126, 80, 16, Component.literal("Units per vote"));
		unitsBox.setMaxLength(7);
		unitsBox.setValue(unitsPerVote);
		unitsBox.setResponder(value -> {
			unitsPerVote = value;
			problem = "";
		});
		voteWidgets.add(addRenderableWidget(unitsBox));
		autoTradeButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleAutoTrade()).bounds(listLeft(), 148, 170, 18).build());
		voteWidgets.add(autoTradeButton);

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
		for (AbstractWidget widget : voteWidgets) {
			widget.visible = isVotes();
		}
		for (int i = 0; i < granularityButtons.size(); i++) {
			granularityButtons.get(i).active = !ChartScreenBlockEntity.GRANULARITIES.get(i).equals(granularity);
		}
		tradesButton.setMessage(Component.literal("Show my trades: " + (showTrades ? "On" : "Off")));
		masterButton.setMessage(Component.literal("Master chart: " + (master ? "On" : "Off")));
		autoTradeButton.setMessage(Component.literal("Auto-trade: " + (autoTrade ? "On" : "Off")));
	}

	/** Turning auto-trade on places orders with no confirmation, so it asks once here instead. */
	private void toggleAutoTrade() {
		if (autoTrade) {
			autoTrade = false;
			updateButtons();
			return;
		}
		boolean live = OandaData.get().config().live();
		Component heading = live
			? Component.literal("LIVE ACCOUNT: let the vote trade real money?").withColor(ERROR)
			: Component.literal("Let the vote trade your practice account?");
		Component message = Component.literal("Your position on the nearest master chart will follow the vote: net votes x "
			+ unitsPerVote + " units, long when positive, short when negative, and closed at zero. Orders go in without asking, "
			+ "a few seconds after the vote settles, while you're within 64 blocks.");
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				autoTrade = true;
			}
			minecraft.gui.setScreen(this);
		}, heading, message));
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
		if (!problem.isEmpty()) {
			graphics.text(font, problem, listLeft(), height - 24, ERROR);
		}

		if (!isChart()) {
			int index = ChartScreenBlockEntity.MODES.indexOf(mode);
			int textWidth = width - 20 - listLeft();
			graphics.textWithWordWrap(font, Component.literal(MODE_DESCRIPTIONS[index]), listLeft(), 70, textWidth, DIM);
			if (isVotes()) {
				graphics.text(font, "Units per vote", listLeft(), 130, TEXT);
				graphics.textWithWordWrap(font, Component.literal(autoTrade
					? "Your position on the nearest master chart follows the vote: net votes x units per vote. Orders go in without asking."
					: "Turn on auto-trade to have your position on the nearest master chart follow the vote."),
					listLeft(), 172, textWidth, autoTrade ? WARNING : DIM);
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}

		String chosen = instrument.isEmpty() ? "none yet" : data.displayName(instrument);
		graphics.centeredText(font, "Market: " + chosen, width / 2, 64, DIM);

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
		graphics.text(font, "Timeframe", x, 78, TEXT);
		graphics.textWithWordWrap(font, Component.literal("Buy and sell buttons and vote counters trade the nearest master chart's market."), x, 182, textWidth, DIM);
		if (data.status() != OandaData.Status.CONNECTED && !data.message().isEmpty()) {
			graphics.textWithWordWrap(font, Component.literal(data.message()), x, 214, textWidth, DIM);
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
		int units;
		try {
			units = Integer.parseInt(unitsPerVote.trim().replace(",", ""));
		} catch (NumberFormatException e) {
			units = 0;
		}
		if (isVotes() && (units < 1 || units > ChartScreenBlockEntity.MAX_UNITS_PER_VOTE)) {
			problem = String.format("Units per vote must be 1 to %,d", ChartScreenBlockEntity.MAX_UNITS_PER_VOTE);
			return;
		}
		if (!isVotes()) {
			units = Math.clamp(units, 1, ChartScreenBlockEntity.MAX_UNITS_PER_VOTE);
		}
		ChartScreenBlockEntity.Settings settings = new ChartScreenBlockEntity.Settings(
			mode, instrument, granularity, showTrades, master, units, autoTrade && isVotes());
		boolean ready = !isChart() || !instrument.isEmpty();
		if (ready && settings.isValid()) {
			ClientPlayNetworking.send(new SetChartPayload(pos, settings));
		}
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
