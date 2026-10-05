package io.github.irfanadli97.tvomf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Settings stored in {@code config/tvomf.json}.
 *
 * <p>The three sway values and their defaults are the ones from the original Forge mod.
 */
public final class MotionHudConfig {
	public static final float DEFAULT_MAX_OFFSET = 25.0f;
	public static final float DEFAULT_DAMPING = 0.25f;
	public static final float DEFAULT_SENSITIVITY = 0.1f;
	public static final float DEFAULT_MOTION_SENSITIVITY = 16.0f;
	public static final float DEFAULT_CURVE_STRENGTH = 25.0f;
	public static final String SHAPE_SPHERE = "sphere";
	public static final String SHAPE_CYLINDER = "cylinder";
	public static final float DEFAULT_SPRINT_CURVE_STRENGTH = 45.0f;
	public static final float DEFAULT_SPRINT_CURVE_TRANSITION = 0.5f;
	public static final float DEFAULT_SPRINT_ZOOM = 4.0f;
	public static final float DEFAULT_BOB_STRENGTH = 3.0f;
	public static final float DEFAULT_SPRINT_BOB_STRENGTH = 5.0f;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(MotionHudClient.MOD_ID + ".json");
	private static final Path OLD_PATH = FabricLoader.getInstance().getConfigDir().resolve("gd656motionhud.json");
	private static MotionHudConfig instance = new MotionHudConfig();

	/** Master switch for the sway. Per-element offsets still apply when this is off. */
	public boolean swayEnabled = true;
	/** Largest sway distance, in GUI pixels. */
	public float maxOffset = DEFAULT_MAX_OFFSET;
	/** Fraction of the sway offset that survives each 50 ms; lower returns to centre faster. */
	public float damping = DEFAULT_DAMPING;
	/** GUI pixels of sway per degree of camera rotation. */
	public float sensitivity = DEFAULT_SENSITIVITY;
	/** GUI pixels of vertical sway per block the camera moves up or down; 0 turns it off. */
	public float motionSensitivity = DEFAULT_MOTION_SENSITIVITY;
	/** When false the crosshair stays at the screen centre while the rest of the HUD sways. */
	public boolean swayCrosshair = true;
	/** Bends the HUD like a curved visor. Fades out while a screen is open. */
	public boolean curveEnabled = true;
	/** Strength of the bend, 0-100. */
	public float curveStrength = DEFAULT_CURVE_STRENGTH;
	/** {@code sphere} bends towards the centre in every direction, {@code cylinder} only sideways. */
	public String curveShape = SHAPE_SPHERE;
	/** When true the bend eases to {@link #sprintCurveStrength} while the player sprints. */
	public boolean sprintCurveEnabled = true;
	/** Strength of the bend while sprinting, 0-100. */
	public float sprintCurveStrength = DEFAULT_SPRINT_CURVE_STRENGTH;
	/** Seconds the bend takes to change when sprinting starts or stops; 0 is instant. */
	public float sprintCurveTransition = DEFAULT_SPRINT_CURVE_TRANSITION;
	/** Percent the whole HUD shrinks towards the screen centre while sprinting; 0 turns it off. */
	public float sprintZoom = DEFAULT_SPRINT_ZOOM;
	/** Makes the HUD bob in step with the player's walk, like the held item does. */
	public boolean bobEnabled = true;
	/** Height of the bob in GUI pixels at full walking speed; it moves half as far sideways. */
	public float bobStrength = DEFAULT_BOB_STRENGTH;
	/** Height of the bob in GUI pixels while sprinting. */
	public float sprintBobStrength = DEFAULT_SPRINT_BOB_STRENGTH;
	/** Per-element offsets, keyed by HUD element id such as {@code minecraft:hotbar}. */
	public Map<String, ElementOffset> elements = new TreeMap<>();

	private transient Map<Identifier, ElementOffset> resolved = Map.of();

	public static final class ElementOffset {
		/** Offset in GUI pixels. */
		public float x;
		public float y;
		/** Offset as a percentage of the GUI width / height, so it follows the window size. */
		public float xPercent;
		public float yPercent;

		public boolean isZero() {
			return x == 0 && y == 0 && xPercent == 0 && yPercent == 0;
		}
	}

