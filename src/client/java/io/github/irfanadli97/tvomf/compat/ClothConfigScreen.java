package io.github.irfanadli97.tvomf.compat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import io.github.irfanadli97.tvomf.HudElements;
import io.github.irfanadli97.tvomf.MotionHudConfig;

/** The settings screen. Only loaded when Cloth Config is installed. */
final class ClothConfigScreen {
	private ClothConfigScreen() {
	}

	static Screen create(Screen parent) {
		MotionHudConfig config = MotionHudConfig.get();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("There's a Visor On My Face"));
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory sway = builder.getOrCreateCategory(Component.literal("Sway"));
		sway.addEntry(entries.startBooleanToggle(Component.literal("Sway enabled"), config.swayEnabled)
				.setDefaultValue(true)
				.setTooltip(Component.literal("Turns the HUD sway on or off. Element offsets still apply when off."))
				.setSaveConsumer(value -> config.swayEnabled = value)
				.build());
		sway.addEntry(entries.startBooleanToggle(Component.literal("Crosshair sways"), config.swayCrosshair)
				.setDefaultValue(true)
				.setTooltip(Component.literal("When off, the crosshair stays at the screen centre."))
				.setSaveConsumer(value -> config.swayCrosshair = value)
				.build());
		sway.addEntry(slider(entries, "Max offset", config.maxOffset, MotionHudConfig.DEFAULT_MAX_OFFSET, 1, 0, 50, "%.0f px",
				"Largest distance the HUD can sway, in GUI pixels.", value -> config.maxOffset = value));
		sway.addEntry(slider(entries, "Damping", config.damping, MotionHudConfig.DEFAULT_DAMPING, 100, 10, 100, "%.2f",
				"How much of the sway is left after 50 ms. Lower returns to centre faster.", value -> config.damping = value));
		sway.addEntry(slider(entries, "Turn sensitivity", config.sensitivity, MotionHudConfig.DEFAULT_SENSITIVITY, 100, 1, 100, "%.2f px/degree",
				"How far the HUD moves when the camera turns.", value -> config.sensitivity = value));
		sway.addEntry(slider(entries, "Motion sensitivity", config.motionSensitivity, MotionHudConfig.DEFAULT_MOTION_SENSITIVITY, 1, 0, 100, "%.0f px/block",
				"How far the HUD moves when the camera rises or drops, such as jumping and falling. 0 turns it off.",
				value -> config.motionSensitivity = value));

		ConfigCategory curve = builder.getOrCreateCategory(Component.literal("Curve"));
		curve.addEntry(entries.startBooleanToggle(Component.literal("Curved HUD"), config.curveEnabled)
				.setDefaultValue(true)
				.setTooltip(Component.literal("Bends the HUD like a curved visor. Flattens while a screen is open."))
				.setSaveConsumer(value -> config.curveEnabled = value)
				.build());
		curve.addEntry(slider(entries, "Curve strength", config.curveStrength, MotionHudConfig.DEFAULT_CURVE_STRENGTH, 1, 0, 100, "%.0f%%",
				"How strongly the HUD bends towards the screen centre.", value -> config.curveStrength = value));
		curve.addEntry(entries.startSelector(Component.literal("Curve shape"),
						new String[] {MotionHudConfig.SHAPE_SPHERE, MotionHudConfig.SHAPE_CYLINDER}, config.curveShape)
				.setDefaultValue(MotionHudConfig.SHAPE_SPHERE)
				.setNameProvider(shape -> Component.literal(MotionHudConfig.SHAPE_CYLINDER.equals(shape) ? "Cylinder" : "Sphere"))
				.setTooltip(Component.literal("Sphere bends towards the centre in every direction. Cylinder bends only sideways, like a curved monitor."))
				.setSaveConsumer(value -> config.curveShape = value)
				.build());
		curve.addEntry(entries.startBooleanToggle(Component.literal("Change curve while sprinting"), config.sprintCurveEnabled)
				.setDefaultValue(true)
				.setTooltip(Component.literal("Eases to the sprinting strength while you sprint and back when you stop."))
				.setSaveConsumer(value -> config.sprintCurveEnabled = value)
				.build());
		curve.addEntry(slider(entries, "Curve strength while sprinting", config.sprintCurveStrength, MotionHudConfig.DEFAULT_SPRINT_CURVE_STRENGTH, 1, 0, 100, "%.0f%%",
				"How strongly the HUD bends while sprinting. Can be lower than the normal strength.", value -> config.sprintCurveStrength = value));
		curve.addEntry(slider(entries, "Sprint transition time", config.sprintCurveTransition, MotionHudConfig.DEFAULT_SPRINT_CURVE_TRANSITION, 10, 0, 100, "%.1f s",
				"How long the curve takes to change when you start or stop sprinting. 0 is instant.", value -> config.sprintCurveTransition = value));
		curve.addEntry(slider(entries, "Sprint zoom-out", config.sprintZoom, MotionHudConfig.DEFAULT_SPRINT_ZOOM, 2, 0, 40, "%.1f%%",
				"How much the whole HUD shrinks towards the screen centre while sprinting. 0 turns it off. "
						+ "Works with the curve on or off and uses the sprint transition time.",
				value -> config.sprintZoom = value));

