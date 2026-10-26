package dev.starhop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click to burn the shard for a short burst of "star power". */
public class CometShardItem extends Item {
	private static final int DURATION_TICKS = 20 * 15;

	public CometShardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Only the server applies effects and consumes the item.
		if (player instanceof ServerPlayer) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, DURATION_TICKS, 2));
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, DURATION_TICKS, 1));
			// Resistance V makes you effectively immune to most damage.
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DURATION_TICKS, 4));
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				StarhopSounds.STAR_POWER, SoundSource.PLAYERS, 1.0f, 1.0f);
			player.getItemInHand(hand).consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
