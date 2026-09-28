package com.tradingdesk.block;

import java.util.List;

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
 * What a chart screen shows: an OANDA instrument name like {@code EUR_USD}, a candle granularity, and whether to draw
 * the player's open trades on it, and whether it's a master chart that buy and sell buttons trade. Every screen in a chart keeps the same settings; the anchor's are the ones used.
 */
public class ChartScreenBlockEntity extends BlockEntity {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** OANDA candle granularities a chart can use, shortest first. */
	public static final List<String> GRANULARITIES = List.of("M1", "M5", "M15", "H1", "H4", "D");

	private String instrument = "";
	private String granularity = "M15";
	private boolean showTrades = true;
	private boolean master;

	public ChartScreenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHART_SCREEN, pos, state);
	}

	public String instrument() {
		return instrument;
	}

	public String granularity() {
		return granularity;
	}

	public boolean showTrades() {
		return showTrades;
	}

	/** Whether buy and sell buttons nearby trade this chart's market. */
	public boolean master() {
		return master;
	}

	public boolean isSetUp() {
		return !instrument.isEmpty();
	}

	/** Whether these are settings a chart can have: a plausible instrument name and a known granularity. */
	public static boolean isValid(String instrument, String granularity) {
		return instrument.length() <= 32 && instrument.matches("[A-Z0-9_]*") && GRANULARITIES.contains(granularity);
	}

	public void apply(String instrument, String granularity, boolean showTrades, boolean master) {
		if (!isValid(instrument, granularity)) {
			return;
		}
		this.instrument = instrument;
		this.granularity = granularity;
		this.showTrades = showTrades;
		this.master = master;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		String loadedInstrument = input.getStringOr("instrument", "");
		String loadedGranularity = input.getStringOr("granularity", "M15");
		if (isValid(loadedInstrument, loadedGranularity)) {
			instrument = loadedInstrument;
			granularity = loadedGranularity;
		}
		showTrades = input.getBooleanOr("show_trades", true);
		master = input.getBooleanOr("master", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("instrument", instrument);
		output.putString("granularity", granularity);
		output.putBoolean("show_trades", showTrades);
		output.putBoolean("master", master);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			saveAdditional(output);
			return output.buildResult();
		}
	}
}