	public static MotionHudConfig get() {
		return instance;
	}

	public static void load() {
		MotionHudConfig loaded = null;
		// Settings saved before the mod was renamed are carried over once.
		boolean migrating = !Files.exists(PATH) && Files.exists(OLD_PATH);
		Path source = migrating ? OLD_PATH : PATH;

		if (Files.exists(source)) {
			try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, MotionHudConfig.class);
			} catch (IOException | JsonParseException e) {
				MotionHudClient.LOGGER.error("Could not read {}, using defaults", source, e);
			}
		}

		instance = loaded != null ? loaded : new MotionHudConfig();
		instance.sanitize();

		if (loaded == null || migrating) {
			instance.save();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());

			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			MotionHudClient.LOGGER.error("Could not write {}", PATH, e);
		}
	}

	public void resetSway() {
		maxOffset = DEFAULT_MAX_OFFSET;
		damping = DEFAULT_DAMPING;
		sensitivity = DEFAULT_SENSITIVITY;
		motionSensitivity = DEFAULT_MOTION_SENSITIVITY;
		curveStrength = DEFAULT_CURVE_STRENGTH;
		sprintCurveStrength = DEFAULT_SPRINT_CURVE_STRENGTH;
		sprintCurveTransition = DEFAULT_SPRINT_CURVE_TRANSITION;
		sprintZoom = DEFAULT_SPRINT_ZOOM;
		bobStrength = DEFAULT_BOB_STRENGTH;
		sprintBobStrength = DEFAULT_SPRINT_BOB_STRENGTH;
	}

	public @Nullable ElementOffset offsetFor(Identifier id) {
		return resolved.get(id);
	}

	/** Call {@link #offsetsChanged()} after editing the returned offset. */
	public ElementOffset getOrCreateOffset(Identifier id) {
		return elements.computeIfAbsent(id.toString(), key -> new ElementOffset());
	}

	public void offsetsChanged() {
		resolve();
	}

	public boolean removeOffset(Identifier id) {
		boolean removed = elements.remove(id.toString()) != null;
		resolve();
		return removed;
	}

	public void clearOffsets() {
		elements.clear();
		resolve();
	}

	private void sanitize() {
		if (!Float.isFinite(maxOffset) || maxOffset < 0) maxOffset = DEFAULT_MAX_OFFSET;
		if (!Float.isFinite(damping)) damping = DEFAULT_DAMPING;
		if (!Float.isFinite(sensitivity)) sensitivity = DEFAULT_SENSITIVITY;
		if (!Float.isFinite(motionSensitivity)) motionSensitivity = DEFAULT_MOTION_SENSITIVITY;
		if (!Float.isFinite(curveStrength)) curveStrength = DEFAULT_CURVE_STRENGTH;
		if (!SHAPE_CYLINDER.equals(curveShape)) curveShape = SHAPE_SPHERE;
		if (!Float.isFinite(sprintCurveStrength)) sprintCurveStrength = DEFAULT_SPRINT_CURVE_STRENGTH;
		if (!Float.isFinite(sprintCurveTransition) || sprintCurveTransition < 0) sprintCurveTransition = DEFAULT_SPRINT_CURVE_TRANSITION;
		if (!Float.isFinite(sprintZoom) || sprintZoom < 0) sprintZoom = DEFAULT_SPRINT_ZOOM;
		if (!Float.isFinite(bobStrength)) bobStrength = DEFAULT_BOB_STRENGTH;
		if (!Float.isFinite(sprintBobStrength)) sprintBobStrength = DEFAULT_SPRINT_BOB_STRENGTH;

		if (elements == null) {
			elements = new TreeMap<>();
		}

		elements.values().removeIf(offset -> offset == null);
		resolve();
	}

	private void resolve() {
		Map<Identifier, ElementOffset> map = new HashMap<>();

		for (Map.Entry<String, ElementOffset> entry : elements.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());

			if (id == null) {
				MotionHudClient.LOGGER.warn("Ignoring invalid HUD element id '{}' in {}", entry.getKey(), PATH.getFileName());
			} else if (!entry.getValue().isZero()) {
				map.put(id, entry.getValue());
			}
		}

		resolved = map;
	}
}
