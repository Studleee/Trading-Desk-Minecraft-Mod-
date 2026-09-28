package com.tradingdesk.block;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.tradingdesk.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.slf4j.Logger;

/**
 * What a chart screen shows. The mode picks a market chart, one of the account boards (account summary, positions,
 * open trades, NAV history, watchlist, ticker), or a vote counter. A market chart also has an OANDA instrument name
 * like {@code EUR_USD}, a candle granularity, whether to draw the player's open trades on it, and whether it's a master
 * chart that buy and sell buttons trade. A vote counter has how many units each net vote is worth and whether its
 * owner's position on the nearest master chart follows the vote. Every screen in a group keeps the same settings; the
 * anchor's are the ones used, and only the anchor keeps the live vote count.
 */
public class ChartScreenBlockEntity extends BlockEntity {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** What a screen can show. */
	public static final List<String> MODES = List.of("chart", "account", "positions", "trades", "nav", "watchlist", "ticker", "votes");
	/** OANDA candle granularities a chart can use, shortest first. */
	public static final List<String> GRANULARITIES = List.of("M1", "M5", "M15", "H1", "H4", "D");
	public static final int MAX_UNITS_PER_VOTE = 1_000_000;

	/** Everything a player picks in a screen's settings. */
	public record Settings(String mode, String instrument, String granularity, boolean showTrades, boolean master, int unitsPerVote, boolean autoTrade) {
		public static final Settings DEFAULT = new Settings("chart", "", "M15", true, false, 1000, false);

		/** Whether these are settings a screen can have: a known mode and granularity, a plausible instrument name, and a sensible vote size. */
		public boolean isValid() {
			return MODES.contains(mode) && instrument.length() <= 32 && instrument.matches("[A-Z0-9_]*") && GRANULARITIES.contains(granularity)
				&& unitsPerVote >= 1 && unitsPerVote <= MAX_UNITS_PER_VOTE;
		}
	}

	private Settings settings = Settings.DEFAULT;
	private @Nullable UUID owner;
	private int yesVotes;
	private int noVotes;

	public ChartScreenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHART_SCREEN, pos, state);
	}

	public Settings settings() {
		return settings;
	}

	public String mode() {
		return settings.mode();
	}

	public boolean isChart() {
		return settings.mode().equals("chart");
	}

	public boolean isVotes() {
		return settings.mode().equals("votes");
	}

	public String instrument() {
		return settings.instrument();
	}

	public String granularity() {
		return settings.granularity();
	}

	public boolean showTrades() {
		return settings.showTrades();
	}

	/** Whether buy and sell buttons and vote counters nearby trade this chart's market. */
	public boolean master() {
		return settings.master() && isChart();
	}

	public int unitsPerVote() {
		return settings.unitsPerVote();
	}

	/** Whether this vote counter's owner's position follows the vote. */
	public boolean autoTrade() {
		return settings.autoTrade() && isVotes() && owner != null;
	}

	/** The player whose account a vote counter trades, if auto-trade has been turned on. */
	public @Nullable UUID owner() {
		return owner;
	}

	public int yesVotes() {
		return yesVotes;
	}

	public int noVotes() {
		return noVotes;
	}

	/** Whether someone has picked what this screen shows. */
	public boolean isSetUp() {
		return !isChart() || !settings.instrument().isEmpty();
	}

	public void apply(Settings settings, @Nullable UUID owner) {
		if (!settings.isValid()) {
			return;
		}
		this.settings = settings;
		this.owner = settings.autoTrade() ? owner : null;
		setChanged();
		sync();
	}

	/** Sets the live vote count, telling players only when it changes. */
	public void setVotes(int yes, int no) {
		if (yes == yesVotes && no == noVotes) {
			return;
		}
		yesVotes = yes;
		noVotes = no;
		sync();
	}

	private void sync() {
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		Settings loaded = new Settings(
			input.getStringOr("mode", "chart"),
			input.getStringOr("instrument", ""),
			input.getStringOr("granularity", "M15"),
			input.getBooleanOr("show_trades", true),
			input.getBooleanOr("master", false),
			input.getIntOr("units_per_vote", 1000),
			input.getBooleanOr("auto_trade", false));
		settings = loaded.isValid() ? loaded : Settings.DEFAULT;
		owner = null;
		String ownerText = input.getStringOr("owner", "");
		if (!ownerText.isEmpty()) {
			try {
				owner = UUID.fromString(ownerText);
			} catch (IllegalArgumentException e) {
				owner = null;
			}
		}
		yesVotes = input.getIntOr("yes_votes", 0);
		noVotes = input.getIntOr("no_votes", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("mode", settings.mode());
		output.putString("instrument", settings.instrument());
		output.putString("granularity", settings.granularity());
		output.putBoolean("show_trades", settings.showTrades());
		output.putBoolean("master", settings.master());
		output.putInt("units_per_vote", settings.unitsPerVote());
		output.putBoolean("auto_trade", settings.autoTrade());
		if (owner != null) {
			output.putString("owner", owner.toString());
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** The saved settings plus the live vote count, which isn't saved with the world. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			saveAdditional(output);
			output.putInt("yes_votes", yesVotes);
			output.putInt("no_votes", noVotes);
			return output.buildResult();
		}
	}
}
