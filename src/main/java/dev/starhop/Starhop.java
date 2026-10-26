package dev.starhop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class Starhop implements ModInitializer {
	public static final String MOD_ID = "starhop";

	public static Item COMET_SHARD;

	@Override
	public void onInitialize() {
		StarhopSounds.init();

		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "comet_shard");
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);

		COMET_SHARD = Registry.register(
			BuiltInRegistries.ITEM,
			key,
			new CometShardItem(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.RARE))
		);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
			.register(output -> output.accept(COMET_SHARD));

		// Server side of the moves: stomp damage and the ground pound shockwave.
		ServerTickEvents.END_SERVER_TICK.register(StarhopServer::tick);
	}
}
