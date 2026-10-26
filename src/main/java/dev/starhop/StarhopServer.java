package dev.starhop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * Server half of the moves. The client moves the player; the server watches the player's
 * position each tick to decide when a ground pound or a stomp has happened, then deals the damage.
 *
 * Ground pound: the client falls at exactly -1.5 blocks/tick, a speed natural falls only pass
 * through briefly after ~20 blocks. Two ticks in a row at that speed counts as a pound. While
 * pounding, fall damage is cancelled and the landing sends out a shockwave.
 */
public final class StarhopServer {
	private static final double POUND_DY = -1.5;
	private static final double POUND_TOLERANCE = 0.05;
	private static final float STOMP_DAMAGE = 6.0f;
	private static final float POUND_STOMP_DAMAGE = 10.0f;
	private static final int STOMP_COOLDOWN_TICKS = 8;

	private static final class State {
		double prevY;
		double prevDy;
		boolean seen;
		boolean pounding;
		double fallen;
		long lastStomp = -100;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private StarhopServer() {
	}

	private static boolean isPoundSpeed(double dy) {
		return Math.abs(dy - POUND_DY) < POUND_TOLERANCE;
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!(p.level() instanceof ServerLevel level)) {
				continue;
			}
			State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());

			double y = p.getY();
			double dy = s.seen ? y - s.prevY : 0.0;
			double prevDy = s.prevDy;
			s.prevY = y;
			s.prevDy = dy;
			s.seen = true;

			if (p.isSpectator() || p.getAbilities().flying || p.isInWater() || p.isInLava() || p.isPassenger()) {
				s.pounding = false;
				continue;
			}

			boolean grounded = p.onGround();

			// ---- Ground pound ----
			if (s.pounding) {
				if (grounded) {
					shockwave(level, p, s.fallen);
					s.pounding = false;
					s.fallen = 0.0;
				} else if (!isPoundSpeed(dy)) {
					// Blocked mid-air (cobweb, slime, ...). Give up quietly.
					s.pounding = false;
					s.fallen = 0.0;
				} else {
					s.fallen += -dy;
					p.resetFallDistance();
				}
			} else if (!grounded && isPoundSpeed(dy) && isPoundSpeed(prevDy)) {
				s.pounding = true;
				s.fallen = -(dy + prevDy);
				p.resetFallDistance();
			}

			// ---- Stomp ----
			long now = level.getGameTime();
			if (!grounded && dy < -0.05 && now - s.lastStomp > STOMP_COOLDOWN_TICKS) {
				AABB box = p.getBoundingBox().inflate(0.1, 0.0, 0.1).expandTowards(0.0, -0.6, 0.0);
				List<LivingEntity> targets = level.getEntitiesOfClass(
					LivingEntity.class, box, e -> e != p && e.isAlive() && e instanceof Enemy);
				for (LivingEntity target : targets) {
					double top = target.getBoundingBox().maxY;
					if (top <= y + 0.35 && top >= y - 0.7) {
						float damage = s.pounding ? POUND_STOMP_DAMAGE : STOMP_DAMAGE;
						target.hurtServer(level, level.damageSources().playerAttack(p), damage);
						s.lastStomp = now;
						s.pounding = false;
						s.fallen = 0.0;
						p.resetFallDistance();
						level.playSound(null, p.getX(), p.getY(), p.getZ(),
							StarhopSounds.STOMP, SoundSource.PLAYERS, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);
						level.sendParticles(ParticleTypes.CRIT,
							target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
							12, 0.3, 0.1, 0.3, 0.3);
						break;
					}
				}
			}
		}
	}

	private static void shockwave(ServerLevel level, ServerPlayer p, double fallen) {
		double radius = 3.0 + Math.min(fallen, 12.0) * 0.12;
		float damage = 5.0f + (float) Math.min(fallen / 3.0, 5.0);

		AABB box = p.getBoundingBox().inflate(radius, 1.5, radius);
		List<LivingEntity> targets = level.getEntitiesOfClass(
			LivingEntity.class, box, e -> e != p && e.isAlive() && e instanceof Enemy);
		for (LivingEntity target : targets) {
			if (target.distanceToSqr(p) <= radius * radius) {
				target.hurtServer(level, level.damageSources().playerAttack(p), damage);
			}
		}

		level.playSound(null, p.getX(), p.getY(), p.getZ(),
			StarhopSounds.POUND_HIT, SoundSource.PLAYERS, 1.2f, 1.0f);

		for (int ring = 1; ring <= 3; ring++) {
			double r = radius * ring / 3.0;
			int n = 10 * ring;
			for (int i = 0; i < n; i++) {
				double a = i * (Math.PI * 2.0 / n);
				level.sendParticles(ParticleTypes.POOF,
					p.getX() + Math.cos(a) * r, p.getY() + 0.1, p.getZ() + Math.sin(a) * r,
					1, 0.0, 0.02, 0.0, 0.02);
			}
		}
		level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.3, p.getZ(), 24, 0.6, 0.2, 0.6, 0.3);
	}
}
