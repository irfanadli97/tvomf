package io.github.irfanadli97.tvomf.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import io.github.irfanadli97.tvomf.HudSway;
import io.github.irfanadli97.tvomf.MotionHudConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.util.Mth;

/**
 * Shoots the stills and the clip for the project page. Only runs when {@code media_request.txt}
 * in the game directory names the set being shot; see {@code make-media.ps1}.
 */
public class MediaShoot implements FabricClientGameTest {
	static final String REQUEST = "media_request.txt";
	private static final int WIDTH = 1600;
	private static final int HEIGHT = 900;
	private static final double CLIP_SECONDS = 5.0;
	private static final int CLIP_FPS = 30;

	static boolean requested() {
		return Files.exists(FabricLoader.getInstance().getGameDir().resolve(REQUEST));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!requested()) {
			return;
		}

		Path gameDir = FabricLoader.getInstance().getGameDir();
		String set;
		Path out;

		try {
			set = Files.readString(gameDir.resolve(REQUEST)).trim();
			out = gameDir.resolve("media").resolve(set);
			Files.createDirectories(out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		context.getInput().resizeWindow(WIDTH, HEIGHT);
		context.runOnClient(minecraft -> {
			minecraft.options.tutorialStep = TutorialSteps.NONE;
			minecraft.options.autoJump().set(true);
		});

		// A normal generated world rather than the flat test default, so there is scenery.
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("tvomf visor"))
				.create()) {
			// Open grassland makes a clearer backdrop than wherever the world happens to spawn you.
			BlockPos plains = singleplayer.getServer().computeOnServer(server -> {
				var found = server.overworld().findClosestBiome3d(biome -> biome.is(Biomes.PLAINS), new BlockPos(0, 70, 0), 6400, 32, 64);
				return found == null ? null : found.getFirst();
			});

			if (plains != null) {
				singleplayer.getServer().runCommand("spreadplayers " + plains.getX() + " " + plains.getZ() + " 0 2 false @a");
				context.waitTicks(200);
			}

			for (String command : List.of(
					"gamemode survival @a",
					"difficulty peaceful",
					"time set noon",
					"weather clear",
					"item replace entity @a hotbar.0 with minecraft:diamond_sword",
					"item replace entity @a hotbar.1 with minecraft:iron_pickaxe",
					"item replace entity @a hotbar.2 with minecraft:torch 32",
					"item replace entity @a hotbar.3 with minecraft:golden_carrot 16",
					"item replace entity @a hotbar.4 with minecraft:oak_log 48",
					"item replace entity @a hotbar.5 with minecraft:water_bucket",
					"item replace entity @a armor.head with minecraft:iron_helmet",
					"item replace entity @a armor.chest with minecraft:iron_chestplate",
					"item replace entity @a armor.legs with minecraft:iron_leggings",
					"item replace entity @a armor.feet with minecraft:iron_boots",
					"xp add @a 17 levels")) {
				singleplayer.getServer().runCommand(command);
			}

			float baseYaw = context.computeOnClient(MediaShoot::clearestYaw);
			// The full set looks down a little further, so mods that describe the block you are
			// looking at have one within reach.
			float stillPitch = set.equals("full") ? 20.0f : 11.0f;
			context.runOnClient(minecraft -> {
				minecraft.player.setYRot(baseYaw);
				minecraft.player.setXRot(stillPitch);
			});
			context.waitTicks(10);

			// A few animals off to the sides, for scenery and for minimap and tooltip mods.
			for (String command : List.of(
					"execute as @a at @s run summon minecraft:cow ^-5 ^3 ^8",
					"execute as @a at @s run summon minecraft:sheep ^6 ^3 ^10",
					"execute as @a at @s run summon minecraft:pig ^-2 ^3 ^14")) {
				singleplayer.getServer().runCommand(command);
			}
			// Long enough for toasts and first-run chat lines from other mods to go away.
			context.waitTicks(320);

			still(context, set + "_1_visor_sphere", config -> {
			});
			still(context, set + "_2_visor_cylinder", config -> config.curveShape = MotionHudConfig.SHAPE_CYLINDER);
			still(context, set + "_3_visor_sphere_strong", config -> config.curveStrength = 70);
			still(context, set + "_4_flat_for_comparison", config -> config.curveEnabled = false);

			clip(context, out, baseYaw);
			write(gameDir.resolve("media_done.txt"), List.of(set));
		}
	}

	/** The mod's default settings, so the pictures show what a new install looks like. */
	private static void defaults(MotionHudConfig config) {
		config.resetSway();
		config.swayEnabled = true;
		config.swayCrosshair = true;
		config.curveEnabled = true;
		config.curveShape = MotionHudConfig.SHAPE_SPHERE;
		config.sprintCurveEnabled = true;
		config.bobEnabled = true;
		config.clearOffsets();
	}

	private static void still(ClientGameTestContext context, String name, Consumer<MotionHudConfig> change) {
		context.runOnClient(minecraft -> {
			MotionHudConfig config = MotionHudConfig.get();
			defaults(config);
			change.accept(config);
			HudSway.setTestOffset(Float.NaN, Float.NaN);
		});
		context.waitTicks(20);
		context.takeScreenshot(name);
	}

	private static void clip(ClientGameTestContext context, Path out, float baseYaw) {
		context.runOnClient(minecraft -> defaults(MotionHudConfig.get()));
		context.waitTicks(20);

		context.runOnClient(minecraft -> FrameCapture.start(out.resolve("clip.raw"), out.resolve("clip.meta"), CLIP_SECONDS, CLIP_FPS,
				time -> new float[] {baseYaw + yaw(time), 14.0f - 5.0f * ease(time, 2.6, 5.0)}));

		boolean walking = false;
		boolean jumped = false;
		boolean sprinting = false;

		while (!FrameCapture.finished()) {
			double time = FrameCapture.elapsedSeconds();

			if (!walking && time > 0) {
				context.getInput().holdKey(options -> options.keyUp);
				walking = true;
			}

			if (!jumped && time >= 2.0) {
				context.getInput().holdKeyFor(options -> options.keyJump, 2);
				jumped = true;
			}

			if (!sprinting && time >= 2.7) {
				context.getInput().holdKey(options -> options.keySprint);
				sprinting = true;
			}

			context.waitTick();
		}

		context.getInput().releaseKey(options -> options.keySprint);
		context.getInput().releaseKey(options -> options.keyUp);
	}

	/** The direction with the most open space ahead, so the camera is not staring at a tree. */
	private static float clearestYaw(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		float best = player.getYRot();
		double bestScore = -1;

		for (int yaw = -180; yaw < 180; yaw += 15) {
			// The clip swings about 35 degrees either side, so the view has to be open there too.
			double score = 16;

			for (int offset = -45; offset <= 45; offset += 15) {
				score = Math.min(score, clearDistance(minecraft, player, yaw + offset, player.getEyeY(), 16) + 0.01 * clearDistance(minecraft, player, yaw + offset, player.getEyeY(), 64));
			}

			// And nothing to walk into.
			score = Math.min(score, clearDistance(minecraft, player, yaw, player.getY() + 1.2, 16));

			if (score > bestScore) {
				bestScore = score;
				best = yaw;
			}
		}

		return best;
	}

	private static double clearDistance(Minecraft minecraft, LocalPlayer player, float yaw, double height, double range) {
		Vec3 from = new Vec3(player.getX(), height, player.getZ());
		Vec3 to = from.add(Vec3.directionFromRotation(0, yaw).scale(range));
		BlockHitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		return hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(from);
	}

	/** Look right, snap left, then come back round while sprinting. */
	private static float yaw(double time) {
		if (time < 1.0) {
			return 25.0f * ease(time, 0.0, 1.0);
		} else if (time < 1.5) {
			return Mth.lerp(ease(time, 1.0, 1.5), 25.0f, -35.0f);
		} else if (time < 2.6) {
			return Mth.lerp(ease(time, 1.5, 2.6), -35.0f, -28.0f);
		}

		return Mth.lerp(ease(time, 2.6, 5.0), -28.0f, 6.0f);
	}

	/** 0 before {@code from}, 1 after {@code to}, smooth in between. */
	private static float ease(double time, double from, double to) {
		float x = (float) Mth.clamp((time - from) / (to - from), 0.0, 1.0);
		return x * x * (3.0f - 2.0f * x);
	}

	private static void write(Path path, List<String> lines) {
		try {
			Files.write(path, lines);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
