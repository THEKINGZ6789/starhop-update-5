package dev.starhop;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** All of Starhop's sound events. The audio files are original and live in assets/starhop/sounds/. */
public final class StarhopSounds {
	public static SoundEvent JUMP_DOUBLE;
	public static SoundEvent JUMP_TRIPLE;
	public static SoundEvent LONG_JUMP;
	public static SoundEvent BACKFLIP;
	public static SoundEvent WALL_JUMP;
	public static SoundEvent SPIN;
	public static SoundEvent POUND_START;
	public static SoundEvent POUND_HIT;
	public static SoundEvent STOMP;
	public static SoundEvent STAR_POWER;

	private StarhopSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Identifier.fromNamespaceAndPath(Starhop.MOD_ID, name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		JUMP_DOUBLE = register("jump_double");
		JUMP_TRIPLE = register("jump_triple");
		LONG_JUMP = register("long_jump");
		BACKFLIP = register("backflip");
		WALL_JUMP = register("wall_jump");
		SPIN = register("spin");
		POUND_START = register("pound_start");
		POUND_HIT = register("pound_hit");
		STOMP = register("stomp");
		STAR_POWER = register("star_power");
	}
}
