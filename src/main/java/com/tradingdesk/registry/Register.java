package com.tradingdesk.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.tradingdesk.TradingDesk;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Shortcuts for adding content. Everything registered here also shows up in the mod's creative tab.
 */
public final class Register {
	private static final List<Item> CREATIVE_TAB_ITEMS = new ArrayList<>();

	private Register() {
	}

	/** A block that uses your own class, plus the item you hold to place it. */
	public static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Identifier id = TradingDesk.id(name);
		BlockItemId ids = BlockItemId.create(id, id);

		Block block = Registry.register(BuiltInRegistries.BLOCK, ids.block(), factory.apply(properties.setId(ids.block())));

		Item blockItem = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(ids.item()));
		Registry.register(BuiltInRegistries.ITEM, ids.item(), blockItem);
		CREATIVE_TAB_ITEMS.add(blockItem);

		return block;
	}

	static List<Item> creativeTabItems() {
		return Collections.unmodifiableList(CREATIVE_TAB_ITEMS);
	}
}
