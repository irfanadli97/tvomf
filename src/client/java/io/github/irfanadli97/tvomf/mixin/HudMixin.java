package io.github.irfanadli97.tvomf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import io.github.irfanadli97.tvomf.HookStatus;
import io.github.irfanadli97.tvomf.HudCurve;
import io.github.irfanadli97.tvomf.HudSway;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Gui.class)
public abstract class HudMixin {
	@Unique
	private GuiGraphics tvomf$graphics;

	// Wrapping the whole method, rather than injecting at HEAD and TAIL like the original,
	// keeps the push and pop paired even if something inside returns early or throws.
	@WrapMethod(method = "render")
	private void tvomf$swayHud(GuiGraphics graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		HookStatus.swayHudRan = true;
		HudSway.update(deltaTracker);
		tvomf$graphics = graphics;
		HudSway.applyTo(graphics, () -> original.call(graphics, deltaTracker));
	}

	// The debug screen is drawn by the game outside the method above, so it never sways, but it
	// shares the GUI mesh and would bend. Dense small text is easier to read flat.
	@WrapMethod(method = "renderDebugOverlay")
	private void tvomf$keepDebugFlat(GuiGraphics graphics, Operation<Void> original) {
		HudCurve.beginFlat();

		try {
			original.call(graphics);
		} finally {
			HudCurve.endFlat();
		}
	}

	// During normal play the subtitles are not drawn inside extractRenderState: the Hud stores a
	// task and Gui runs it later through this method. Fabric API draws every element registered
	// after the subtitles (which is where addLast puts modded elements) in that same task.
	@WrapMethod(method = "renderDeferredSubtitles")
	private void tvomf$swayDeferredSubtitles(Operation<Void> original) {
		HookStatus.swayDeferredSubtitlesRan = true;
		GuiGraphics graphics = tvomf$graphics;
		tvomf$graphics = null;

		if (graphics == null) {
			original.call();
		} else {
			HudSway.applyTo(graphics, original::call);
		}
	}
}
