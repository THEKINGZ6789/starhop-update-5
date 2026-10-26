package dev.starhop.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import dev.starhop.Starhop;
import dev.starhop.StarhopSounds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side platforming moves, each with its own sound and particle animation:
 *  - Triple jump:  chain jumps shortly after landing while moving.
 *  - Long jump:    run, then crouch and jump. Low and very fast.
 *  - Backflip:     crouch and jump while standing still. Very high, a little backwards.
 *  - Wall jump:    slide down a wall, then press jump to kick off it.
 *  - Spin:         press the Spin key once per jump to hover briefly.
 *  - Ground pound: press crouch in mid-air. Hover, then slam down. The damage is done by the server.
 *  - Stomp:        land on top of a monster to bounce off it. Hold jump to bounce higher.
 *
 * Heights are tuned to stay under 3 blocks so none of these cause fall damage by themselves.
 * Velocities below are "initial jump speeds" (the vanilla jump is 0.42).
 */
public class StarhopClient implements ClientModInitializer {
	// Triple jump
	private static final double JUMP_STAGE_2 = 0.52;
	private static final double JUMP_STAGE_3 = 0.62;
	// Max ticks between landing and the next jump for it to count as a chain.
	// Holding jump in vanilla auto-jumps every 10 ticks, so this must be above 10.
	private static final int CHAIN_WINDOW_TICKS = 12;
	private static final double MIN_CHAIN_SPEED = 0.08;

	// Spin
	private static final int SPIN_TICKS = 12;
	private static final double SPIN_MAX_FALL_SPEED = -0.08;

	// Long jump and backflip
	private static final int SNEAK_GRACE_TICKS = 6;
	private static final double LONG_JUMP_MIN_SPEED = 0.09;
	private static final double LONG_JUMP_UP = 0.40;
	private static final double LONG_JUMP_SPEED = 0.70;
	private static final double BACKFLIP_UP = 0.62;
	private static final double BACKFLIP_PUSH = 0.18;

	// Wall jump
	private static final double WALL_SLIDE_SPEED = -0.14;
	private static final double WALL_JUMP_UP = 0.52;
	private static final double WALL_JUMP_PUSH = 0.42;
	private static final int WALL_COOLDOWN_TICKS = 6;

	// Ground pound. The server recognises POUND_SPEED exactly, so change both together.
	private static final double POUND_SPEED = -1.5;
	private static final int POUND_WINDUP_TICKS = 8;
	private static final int POUND_MIN_AIR_TICKS = 4;

