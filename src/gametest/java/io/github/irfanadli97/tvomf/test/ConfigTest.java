package io.github.irfanadli97.tvomf.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import io.github.irfanadli97.tvomf.MotionHudConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/**
 * Section 6 of BETA-CHECKLIST.md: what the mod does with a missing, old, broken or hand-mangled
 * settings file. Each case writes the file, reloads the settings as the game does at start-up,
 * checks the outcome, then lets the HUD draw for a moment with whatever was loaded. Only runs
 * when {@code config_test_request.txt} exists in the game directory; see {@code run-config-test.ps1}.
 */
public class ConfigTest implements FabricClientGameTest {
	static final String REQUEST = "config_test_request.txt";

	private static final String FULL = """
			{
			  "swayEnabled": true,
			  "maxOffset": 44.0,
			  "damping": 0.37,
			  "sensitivity": 0.25,
			  "motionSensitivity": 28.0,
			  "swayCrosshair": false,
			  "curveEnabled": true,
			  "curveStrength": 9.0,
			  "curveShape": "cylinder",
			  "sprintCurveEnabled": true,
			  "sprintCurveStrength": 18.0,
			  "sprintCurveTransition": 0.5,
			  "sprintZoom": 2.5,
			  "bobEnabled": true,
			  "bobStrength": 2.0,
			  "sprintBobStrength": 3.5,
			  "elements": {
			    "minecraft:hotbar": {"x": 5.0, "y": -7.0, "xPercent": 0.0, "yPercent": 1.5}
			  }
			}
			""";

	private final List<String> results = new ArrayList<>();
	private Path file;
	private Path oldFile;
	private Path brokenCopy;

	static boolean requested() {
		return Files.exists(FabricLoader.getInstance().getGameDir().resolve(REQUEST));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!requested()) {
			return;
		}

		Path config = FabricLoader.getInstance().getConfigDir();
		file = config.resolve("tvomf.json");
		oldFile = config.resolve("gd656motionhud.json");
		brokenCopy = config.resolve("tvomf.json.broken");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(60);

			noFile(context);
			fromOldName(context);
			fromPreviousVersion(context);
			cutOff(context);
			outOfRange(context);
			notANumber(context);
			unknownElements(context);