		ConfigCategory bob =builder.getOrCreateCategory(Component.literal("Bob"));
		bob.addEntry(entries.startBooleanToggle(Component.literal("HUD bobbing"), config.bobEnabled)
				.setDefaultValue(true)
				.setTooltip(Component.literal("The HUD bobs in step with your walk. Works whether or not the game's View Bobbing is on."))
				.setSaveConsumer(value -> config.bobEnabled = value)
				.build());
		bob.addEntry(slider(entries, "Bob strength (walking)", config.bobStrength, MotionHudConfig.DEFAULT_BOB_STRENGTH, 2, 0, 40, "%.1f px",
				"Height of the bob while walking, in GUI pixels. It moves half as far sideways.", value -> config.bobStrength = value));
		bob.addEntry(slider(entries, "Bob strength (sprinting)", config.sprintBobStrength, MotionHudConfig.DEFAULT_SPRINT_BOB_STRENGTH, 2, 0, 40, "%.1f px",
				"Height of the bob while sprinting, in GUI pixels.", value -> config.sprintBobStrength = value));

		ConfigCategory elements = builder.getOrCreateCategory(Component.literal("HUD elements"));
		elements.addEntry(entries.startTextDescription(Component.literal(
				"Offsets are measured from each element's normal position. Pixels and percent of the screen add together. "
						+ "Positive X is right, positive Y is down.")).build());
		Map<Identifier, float[]> edited = new LinkedHashMap<>();

		for (Identifier id : HudElements.movable()) {
			MotionHudConfig.ElementOffset offset = config.offsetFor(id);
			float[] values = offset == null ? new float[4] : new float[] {offset.x, offset.y, offset.xPercent, offset.yPercent};
			edited.put(id, values);

			SubCategoryBuilder group = entries.startSubCategory(Component.literal(id.toString())).setExpanded(offset != null);
			group.add(offsetField(entries, "X (pixels)", values, 0, 4000));
			group.add(offsetField(entries, "Y (pixels)", values, 1, 4000));
			group.add(offsetField(entries, "X (% of screen width)", values, 2, 100));
			group.add(offsetField(entries, "Y (% of screen height)", values, 3, 100));
			elements.addEntry(group.build());
		}

		// Runs after every entry's save consumer.
		builder.setSavingRunnable(() -> {
			edited.forEach((id, values) -> {
				if (values[0] == 0 && values[1] == 0 && values[2] == 0 && values[3] == 0) {
					config.removeOffset(id);
				} else {
					MotionHudConfig.ElementOffset offset = config.getOrCreateOffset(id);
					offset.x = values[0];
					offset.y = values[1];
					offset.xPercent = values[2];
					offset.yPercent = values[3];
				}
			});
			config.offsetsChanged();
			config.save();
		});

		return builder.build();
	}

	/**
	 * Cloth Config only has whole-number sliders, so the value is stored as {@code value * scale}.
	 * The setting is only written back if the slider was moved, which keeps a finer value set by
	 * command or in the file from being rounded.
	 */
	private static AbstractConfigListEntry<?> slider(ConfigEntryBuilder entries, String name, float current, float defaultValue,
			int scale, int min, int max, String format, String tooltip, Consumer<Float> setter) {
		int initial = Math.clamp(Math.round(current * scale), min, max);

		return entries.startIntSlider(Component.literal(name), initial, min, max)
				.setDefaultValue(Math.round(defaultValue * scale))
				.setTextGetter(value -> Component.literal(String.format(Locale.ROOT, format, value / (float) scale)))
				.setTooltip(Component.literal(tooltip))
				.setSaveConsumer(value -> {
					if (value != initial) {
						setter.accept(value / (float) scale);
					}
				})
				.build();
	}

	private static AbstractConfigListEntry<?> offsetField(ConfigEntryBuilder entries, String name, float[] values, int index, float limit) {
		return entries.startFloatField(Component.literal(name), values[index])
				.setDefaultValue(0f)
				.setMin(-limit)
				.setMax(limit)
				.setSaveConsumer(value -> values[index] = value)
				.build();
	}
}
