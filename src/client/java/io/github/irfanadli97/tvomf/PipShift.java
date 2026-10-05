package io.github.irfanadli97.tvomf;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Makes 3D models drawn onto the HUD (entities, player models; "picture in picture") follow the
 * sway and the per-element offsets.
 *
 * <p>Those models are recorded with a fixed screen rectangle and ignore the pose the rest of the
 * HUD is shifted with. Later each one is rendered to a texture and placed on the GUI as a plain
 * textured quad. So the shift in force when a model is recorded is remembered here, and applied
 * to that quad instead. Models recorded outside the HUD, such as the player in the inventory
 * screen, are recorded with no shift in force and are left alone.
 */
public final class PipShift {
	private static final Map<PictureInPictureRenderState, float[]> SHIFTS = new IdentityHashMap<>();
	private static float currentX;
	private static float currentY;
	private static float @Nullable [] pending;

	private PipShift() {
	}

	/** Adds to the shift in force for whatever part of the HUD is being recorded now. */
	public static void add(float x, float y) {
		currentX += x;
		currentY += y;
	}

	/**
	 * Called at the start of each frame's HUD, by which time the models recorded for the previous
	 * frame have been placed. Also zeroes the running shift: adding and then subtracting floats
	 * does not always land back on exactly zero, and the leftovers would build up over hours.
	 */
	public static void startFrame() {
		SHIFTS.clear();
		pending = null;
		currentX = 0;
		currentY = 0;
	}

	public static void record(PictureInPictureRenderState state) {
		// A state with its own pose was positioned with the HUD's pose and has the shift already.
		if ((Math.abs(currentX) > 1.0e-4f || Math.abs(currentY) > 1.0e-4f) && state.pose() == PictureInPictureRenderState.IDENTITY_POSE) {
			SHIFTS.put(state, new float[] {currentX, currentY});
		}
	}

	/** Brackets the step in which {@code state}'s texture is placed on the GUI. */
	public static void beginPlacing(PictureInPictureRenderState state) {
		pending = SHIFTS.get(state);
	}

	public static void endPlacing() {
		pending = null;
	}

	public static BlitRenderState shift(BlitRenderState blit) {
		float[] shift = pending;

		if (shift == null) {
			return blit;
		}

		ScreenRectangle scissor = blit.scissorArea();

		if (scissor != null) {
			scissor = new ScreenRectangle(scissor.left() + Math.round(shift[0]), scissor.top() + Math.round(shift[1]), scissor.width(), scissor.height());
		}

		// Shifted in screen space, so the translation goes in front of the quad's own pose.
		Matrix3x2f pose = new Matrix3x2f().translation(shift[0], shift[1]).mul(blit.pose());
		return new BlitRenderState(blit.pipeline(), blit.textureSetup(), pose, blit.x0(), blit.y0(), blit.x1(), blit.y1(),
				blit.u0(), blit.u1(), blit.v0(), blit.v1(), blit.color(), scissor);
	}
}
