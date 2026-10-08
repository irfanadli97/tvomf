package io.github.irfanadli97.tvomf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
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
	private static final Path BROKEN_PATH = FabricLoader.getInstance().getConfigDir().resolve(MotionHudClient.MOD_ID + ".json.broken");
	/** The settings screen's limit for a pixel offset. */
	private static final float MAX_OFFSET_PIXELS = 4000.0f;
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
			// Anything at all can be wrong with a hand-edited file, and Gson does not report every
			// kind of mistake the same way (text where a number belongs is a NumberFormatException),
			// so every failure is treated alike: never let it stop the game from starting.
			try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, MotionHudConfig.class);
			} catch (IOException | RuntimeException e) {
				MotionHudClient.LOGGER.error("Could not read {} ({}); using the default settings. The file as it was is kept as {}",
						source.getFileName(), e.getMessage(), BROKEN_PATH.getFileName());
				keepUnreadable(source);
			}
		}

		instance = loaded != null ? loaded : new MotionHudConfig();
		instance.sanitize();
		HudElements.reportUnknownOffsets();

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
		// The same ranges the command and the settings screen enforce; a file edited by hand is
		// the one way round them.
		maxOffset = inRange("maxOffset", maxOffset, 0.0f, 50.0f, DEFAULT_MAX_OFFSET);
		damping = inRange("damping", damping, 0.1f, 1.0f, DEFAULT_DAMPING);
		sensitivity = inRange("sensitivity", sensitivity, 0.01f, 1.0f, DEFAULT_SENSITIVITY);
		motionSensitivity = inRange("motionSensitivity", motionSensitivity, 0.0f, 100.0f, DEFAULT_MOTION_SENSITIVITY);
		curveStrength = inRange("curveStrength", curveStrength, 0.0f, 100.0f, DEFAULT_CURVE_STRENGTH);
		sprintCurveStrength = inRange("sprintCurveStrength", sprintCurveStrength, 0.0f, 100.0f, DEFAULT_SPRINT_CURVE_STRENGTH);
		sprintCurveTransition = inRange("sprintCurveTransition", sprintCurveTransition, 0.0f, 10.0f, DEFAULT_SPRINT_CURVE_TRANSITION);
		sprintZoom = inRange("sprintZoom", sprintZoom, 0.0f, HudCurve.MAX_ZOOM, DEFAULT_SPRINT_ZOOM);
		bobStrength = inRange("bobStrength", bobStrength, 0.0f, 20.0f, DEFAULT_BOB_STRENGTH);
		sprintBobStrength = inRange("sprintBobStrength", sprintBobStrength, 0.0f, 20.0f, DEFAULT_SPRINT_BOB_STRENGTH);

		if (!SHAPE_CYLINDER.equals(curveShape) && !SHAPE_SPHERE.equals(curveShape)) {
			MotionHudClient.LOGGER.warn("curveShape '{}' in {} is neither sphere nor cylinder; using sphere", curveShape, PATH.getFileName());
			curveShape = SHAPE_SPHERE;
		}

		if (elements == null) {
			elements = new TreeMap<>();
		}

		elements.values().removeIf(offset -> offset == null);

		for (Map.Entry<String, ElementOffset> entry : elements.entrySet()) {
			ElementOffset offset = entry.getValue();
			String name = "elements." + entry.getKey();
			offset.x = inRange(name + ".x", offset.x, -MAX_OFFSET_PIXELS, MAX_OFFSET_PIXELS, 0.0f);
			offset.y = inRange(name + ".y", offset.y, -MAX_OFFSET_PIXELS, MAX_OFFSET_PIXELS, 0.0f);
			offset.xPercent = inRange(name + ".xPercent", offset.xPercent, -100.0f, 100.0f, 0.0f);
			offset.yPercent = inRange(name + ".yPercent", offset.yPercent, -100.0f, 100.0f, 0.0f);
		}

		resolve();
	}

	/**
	 * {@code value} if it is a number within the range; the nearest end of the range if it is
	 * outside; {@code fallback} if it is not a number at all. Says so in the log when it changes it.
	 */
	private static float inRange(String name, float value, float min, float max, float fallback) {
		float fixed = Float.isNaN(value) ? fallback : Mth.clamp(value, min, max);

		if (fixed != value) {
			MotionHudClient.LOGGER.warn("{} in {} was {}, outside {} to {}; using {}", name, PATH.getFileName(), value, min, max, fixed);
		}

		return fixed;
	}

	private static void keepUnreadable(Path source) {
		try {
			Files.copy(source, BROKEN_PATH, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			MotionHudClient.LOGGER.error("Could not keep a copy of {}", source.getFileName(), e);
		}
	}

	/**
	 * Called once the HUD elements are known: an offset for an id no element has does nothing, and
	 * is most likely a typing mistake.
	 */
	public void warnAboutUnknownElements(java.util.Collection<Identifier> known) {
		for (Identifier id : resolved.keySet()) {
			if (!known.contains(id)) {
				MotionHudClient.LOGGER.warn("No HUD element is called '{}'; its offset in {} is ignored. See /{} element list", id, PATH.getFileName(), MotionHudClient.MOD_ID);
			}
		}
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
