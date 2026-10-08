package io.github.irfanadli97.tvomf.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import io.github.irfanadli97.tvomf.HudCurve;
import io.github.irfanadli97.tvomf.HudSway;
import io.github.irfanadli97.tvomf.MotionHudConfig;

/**
 * Takes screenshots of the HUD with a fixed sway offset and with the curve on and off, so the
 * effect on every element (including other mods' HUDs) can be compared by eye, then records the
 * sway frame by frame during a long fall.
 */
public class HudVisualTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (MediaShoot.requested()) {
			return;
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);

			shot(context, "1_flat_rest", 0, 0, false);
			shot(context, "2_flat_swayed", 24, 16, false);
			shot(context, "3_curved_rest", 0, 0, true);
			shot(context, "4_curved_swayed", 24, 16, true);

			// Sneaking makes mods that draw the player model on the HUD show it.
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(20);
			shot(context, "4a_sneak_flat_rest", 0, 0, false);
			shot(context, "4b_sneak_flat_swayed", 24, 16, false);
			shot(context, "4c_sneak_curved_swayed", 24, 16, true);
			// Pushes the model's clipping area past the top left of the window, which the game
			// rejects with a crash unless it is cut back to the screen.
			shot(context, "4d_sneak_flat_swayed_off_screen", -60, -60, false);
			shot(context, "4e_sneak_curved_swayed_off_screen", -60, -60, true);
			context.getInput().releaseKey(options -> options.keyShift);

			// The spyglass view is a scope texture with black bars round it. None of it may sway
			// or bend, or the world shows through at the screen edges and between the pieces.
			singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:spyglass");
			context.waitTicks(10);
			// The scope grows from half size for the first few ticks; catch it part-way, with the
			// curve already on.
			context.runOnClient(minecraft -> {
				MotionHudConfig config = MotionHudConfig.get();
				config.curveEnabled = true;
				config.curveStrength = 100;
				config.curveShape = MotionHudConfig.SHAPE_CYLINDER;
				HudSway.setTestOffset(24, 16);
			});
			context.waitTicks(10);
			context.getInput().holdMouse(1);
			context.takeScreenshot("4f0a_spyglass_opening");
			context.takeScreenshot("4f0b_spyglass_opening");
			context.takeScreenshot("4f0c_spyglass_opening");
			context.runOnClient(minecraft -> MotionHudConfig.get().curveShape = MotionHudConfig.SHAPE_SPHERE);
			context.waitTicks(30);
			shot(context, "4f_spyglass_flat_rest", 0, 0, false);
			shot(context, "4g_spyglass_curved_swayed", 24, 16, true);
			context.runOnClient(minecraft -> MotionHudConfig.get().curveShape = MotionHudConfig.SHAPE_CYLINDER);
			shot(context, "4h_spyglass_cylinder_swayed", 24, 16, true);
			context.runOnClient(minecraft -> MotionHudConfig.get().curveShape = MotionHudConfig.SHAPE_SPHERE);
			context.getInput().releaseMouse(1);
			singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			context.waitTicks(10);

			// The debug screen stays flat and still while the HUD under it is curved and swayed.
			context.getInput().pressKey(InputConstants.KEY_F3);
			context.waitTicks(5);
			shot(context, "4i_debug_screen_curved_swayed", 24, 16, true);
			context.getInput().pressKey(InputConstants.KEY_F3);
			context.waitTicks(5);

			busyHud(context, singleplayer);
			traceMotion(context, singleplayer);

			// Other mods can stall the client while the world closes, so tell the runner now.
			write("test_done.txt", List.of("done"));
		}
	}

	private static void shot(ClientGameTestContext context, String name, float swayX, float swayY, boolean curve) {
		context.runOnClient(minecraft -> {
			MotionHudConfig config = MotionHudConfig.get();
			config.swayEnabled = true;
			config.curveEnabled = curve;
			config.curveStrength = 100;
			HudSway.setTestOffset(swayX, swayY);
		});
		// Long enough for the curve to finish fading in or out.
		context.waitTicks(10);
		context.takeScreenshot(name);
	}

	/**
	 * Fills the HUD with as many kinds of element as commands can produce, so the curve is
	 * exercised on items, effects, bars, titles and whatever other mods draw for them.
	 */
	private static void busyHud(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		Set<String> extraDataUsers = context.computeOnClient(minecraft -> HudCurve.recordExtraDataUsers());

		for (String command : List.of(
				"give @a minecraft:diamond_sword",
				"give @a minecraft:golden_apple 5",
				"give @a minecraft:oak_planks 64",
				"give @a minecraft:enchanted_book[minecraft:stored_enchantments={\"minecraft:sharpness\":3}]",
				"give @a minecraft:shield",
				"xp add @a 30 levels",
				"effect give @a minecraft:speed 600 1",
				"effect give @a minecraft:hunger 600 0",
				"bossbar add test \"Boss bar\"",
				"bossbar set minecraft:test players @a",
				"scoreboard objectives add visual dummy \"Scoreboard\"",
				"scoreboard objectives setdisplay sidebar visual",
				"scoreboard players set @a visual 7",
				"title @a subtitle \"Subtitle text\"",
				"title @a title \"Title text\"",
				"title @a actionbar \"Action bar text\"")) {
			singleplayer.getServer().runCommand(command);
		}

		// Look at the ground so mods that describe the targeted block have something to show.
		context.runOnClient(minecraft -> minecraft.player.setXRot(70.0f));
		context.waitTicks(30);
		shot(context, "5_busy_flat", 0, 0, false);
		shot(context, "6_busy_curved_swayed", 24, 16, true);
		shot(context, "6a_busy_sphere_rest", 0, 0, true);
		context.runOnClient(minecraft -> MotionHudConfig.get().curveShape = MotionHudConfig.SHAPE_CYLINDER);
		shot(context, "6b_busy_cylinder_rest", 0, 0, true);
		context.runOnClient(minecraft -> MotionHudConfig.get().curveShape = MotionHudConfig.SHAPE_SPHERE);

		// Mobs around the player, for minimap mods that mark them. Splitting quads far more
		// finely than normal makes even small markers and glyphs go through the blending.
		for (String command : List.of(
				"summon minecraft:cow ~4 ~ ~2 {NoAI:1b}",
				"summon minecraft:pig ~-5 ~ ~3 {NoAI:1b}",
				"summon minecraft:sheep ~2 ~ ~-6 {NoAI:1b}",
				"summon minecraft:creeper ~-3 ~ ~-4 {NoAI:1b}",
				"summon minecraft:villager ~6 ~ ~-2 {NoAI:1b}")) {
			singleplayer.getServer().runCommand(command);
		}

		context.waitTicks(80);
		shot(context, "7_mobs_flat", 0, 0, false);
		shot(context, "8_mobs_curved", 0, 0, true);
		context.runOnClient(minecraft -> HudCurve.setTestCellSize(3.0f));
		shot(context, "9_mobs_curved_fine_split", 0, 0, true);
		context.runOnClient(minecraft -> HudCurve.setTestCellSize(16.0f));

		List<String> lines = new ArrayList<>(extraDataUsers);
		lines.addFirst("Elements that write more than position, colour and texture coordinates:");
		write("extra_data_users.txt", lines);
	}

	private static void traceMotion(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		List<String> lines = new ArrayList<>();
		lines.add("ms,yaw,pitch,height,offsetX,offsetY");

		context.runOnClient(minecraft -> {
			MotionHudConfig config = MotionHudConfig.get();
			config.resetSway();
			config.curveEnabled = false;
			HudSway.setTestOffset(Float.NaN, Float.NaN);
		});
		// Start high up so the player falls for the whole trace.
		singleplayer.getServer().runCommand("tp @a ~ ~200 ~");
		context.waitTicks(10);
		context.runOnClient(minecraft -> HudSway.setTrace(row -> lines.add(String.format(Locale.ROOT,
				"%.3f,%.3f,%.3f,%.4f,%.3f,%.3f", row[0], row[1], row[2], row[3], row[4], row[5]))));

		context.waitTicks(40);
		context.runOnClient(minecraft -> HudSway.setTrace(null));
		write("sway_trace.csv", lines);
	}

	private static void write(String name, List<String> lines) {
		try {
			Path path = FabricLoader.getInstance().getGameDir().resolve(name);
			Files.write(path, lines);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
