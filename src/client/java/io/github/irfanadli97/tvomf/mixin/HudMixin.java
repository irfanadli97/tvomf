package io.github.irfanadli97.tvomf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import io.github.irfanadli97.tvomf.HookStatus;
import io.github.irfanadli97.tvomf.HudSway;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Unique
	private GuiGraphicsExtractor tvomf$graphics;

	// Wrapping the whole method, rather than injecting at HEAD and TAIL like the original,
	// keeps the push and pop paired even if something inside returns early or throws.
	@WrapMethod(method = "extractRenderState")
	private void tvomf$swayHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		HookStatus.swayHudRan = true;
		HudSway.update(deltaTracker);
		tvomf$graphics = graphics;
		HudSway.applyTo(graphics, () -> original.call(graphics, deltaTracker));
	}

	// During normal play the subtitles are not drawn inside extractRenderState: the Hud stores a
	// task and Gui runs it later through this method. Fabric API draws every element registered
	// after the subtitles (which is where addLast puts modded elements) in that same task.
	@WrapMethod(method = "extractDeferredSubtitles")
	private void tvomf$swayDeferredSubtitles(Operation<Void> original) {
		HookStatus.swayDeferredSubtitlesRan = true;
		GuiGraphicsExtractor graphics = tvomf$graphics;
		tvomf$graphics = null;

		if (graphics == null) {
			original.call();
		} else {
			HudSway.applyTo(graphics, original::call);
		}
	}
}