	// Stomp
	private static final double STOMP_BOUNCE = 0.40;
	private static final double STOMP_BOUNCE_HELD = 0.50;
	private static final double STOMP_BOUNCE_CHAIN = 0.60;
	private static final int STOMP_CHAIN_TICKS = 40;
	private static final int STOMP_COOLDOWN_TICKS = 6;

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Starhop.MOD_ID, "starhop"));

	private static KeyMapping spinKey;

	private Minecraft mc;

	private boolean wasOnGround = true;
	private boolean wasSneak = false;
	private boolean wasJump = false;
	private int airTicks = 0;
	private int ticksSinceLanding = 100;
	private int jumpStage = 0;

	private boolean spunThisJump = false;
	private int spinTicksLeft = 0;

	private int sneakGrace = 0;
	private double recentSpeed = 0.0;
	private Vec3 lastGroundDir = new Vec3(0.0, 0.0, 1.0);
	private Vec3 lastAirDir = null;

	private int trailTicks = 0;
	private int trailKind = 0; // 1 = triple jump, 2 = long jump, 3 = backflip

	private int wallCooldown = 0;

	private int poundPhase = 0; // 0 = none, 1 = hovering, 2 = slamming
	private int poundTimer = 0;

	private int stompChain = 0;
	private int stompChainTimer = 0;
	private int stompCooldown = 0;

	@Override
	public void onInitializeClient() {
		spinKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.starhop.spin", InputConstants.KEY_R, CATEGORY)
		);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft minecraft) {
		this.mc = minecraft;
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.isPaused()) {
			return;
		}

		boolean onGround = player.onGround();
		boolean sneak = player.isShiftKeyDown();
		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !wasJump;
		boolean sneakPressed = sneak && !wasSneak;

		// Don't interfere with flying, swimming, climbing, riding or spectating.
		if (player.isSpectator() || player.getAbilities().flying || player.isInWater()
			|| player.isInLava() || player.onClimbable() || player.isPassenger()) {
			resetState(onGround, sneak, jumpDown);
			// Drain any queued spin presses so they don't fire later.
			while (spinKey.consumeClick()) { /* discard */ }
			return;
		}

		Vec3 v = player.getDeltaMovement();
		double hSpeed = v.horizontalDistance();

		if (wallCooldown > 0) {
			wallCooldown--;
		}
		if (stompCooldown > 0) {
			stompCooldown--;
		}
		if (stompChainTimer > 0) {
			stompChainTimer--;
		} else {
			stompChain = 0;
		}

		// --- Ground bookkeeping ---
		if (onGround) {
			airTicks = 0;
			if (sneak) {
				sneakGrace = SNEAK_GRACE_TICKS;
			} else if (sneakGrace > 0) {
				sneakGrace--;
			}
			// Remember how fast we were running, so a crouch right before a jump still counts as a run.
			recentSpeed = Math.max(hSpeed, recentSpeed * 0.85);
			if (hSpeed > 0.02) {
				lastGroundDir = new Vec3(v.x / hSpeed, 0.0, v.z / hSpeed);
			}
		} else {
			airTicks++;
		}

		// --- Landing ---
		if (onGround && !wasOnGround) {
			ticksSinceLanding = 0;
			spunThisJump = false;
			spinTicksLeft = 0;
			wallCooldown = 0;
			poundPhase = 0;
			trailTicks = 0;
			lastAirDir = null;
		} else if (onGround) {
			ticksSinceLanding = Math.min(ticksSinceLanding + 1, 100);
		}

		// --- Takeoff: left the ground while moving upward ---
		if (wasOnGround && !onGround && v.y > 0.1) {
			if (sneak || sneakGrace > 0) {
				if (recentSpeed > LONG_JUMP_MIN_SPEED) {
					longJump(player);
				} else {
					backflip(player);
				}
				jumpStage = 0;
			} else {
				boolean chained = jumpStage > 0
					&& ticksSinceLanding <= CHAIN_WINDOW_TICKS
					&& hSpeed > MIN_CHAIN_SPEED;
				jumpStage = chained ? (jumpStage % 3) + 1 : 1;

				if (jumpStage >= 2) {
					double jumpVelocity = (jumpStage == 3) ? JUMP_STAGE_3 : JUMP_STAGE_2;
					// The vanilla jump already moved us this tick; set next tick's upward speed
					// as if we had started at jumpVelocity (gravity 0.08, drag 0.98).
					player.setDeltaMovement(v.x, launchSpeed(jumpVelocity), v.z);
					puff(player, jumpStage == 3 ? 10 : 5);
					play(player, jumpStage == 3 ? StarhopSounds.JUMP_TRIPLE : StarhopSounds.JUMP_DOUBLE, 1.0f);
					if (jumpStage == 3) {
						trailTicks = 14;
						trailKind = 1;
					}
				}
			}
		}

		// --- Ground pound ---
		if (!onGround && poundPhase == 0 && sneakPressed && airTicks >= POUND_MIN_AIR_TICKS) {
			poundPhase = 1;
			poundTimer = POUND_WINDUP_TICKS;
			spinTicksLeft = 0;
			jumpStage = 0;
			trailTicks = 0;
			play(player, StarhopSounds.POUND_START, 1.0f);
		}
		if (poundPhase == 1 && !onGround) {
			player.setDeltaMovement(0.0, 0.0, 0.0);
			ring(player, ParticleTypes.END_ROD, 0.8, 4, 1.0, poundTimer * 0.8);
			if (--poundTimer <= 0) {
				poundPhase = 2;
			}
		} else if (poundPhase == 2 && !onGround) {
			player.setDeltaMovement(0.0, POUND_SPEED, 0.0);
			puff(player, 1);
		}

		// --- Wall slide and wall jump ---
		if (!onGround && poundPhase == 0) {
			if (!player.horizontalCollision) {
				if (hSpeed > 0.02) {
					lastAirDir = new Vec3(v.x / hSpeed, 0.0, v.z / hSpeed);
				}
			} else {
				if (v.y < WALL_SLIDE_SPEED) {
					player.setDeltaMovement(v.x, WALL_SLIDE_SPEED, v.z);
					if (airTicks % 3 == 0) {
						puff(player, 1);
					}
				}
				if (jumpPressed && wallCooldown == 0 && airTicks >= 2) {
					wallJump(player);
				}
			}
		}

		// --- Stomp ---
		if (!onGround && stompCooldown == 0 && player.getDeltaMovement().y < -0.1) {
			LivingEntity target = findStompTarget(player);
			if (target != null) {
				stomp(player, jumpDown);
			}
		}

		// --- Spin ---
		boolean spinRequested = false;
		while (spinKey.consumeClick()) {
			spinRequested = true;
		}
		if (spinRequested && !onGround && !spunThisJump && poundPhase == 0) {
			spunThisJump = true;
			spinTicksLeft = SPIN_TICKS;
			Vec3 sv = player.getDeltaMovement();
			player.setDeltaMovement(sv.x, Math.max(sv.y, 0.2), sv.z);
			play(player, StarhopSounds.SPIN, 1.0f);
		}
		if (spinTicksLeft > 0 && !onGround) {
			spinTicksLeft--;
			Vec3 sv = player.getDeltaMovement();
			if (sv.y < SPIN_MAX_FALL_SPEED) {
				player.setDeltaMovement(sv.x, SPIN_MAX_FALL_SPEED, sv.z);
			}
			ring(player, ParticleTypes.END_ROD, 0.9, 3, 1.0, spinTicksLeft * 0.9);
		}

		// --- Trails ---
		if (trailTicks > 0) {
			trailTicks--;
			trail(player);
		}

		wasOnGround = onGround;
		wasSneak = sneak;
		wasJump = jumpDown;
	}

	// ------------------------------------------------------------------ moves

	/** Vertical speed to set this tick so the next tick behaves as if we jumped at jumpVelocity. */
	private static double launchSpeed(double jumpVelocity) {
		return (jumpVelocity - 0.08) * 0.98;
	}

	private void longJump(LocalPlayer player) {
		Vec3 dir = lastGroundDir;
		player.setDeltaMovement(dir.x * LONG_JUMP_SPEED, launchSpeed(LONG_JUMP_UP), dir.z * LONG_JUMP_SPEED);
		puff(player, 8);
		play(player, StarhopSounds.LONG_JUMP, 1.0f);
		trailTicks = 12;
		trailKind = 2;
	}

	private void backflip(LocalPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		player.setDeltaMovement(-flat.x * BACKFLIP_PUSH, launchSpeed(BACKFLIP_UP), -flat.z * BACKFLIP_PUSH);
		puff(player, 6);
		play(player, StarhopSounds.BACKFLIP, 1.0f);
		trailTicks = 16;
		trailKind = 3;
	}

	private void wallJump(LocalPlayer player) {
		Vec3 away;
		if (lastAirDir != null) {
			away = new Vec3(-lastAirDir.x, 0.0, -lastAirDir.z);
		} else {
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(-look.x, 0.0, -look.z);
			away = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		}
		player.setDeltaMovement(away.x * WALL_JUMP_PUSH, launchSpeed(WALL_JUMP_UP), away.z * WALL_JUMP_PUSH);
		wallCooldown = WALL_COOLDOWN_TICKS;
		spunThisJump = false;
		jumpStage = 0;
		lastAirDir = away;
		puff(player, 8);
		play(player, StarhopSounds.WALL_JUMP, 1.0f);
	}

	private void stomp(LocalPlayer player, boolean jumpHeld) {
		stompChain = (stompChainTimer > 0) ? stompChain + 1 : 1;
		stompChainTimer = STOMP_CHAIN_TICKS;
		stompCooldown = STOMP_COOLDOWN_TICKS;

		double up = jumpHeld ? STOMP_BOUNCE_HELD : STOMP_BOUNCE;
		if (stompChain % 3 == 0) {
			up = STOMP_BOUNCE_CHAIN;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x, up, v.z);

		poundPhase = 0;
		spunThisJump = false;
		spinTicksLeft = 0;
		for (int i = 0; i < 8; i++) {
			player.level().addParticle(ParticleTypes.CRIT,
				player.getX(), player.getY() + 0.1, player.getZ(),
				(player.getRandom().nextDouble() - 0.5) * 0.4, 0.2, (player.getRandom().nextDouble() - 0.5) * 0.4);
		}
		// The stomp sound and the damage come from the server, so everyone nearby hears them.
	}

	private LivingEntity findStompTarget(LocalPlayer player) {
		AABB box = player.getBoundingBox().inflate(0.1, 0.0, 0.1).expandTowards(0.0, -0.6, 0.0);
		List<LivingEntity> list = player.level().getEntitiesOfClass(
			LivingEntity.class, box, e -> e != player && e.isAlive() && e instanceof Enemy);
		double feet = player.getY();
		for (LivingEntity e : list) {
			double top = e.getBoundingBox().maxY;
			if (top <= feet + 0.35 && top >= feet - 0.7) {
				return e;
			}
		}
		return null;
	}

	// ------------------------------------------------------------- animations

	private void trail(LocalPlayer player) {
		double x = player.getX();
		double y = player.getY() + 0.9;
		double z = player.getZ();
		switch (trailKind) {
			case 1 -> {
				// Triple jump: a sparkling spiral.
				double a = trailTicks * 1.1;
				player.level().addParticle(ParticleTypes.END_ROD, x + Math.cos(a) * 0.5, y, z + Math.sin(a) * 0.5, 0.0, 0.0, 0.0);
				player.level().addParticle(ParticleTypes.FIREWORK, x - Math.cos(a) * 0.5, y, z - Math.sin(a) * 0.5, 0.0, 0.0, 0.0);
			}
			case 2 -> {
				// Long jump: a streak of cloud behind you.
				player.level().addParticle(ParticleTypes.CLOUD, x, y - 0.3, z, 0.0, 0.0, 0.0);
				player.level().addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, 0.0, 0.0);
			}
			case 3 -> {
				// Backflip: a ring that follows the flip.
				ring(player, ParticleTypes.CRIT, 0.7, 5, 0.9, trailTicks * 0.7);
			}
			default -> {
			}
		}
	}

	private void ring(LocalPlayer player, ParticleOptions type, double radius, int count, double yOffset, double phase) {
		for (int i = 0; i < count; i++) {
			double a = phase + i * (Math.PI * 2.0 / count);
			player.level().addParticle(type,
				player.getX() + Math.cos(a) * radius, player.getY() + yOffset, player.getZ() + Math.sin(a) * radius,
				0.0, 0.0, 0.0);
		}
	}

	private void puff(LocalPlayer player, int count) {
		for (int i = 0; i < count; i++) {
			double dx = (player.getRandom().nextDouble() - 0.5) * 0.6;
			double dz = (player.getRandom().nextDouble() - 0.5) * 0.6;
			player.level().addParticle(ParticleTypes.CLOUD,
				player.getX() + dx, player.getY() + 0.1, player.getZ() + dz, 0.0, 0.02, 0.0);
		}
	}

	private void play(LocalPlayer player, SoundEvent sound, float volume) {
		float pitch = 0.95f + player.getRandom().nextFloat() * 0.1f;
		mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
			sound, SoundSource.PLAYERS, volume, pitch, false);
	}

	private void resetState(boolean onGround, boolean sneak, boolean jumpDown) {
		wasOnGround = onGround;
		wasSneak = sneak;
		wasJump = jumpDown;
		airTicks = 0;
		jumpStage = 0;
		spunThisJump = false;
		spinTicksLeft = 0;
		poundPhase = 0;
		trailTicks = 0;
		wallCooldown = 0;
		stompChain = 0;
		lastAirDir = null;
	}
}
