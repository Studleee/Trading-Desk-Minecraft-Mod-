package com.tradingdesk.registry;

import com.tradingdesk.TradingDesk;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(
		BuiltInRegistries.CREATIVE_MODE_TAB.key(),
		TradingDesk.id(TradingDesk.MOD_ID)
	);

	private ModCreativeTab() {
	}

	public static void initialize() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(Register.creativeTabItems().getFirst()))
			.title(Component.translatable("creativeTab." + TradingDesk.MOD_ID))
			.displayItems((params, output) -> Register.creativeTabItems().forEach(output::accept))
			.build();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
	}
}
