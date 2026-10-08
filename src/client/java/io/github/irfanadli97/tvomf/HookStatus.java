package io.github.irfanadli97.tvomf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Finds out which of the mod's hooks into the game actually took.
 *
 * <p>None of the hooks is mandatory: on a game version where a hooked method has moved or been
 * renamed, that hook is skipped instead of stopping the game from starting. That would otherwise
 * be silent, so after each mixin is applied this looks in the patched game class for the calls to
 * its handlers and records the ones that are missing. Hooks made with MixinExtras are woven in
 * after that point and cannot be checked this way; those run every frame, so they report in by
 * setting a flag instead. The player is told once in chat.
 *
 * <p>This runs while game classes are still being loaded, so it must not touch any of them.
 */
public final class HookStatus implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("tvomf");

	/** Handler methods of each mixin, by the feature a player would know them as. */
	private static final Map<String, Map<String, String>> EXPECTED = new LinkedHashMap<>();
	private static final Set<String> SEEN = new LinkedHashSet<>();
	private static final Set<String> MISSING = new LinkedHashSet<>();

	/** Set by the hooks that cannot be found by looking at the patched class. */
	public static boolean swayHudRan;
	public static boolean swayDeferredSubtitlesRan;
	public static boolean curveRan;

	static {
		expect("GuiRendererMixin", "beginFrame", "the curved HUD and sprint zoom");
		expect("GuiRendererMixin", "curveScissor", "clipping of curved or swayed elements");
		expect("GuiRenderStateMixin", "keepFlat", "the debug screen and full-screen overlays staying flat under the curve");
		expect("GuiRenderStateMixin", "keepTextFlat", "the debug screen and full-screen overlays staying flat under the curve");
		expect("GuiRenderStateMixin", "recordShift", "sway for 3D models on the HUD");
		expect("GuiRenderStateMixin", "shiftModel", "sway for 3D models on the HUD");
		expect("PictureInPictureRendererMixin", "beginPlacing", "sway for 3D models on the HUD");
		expect("PictureInPictureRendererMixin", "endPlacing", "sway for 3D models on the HUD");
	}

	private static void expect(String mixin, String handler, String feature) {
		EXPECTED.computeIfAbsent(mixin, key -> new LinkedHashMap<>()).put(handler, feature);
	}

	/**
	 * The features that are switched off on this game version; empty when everything works. Only
	 * meaningful once the HUD has been drawn for a while, so the every-frame hooks have had their
	 * chance to run.
	 */
	public static List<String> missingFeatures() {
		Set<String> missing = new LinkedHashSet<>();

		if (!swayHudRan) {
			missing.add("the HUD sway, bob and element offsets");
		}

		if (!swayDeferredSubtitlesRan) {
			missing.add("sway for captions and other mods' HUD elements");
		}

		if (!curveRan) {
			missing.add("the curved HUD and sprint zoom");
		}

		missing.addAll(MISSING);

		// A mixin that was never applied at all, for example because its game class is gone.
		EXPECTED.forEach((mixin, handlers) -> {
			if (!SEEN.contains(mixin)) {
				missing.addAll(handlers.values());
			}
		});

		return new ArrayList<>(missing);
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		String mixin = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		Map<String, String> handlers = EXPECTED.get(mixin);

		if (handlers == null) {
			return;
		}

		SEEN.add(mixin);
		Set<String> called = new LinkedHashSet<>();

		for (MethodNode method : targetClass.methods) {
			for (AbstractInsnNode instruction : method.instructions) {
				// Mixin prefixes a merged handler's name but keeps the original name at the end.
				if (instruction instanceof MethodInsnNode call && call.name.contains("tvomf$")) {
					called.add(call.name.substring(call.name.lastIndexOf("tvomf$") + "tvomf$".length()));
				}
			}
		}

		handlers.forEach((handler, feature) -> {
			if (!called.contains(handler) && MISSING.add(feature)) {
				LOGGER.warn("Could not hook {} in {} on this game version; turned off: {}", handler, targetClassName, feature);
			}
		});
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
