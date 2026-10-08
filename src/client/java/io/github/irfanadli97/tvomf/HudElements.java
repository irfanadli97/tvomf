package io.github.irfanadli97.tvomf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.impl.client.rendering.hud.HudElementRegistryImpl;
import net.fabricmc.fabric.impl.client.rendering.hud.HudLayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Wraps every HUD element registered with Fabric API, vanilla or modded, so each one can be
 * shifted on its own.
 */
public final class HudElements {
	/** Full-screen overlays. Shifting these would expose the screen edge, so they never move. */
	private static final Set<Identifier> FULL_SCREEN = Set.of(VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.SLEEP);

	private static final List<Identifier> VANILLA = List.of(
			VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR, VanillaHudElements.SPECTATOR_MENU,
			VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
			VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
			VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP,
			VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR,
			VanillaHudElements.SLEEP, VanillaHudElements.DEMO_TIMER, VanillaHudElements.SCOREBOARD,
			VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT,
			VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES);

	private static final Set<Identifier> wrapped = new LinkedHashSet<>();

	private HudElements() {
	}

	/** Ids of the elements that can be moved, in draw order. */
	public static List<Identifier> movable() {
		List<Identifier> ids = new ArrayList<>(wrapped);
		ids.removeAll(FULL_SCREEN);
		return Collections.unmodifiableList(ids);
	}

	public static boolean isMovable(Identifier id) {
		return wrapped.contains(id) && !FULL_SCREEN.contains(id);
	}

	/**
	 * Wraps every element registered so far. Called once the client has started, after all mods
	 * have had the chance to register theirs.
	 */
	public static void wrapAll() {
		for (Identifier id : discover()) {
			if (wrapped.contains(id)) {
				continue;
			}

			try {
				HudElementRegistry.replaceElement(id, original -> new Wrapper(id, original));
				wrapped.add(id);
			} catch (IllegalArgumentException e) {
				MotionHudClient.LOGGER.warn("Could not wrap HUD element {}", id, e);
			}
		}

		MotionHudClient.LOGGER.info("Wrapped {} HUD elements", wrapped.size());
		reportUnknownOffsets();
	}

	/** Does nothing until the elements are known, which is once the game has finished starting. */
	static void reportUnknownOffsets() {
		if (!wrapped.isEmpty()) {
			MotionHudConfig.get().warnAboutUnknownElements(wrapped);
		}
	}

	private static List<Identifier> discover() {
		// Fabric API has no public way to list registered elements, so this reads its internal
		// registry and falls back to the vanilla ids if those internals ever change.
		try {
			List<Identifier> ids = new ArrayList<>();

			for (Identifier root : VANILLA) {
				HudElementRegistryImpl.RootLayer rootLayer = HudElementRegistryImpl.ROOT_ELEMENTS.get(root);

				if (rootLayer == null) {
					continue;
				}

				for (HudLayer layer : rootLayer.layers()) {
					ids.add(layer.id());
				}
			}

			return ids;
		} catch (LinkageError | RuntimeException e) {
			MotionHudClient.LOGGER.warn("Could not list modded HUD elements; only vanilla ones will be movable", e);
			return VANILLA;
		}
	}

	private record Wrapper(Identifier id, HudElement original) implements HudElement {
		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
			if (!FULL_SCREEN.contains(id)) {
				draw(graphics, deltaTracker);
				return;
			}

			// A full-screen overlay is made of several pieces (the spyglass is a scope and four
			// black bars); bending or scaling any of them opens gaps onto the world.
			HudCurve.beginFlat();

			try {
				draw(graphics, deltaTracker);
			} finally {
				HudCurve.endFlat();
			}
		}

		private void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
			MotionHudConfig config = MotionHudConfig.get();
			float x = 0;
			float y = 0;

			if (FULL_SCREEN.contains(id) || (!config.swayCrosshair && id.equals(VanillaHudElements.CROSSHAIR))) {
				// Undo the sway applied to the whole HUD by HudMixin.
				x -= HudSway.x();
				y -= HudSway.y();
			}

			MotionHudConfig.ElementOffset offset = FULL_SCREEN.contains(id) ? null : config.offsetFor(id);

			if (offset != null) {
				x += HudSway.snapToScreenPixel(offset.x + offset.xPercent / 100.0 * graphics.guiWidth());
				y += HudSway.snapToScreenPixel(offset.y + offset.yPercent / 100.0 * graphics.guiHeight());
			}

			if (x == 0 && y == 0) {
				original.extractRenderState(graphics, deltaTracker);
				return;
			}

			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();

			PipShift.add(x, y);

			try {
				pose.translate(x, y);
				original.extractRenderState(graphics, deltaTracker);
			} finally {
				pose.popMatrix();
				PipShift.add(-x, -y);
			}
		}
	}
}