			// Leave defaults behind for whatever runs next in this folder.
			clear();
			reload(context);
			write(FabricLoader.getInstance().getGameDir().resolve("config_test_results.txt"), String.join("\n", results) + "\n");
		}
	}

	private void noFile(ClientGameTestContext context) {
		clear();
		reload(context);
		MotionHudConfig loaded = MotionHudConfig.get();
		check("1 no file: one is created", Files.exists(file));
		check("1 no file: defaults are in use", loaded.maxOffset == MotionHudConfig.DEFAULT_MAX_OFFSET
				&& loaded.curveStrength == MotionHudConfig.DEFAULT_CURVE_STRENGTH
				&& MotionHudConfig.SHAPE_SPHERE.equals(loaded.curveShape) && loaded.elements.isEmpty());
	}

	private void fromOldName(ClientGameTestContext context) {
		clear();
		write(oldFile, FULL);
		reload(context);
		MotionHudConfig loaded = MotionHudConfig.get();
		check("2 old file name: settings carry over", loaded.maxOffset == 44.0f && !loaded.swayCrosshair
				&& MotionHudConfig.SHAPE_CYLINDER.equals(loaded.curveShape));
		check("2 old file name: element offsets carry over", hotbarIs(loaded, 5.0f, -7.0f, 1.5f));
		check("2 old file name: new file is written", Files.exists(file));
		check("2 old file name: old file is left in place", Files.exists(oldFile));
	}

	private void fromPreviousVersion(ClientGameTestContext context) {
		clear();
		write(file, FULL);
		reload(context);
		MotionHudConfig loaded = MotionHudConfig.get();
		check("3 file from another game version: loads", loaded.damping == 0.37f && loaded.sprintZoom == 2.5f && hotbarIs(loaded, 5.0f, -7.0f, 1.5f));
		check("3 file from another game version: not rewritten", FULL.equals(read(file)));
	}

	private void cutOff(ClientGameTestContext context) {
		clear();
		String half = FULL.substring(0, FULL.length() / 2);
		write(file, half);
		reload(context);
		MotionHudConfig loaded = MotionHudConfig.get();
		check("4 cut-off file: defaults are in use", loaded.maxOffset == MotionHudConfig.DEFAULT_MAX_OFFSET && loaded.elements.isEmpty());
		check("4 cut-off file: the unreadable file is kept as tvomf.json.broken", Files.exists(brokenCopy) && half.equals(read(brokenCopy)));
		check("4 cut-off file: a readable file replaces it", read(file).contains("\"maxOffset\""));
	}

	private void outOfRange(ClientGameTestContext context) {
		clear();
		write(file, """
				{
				  "maxOffset": 9999, "damping": -3, "sensitivity": 50, "motionSensitivity": -1,
				  "curveStrength": 1e9, "sprintCurveStrength": -5, "sprintCurveTransition": 99,
				  "sprintZoom": 500, "bobStrength": 1000, "sprintBobStrength": -2,
				  "curveShape": "banana",
				  "elements": {"minecraft:hotbar": {"x": 1e30, "y": "NaN", "xPercent": -900, "yPercent": 12}}
				}
				""");
		reload(context);
		MotionHudConfig loaded = MotionHudConfig.get();
		check("5 out of range: maxOffset within 0-50", within(loaded.maxOffset, 0, 50));
		check("5 out of range: damping within 0.1-1", within(loaded.damping, 0.1f, 1));
		check("5 out of range: sensitivity within 0.01-1", within(loaded.sensitivity, 0.01f, 1));
		check("5 out of range: motionSensitivity within 0-100", within(loaded.motionSensitivity, 0, 100));
		check("5 out of range: curveStrength within 0-100", within(loaded.curveStrength, 0, 100));
		check("5 out of range: sprintCurveStrength within 0-100", within(loaded.sprintCurveStrength, 0, 100));
		check("5 out of range: sprintCurveTransition within 0-10", within(loaded.sprintCurveTransition, 0, 10));
		check("5 out of range: sprintZoom within 0-20", within(loaded.sprintZoom, 0, 20));
		check("5 out of range: bobStrength within 0-20", within(loaded.bobStrength, 0, 20));
		check("5 out of range: sprintBobStrength within 0-20", within(loaded.sprintBobStrength, 0, 20));
		check("5 out of range: unknown shape falls back to sphere", MotionHudConfig.SHAPE_SPHERE.equals(loaded.curveShape));
		MotionHudConfig.ElementOffset hotbar = loaded.elements.get("minecraft:hotbar");
		check("5 out of range: element offsets are finite and on a sane scale", hotbar != null
				&& within(hotbar.x, -4000, 4000) && within(hotbar.y, -4000, 4000)
				&& within(hotbar.xPercent, -100, 100) && within(hotbar.yPercent, -100, 100));
	}

	private void notANumber(ClientGameTestContext context) {
		clear();
		String text = "{\n  \"maxOffset\": \"lots\",\n  \"curveShape\": \"cylinder\"\n}\n";
		write(file, text);
		reload(context);
		MotionHudConfig loaded = MotionHudConfig.get();
		check("5 text where a number belongs: no crash, default for that value", loaded.maxOffset == MotionHudConfig.DEFAULT_MAX_OFFSET);
		check("5 text where a number belongs: the file as typed is kept as tvomf.json.broken", Files.exists(brokenCopy) && text.equals(read(brokenCopy)));
	}

	private void unknownElements(ClientGameTestContext context) {
		clear();
		write(file, """
				{
				  "elements": {
				    "minecraft:hotbar": {"x": 3, "y": 0, "xPercent": 0, "yPercent": 0},
				    "nomod:nothing": {"x": 3, "y": 0, "xPercent": 0, "yPercent": 0},
				    "Not An Id!!": {"x": 3, "y": 0, "xPercent": 0, "yPercent": 0}
				  }
				}
				""");
		reload(context);
		MotionHudConfig loaded = MotionHudConfig.get();
		check("6 unknown element ids: the real element still gets its offset", hotbarIs(loaded, 3.0f, 0.0f, 0.0f));
		check("6 unknown element ids: nothing is applied for an id that is not a valid id",
				loaded.offsetFor(Identifier.fromNamespaceAndPath("minecraft", "hotbar")) != null);
		// The two log lines this case should produce are checked by run-config-test.ps1.
	}

	private void reload(ClientGameTestContext context) {
		context.runOnClient(minecraft -> MotionHudConfig.load());
		// Draw the HUD for half a second with whatever was loaded.
		context.waitTicks(10);
	}

	private boolean hotbarIs(MotionHudConfig loaded, float x, float y, float yPercent) {
		MotionHudConfig.ElementOffset offset = loaded.offsetFor(Identifier.fromNamespaceAndPath("minecraft", "hotbar"));
		return offset != null && offset.x == x && offset.y == y && offset.yPercent == yPercent;
	}

	private static boolean within(float value, float min, float max) {
		return Float.isFinite(value) && value >= min && value <= max;
	}

	private void check(String name, boolean passed) {
		results.add((passed ? "PASS  " : "FAIL  ") + name);
	}

	private void clear() {
		try {
			Files.deleteIfExists(file);
			Files.deleteIfExists(oldFile);
			Files.deleteIfExists(brokenCopy);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void write(Path path, String text) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, text);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
