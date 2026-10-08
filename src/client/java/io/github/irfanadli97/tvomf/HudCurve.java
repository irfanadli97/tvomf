package io.github.irfanadli97.tvomf;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Bends the HUD as if it were drawn on the inside of a curved visor (concave, as in Warframe):
 * the screen corners stay where they are and everything nearer the centre is drawn inwards, so
 * the screen edges sag towards the middle and off-centre elements tilt. Not in the original mod.
 *
 * <p>The bend is applied to the vertices of the GUI mesh, so text and icons are not resampled
 * through an off-screen texture. Quads larger than 16 GUI pixels are split into a grid first,
 * so a wide element follows the curve along its length, and a screen-sized layer that another
 * mod pastes onto the HUD (JourneyMap's minimap does this) bends the same way as the elements
 * drawn over it.
 *
 * <p>Screens share the GUI mesh with the HUD and their widgets must stay where the mouse expects
 * them, so the bend fades out whenever a screen is open.
 */
public final class HudCurve {
	/** How much further out the corners sit than the centre at 100% strength, as a fraction. */
	private static final float MAX_PULL = 0.2f;
	private static final float FADE_MS = 150.0f;
	/** Largest sprint zoom-out, in percent. */
	public static final float MAX_ZOOM = 20.0f;
	/** Elements this small that sit on the screen centre (the crosshair) are left unscaled. */
	private static final int CENTER_ELEMENT_SIZE = 32;
	/** Largest quad edge, in GUI pixels, that is bent without being split. */
	private static float cellSize = 16.0f;
	private static final int MAX_CELLS = 64;

	private static final Curved CURVED = new Curved();

	// Things that must stay flat whatever their size: the debug screen and the full-screen
	// overlays. They are noted while the frame is being recorded and looked up when its mesh is
	// built, so there is one set being filled and one being read.
	private static Set<Object> flatRecording = Collections.newSetFromMap(new IdentityHashMap<>());
	private static Set<Object> flat = Collections.newSetFromMap(new IdentityHashMap<>());
	private static int flatDepth;
	private static @Nullable Set<String> extraDataUsers;
	private static float fade;
	/** The strength in use this frame, 0-100, easing towards the normal or the sprinting setting. */
	private static float strength;
	private static long lastFrameNanos;

	/** How much smaller the whole HUD is drawn this frame, in percent, easing in while sprinting. */
	private static float zoom;

	private static boolean curveWasEnabled;

	private static float pull;
	/** The whole HUD is scaled by this about the screen centre, on top of the bend. */
	private static float zoomScale = 1.0f;
	private static boolean cylinder;
	private static float centerX;
	private static float centerY;

	private HudCurve() {
	}

	/** Called once per frame before the GUI mesh is built. */
	public static void beginFrame() {
		Set<Object> read = flat;
		flat = flatRecording;
		flatRecording = read;
		flatRecording.clear();

		Minecraft minecraft = Minecraft.getInstance();
		MotionHudConfig config = MotionHudConfig.get();
		boolean wanted = minecraft.player != null && minecraft.gui.screen() == null;

		long now = System.nanoTime();
		float elapsedMs = lastFrameNanos == 0 ? FADE_MS : (now - lastFrameNanos) / 1.0e6f;
		lastFrameNanos = now;
		float step = Math.max(0, elapsedMs) / FADE_MS;
		fade = Mth.clamp(fade + (wanted ? step : -step), 0.0f, 1.0f);

		Window window = minecraft.getWindow();
		centerX = window.getGuiScaledWidth() / 2.0f;
		centerY = window.getGuiScaledHeight() / 2.0f;

		boolean sprinting = minecraft.player != null && minecraft.player.isSprinting();
		float target = !config.curveEnabled ? 0
				: Mth.clamp(sprinting && config.sprintCurveEnabled ? config.sprintCurveStrength : config.curveStrength, 0.0f, 100.0f);
		float zoomTarget = sprinting ? Mth.clamp(config.sprintZoom, 0.0f, MAX_ZOOM) : 0;

		// Nothing is bent while fully faded out, so there is no change of strength to ease through.
		// The change is exponential, which is all but finished after three time constants.
		float easeMs = config.sprintCurveTransition * 1000.0f / 3.0f;

		if (fade <= step || easeMs <= 0) {
			strength = target;
			zoom = zoomTarget;
		} else {
			float eased = 1.0f - (float) Math.exp(-Math.max(0, elapsedMs) / easeMs);
			strength += (target - strength) * eased;
			zoom += (zoomTarget - zoom) * eased;
		}

		// Switching the curve itself on or off is not a sprint change, so it does not ease.
		if (config.curveEnabled != curveWasEnabled) {
			strength = target;
			curveWasEnabled = config.curveEnabled;
		}

		if (zoomTarget == 0 && zoom < 0.01f) {
			zoom = 0;
		}

		cylinder = MotionHudConfig.SHAPE_CYLINDER.equals(config.curveShape);
		// Smoothstep so the fade eases at both ends.
		float smoothFade = fade * fade * (3 - 2 * fade);
		pull = smoothFade * strength / 100.0f * MAX_PULL;
		zoomScale = 1.0f - smoothFade * zoom / 100.0f;
	}

	public static boolean active() {
		return (pull > 0 || zoomScale < 1.0f) && centerX > 0 && centerY > 0;
	}

	/**
	 * Whether an element should be bent. Anything that declares bounds spanning the full width or
	 * height of the screen (vignette, spyglass, portal and similar overlays) is left flat, since
	 * pulling it inwards would uncover the screen edge. So is a small element on the exact centre,
	 * which would only shrink by a fraction of a pixel and turn blurry.
	 */
	public static boolean appliesTo(GuiElementRenderState element) {
		if (!active() || flat.contains(element)) {
			return false;
		}

		// Text is recorded as a whole and only split into glyphs later; the glyphs share the
		// text's pose object, which is what was noted for it.
		if (element instanceof GlyphRenderState glyph && flat.contains(glyph.pose())) {
			return false;
		}

		ScreenRectangle bounds = element.bounds();

		if (bounds == null) {
			return true;
		}

		if (bounds.width() >= centerX * 2 - 1 || bounds.height() >= centerY * 2 - 1) {
			return false;
		}

		boolean onCenter = bounds.left() <= centerX && bounds.right() >= centerX && bounds.top() <= centerY && bounds.bottom() >= centerY;
		return !(onCenter && bounds.width() <= CENTER_ELEMENT_SIZE && bounds.height() <= CENTER_ELEMENT_SIZE);
	}

	/** Everything recorded until the matching {@link #endFlat()} is left unbent and unscaled. */
	public static void beginFlat() {
		flatDepth++;
	}

	public static void endFlat() {
		flatDepth--;
	}

	/** Notes {@code key} (an element, or a text's pose) if it is being recorded inside a flat section. */
	public static void recordFlat(Object key) {
		if (flatDepth > 0) {
			// Only reachable if the per-frame swap in beginFrame has stopped running.
			if (flatRecording.size() > 8192) {
				flatRecording.clear();
			}

			flatRecording.add(key);
		}
	}

	/** Builds the element's vertices into {@code consumer}, bent. */
	public static void build(GuiElementRenderState element, VertexConsumer consumer, Consumer<VertexConsumer> buildVertices) {
		CURVED.begin(consumer, element.pipeline().getPrimitiveTopology() == PrimitiveTopology.QUADS);

		try {
			buildVertices.accept(CURVED);

			if (extraDataUsers != null && CURVED.usesExtraData()) {
				extraDataUsers.add(element.getClass().getName() + " / " + element.pipeline().getLocation());
			}
		} finally {
			CURVED.end();
		}
	}

	/**
	 * Moves a clipping rectangle to where the content it clips ends up once bent. Clipping can
	 * only be an upright rectangle, so this is the box around its four bent corners.
	 */
	public static ScreenRectangle bendScissor(ScreenRectangle area) {
		return keepOnScreen(active() ? bend(area) : area);
	}

	/**
	 * The game rejects a clipping rectangle that reaches past the top or left of the window, or
	 * is empty, and crashes. It never produces one itself, but a rectangle that has been swayed
	 * can end up there, so it is cut down to the part on screen, and to a single pixel at the
	 * nearest edge when none of it is.
	 */
	private static ScreenRectangle keepOnScreen(ScreenRectangle area) {
		Window window = Minecraft.getInstance().getWindow();
		int width = Math.max(1, window.getGuiScaledWidth());
		int height = Math.max(1, window.getGuiScaledHeight());

		if (area.left() >= 0 && area.top() >= 0 && area.right() <= width && area.bottom() <= height && area.width() > 0 && area.height() > 0) {
			return area;
		}

		int left = Mth.clamp(area.left(), 0, width - 1);
		int top = Mth.clamp(area.top(), 0, height - 1);
		int right = Mth.clamp(area.right(), left + 1, width);
		int bottom = Mth.clamp(area.bottom(), top + 1, height);
		return new ScreenRectangle(left, top, right - left, bottom - top);
	}

	private static ScreenRectangle bend(ScreenRectangle area) {
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		for (int corner = 0; corner < 4; corner++) {
			float x = (corner & 1) == 0 ? area.left() : area.right();
			float y = (corner & 2) == 0 ? area.top() : area.bottom();
			float bentX = bendX(x, y);
			float bentY = bendY(x, y);
			minX = Math.min(minX, bentX);
			minY = Math.min(minY, bentY);
			maxX = Math.max(maxX, bentX);
			maxY = Math.max(maxY, bentY);
		}

		int left = Mth.floor(minX);
		int top = Mth.floor(minY);
		return new ScreenRectangle(left, top, Mth.ceil(maxX) - left, Mth.ceil(maxY) - top);
	}

	/** For the visual tests: splits quads more finely than normal, so small elements are split too. */
	public static void setTestCellSize(float size) {
		cellSize = size;
	}

	/**
	 * For the visual tests: starts recording which element types write more than position,
	 * colour and texture coordinates, and returns the set they are recorded into.
	 */
	public static Set<String> recordExtraDataUsers() {
		extraDataUsers = new TreeSet<>();
		return extraDataUsers;
	}

	private static float bendX(float x, float y) {
		return centerX + (x - centerX) * scaleAt(x, y);
	}

	private static float bendY(float x, float y) {
		return centerY + (y - centerY) * scaleAt(x, y);
	}

	private static float scaleAt(float x, float y) {
		float nx = (x - centerX) / centerX;
		float ny = (y - centerY) / centerY;
		// Bowl: 0 at the centre, 1 at the corners. Cylinder (upright, like a curved monitor): 0
		// along the vertical centre line, 1 at the left and right edges, whatever the height.
		float distanceSq = cylinder ? nx * nx : (nx * nx + ny * ny) / 2.0f;
		// Grows with distance, so the far edge stays put and everything nearer the centre is
		// drawn in. In a bowl all four screen edges sag towards the middle; in a cylinder only
		// the top and bottom do, and upright lines stay straight.
		return (1.0f + pull * distanceSq) / (1.0f + pull) * zoomScale;
	}

	/**
	 * Collects the four corners of each quad the element writes, then emits the quad bent, split
	 * into a grid when it is large. Pipelines that do not draw quads are bent vertex by vertex.
	 */
	private static final class Curved implements VertexConsumer {
		private VertexConsumer delegate;
		private boolean quads;
		private int count;

		// Every value a vertex can carry, one row per value and one column per corner. Colour is
		// kept as four channels so all of them can be blended the same way.
		private static final int X = 0;
		private static final int Y = 1;
		private static final int Z = 2;
		private static final int U = 3;
		private static final int V = 4;
		private static final int ALPHA = 5;
		private static final int RED = 6;
		private static final int GREEN = 7;
		private static final int BLUE = 8;
		private static final int UV1_U = 9;
		private static final int UV1_V = 10;
		private static final int UV2_U = 11;
		private static final int UV2_V = 12;
		private static final int NORMAL_X = 13;
		private static final int NORMAL_Y = 14;
		private static final int NORMAL_Z = 15;
		private static final int LINE_WIDTH = 16;
		private static final int VALUES = 17;

		private final float[][] corners = new float[VALUES][4];
		private final float[] x = corners[X];
		private final float[] y = corners[Y];
		private final float[] blended = new float[VALUES];
		private boolean hasUv;
		private boolean hasColor;
		private boolean hasUv1;
		private boolean hasUv2;
		private boolean hasNormal;
		private boolean hasLineWidth;

		void begin(VertexConsumer delegate, boolean quads) {
			this.delegate = delegate;
			this.quads = quads;
			count = 0;
			hasUv = false;
			hasColor = false;
			hasUv1 = false;
			hasUv2 = false;
			hasNormal = false;
			hasLineWidth = false;
		}

		boolean usesExtraData() {
			return hasUv1 || hasUv2 || hasNormal || hasLineWidth;
		}

		void end() {
			if (count == 4) {
				emitQuad();
			} else {
				// Not a whole quad; pass what there is through unsplit.
				for (int corner = 0; corner < count; corner++) {
					emitCorner(corner);
				}
			}

			count = 0;
			delegate = null;
		}

		@Override
		public VertexConsumer addVertex(float vx, float vy, float vz) {
			if (!quads) {
				delegate.addVertex(bendX(vx, vy), bendY(vx, vy), vz);
				return delegate;
			}

			if (count == 4) {
				emitQuad();
				count = 0;
			}

			corners[X][count] = vx;
			corners[Y][count] = vy;
			corners[Z][count] = vz;
			count++;
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			corners[ALPHA][count - 1] = alpha;
			corners[RED][count - 1] = red;
			corners[GREEN][count - 1] = green;
			corners[BLUE][count - 1] = blue;
			hasColor = true;
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			return setColor(ARGB.red(argb), ARGB.green(argb), ARGB.blue(argb), ARGB.alpha(argb));
		}

		@Override
		public VertexConsumer setUv(float vu, float vv) {
			corners[U][count - 1] = vu;
			corners[V][count - 1] = vv;
			hasUv = true;
			return this;
		}

		// Vanilla GUI elements only write position, colour and texture coordinates, but other
		// mods' elements use formats with more, and a vertex missing any of them crashes the
		// buffer. They are blended like everything else: JourneyMap, for one, passes a second set
		// of texture coordinates for its mob markers this way.
		@Override
		public VertexConsumer setUv1(int vu, int vv) {
			corners[UV1_U][count - 1] = vu;
			corners[UV1_V][count - 1] = vv;
			hasUv1 = true;
			return this;
		}

		@Override
		public VertexConsumer setUv2(int vu, int vv) {
			corners[UV2_U][count - 1] = vu;
			corners[UV2_V][count - 1] = vv;
			hasUv2 = true;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float nx, float ny, float nz) {
			corners[NORMAL_X][count - 1] = nx;
			corners[NORMAL_Y][count - 1] = ny;
			corners[NORMAL_Z][count - 1] = nz;
			hasNormal = true;
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			corners[LINE_WIDTH][count - 1] = width;
			hasLineWidth = true;
			return this;
		}

		private void emitQuad() {
			// Corners run around the quad, so 0-3 and 1-2 are one pair of opposite edges and
			// 0-1 and 3-2 the other.
			int columns = cells(Math.max(length(0, 3), length(1, 2)));
			int rows = cells(Math.max(length(0, 1), length(3, 2)));

			if (columns == 1 && rows == 1) {
				for (int corner = 0; corner < 4; corner++) {
					emitCorner(corner);
				}

				return;
			}

			for (int row = 0; row < rows; row++) {
				float t0 = (float) row / rows;
				float t1 = (float) (row + 1) / rows;

				for (int column = 0; column < columns; column++) {
					float s0 = (float) column / columns;
					float s1 = (float) (column + 1) / columns;
					emitAt(s0, t0);
					emitAt(s0, t1);
					emitAt(s1, t1);
					emitAt(s1, t0);
				}
			}
		}

		private void emitCorner(int corner) {
			for (int value = 0; value < VALUES; value++) {
				blended[value] = corners[value][corner];
			}

			emitBlended();
		}

		/** Emits the point {@code s} of the way from edge 0-1 to edge 3-2 and {@code t} of the way along them. */
		private void emitAt(float s, float t) {
			float w0 = (1 - s) * (1 - t);
			float w1 = (1 - s) * t;
			float w2 = s * t;
			float w3 = s * (1 - t);

			for (int value = 0; value < VALUES; value++) {
				float[] row = corners[value];
				blended[value] = row[0] * w0 + row[1] * w1 + row[2] * w2 + row[3] * w3;
			}

			emitBlended();
		}

		private void emitBlended() {
			// Always through the delegate itself, never a consumer it returned, so every value
			// lands on the vertex just started whatever the delegate chooses to return.
			delegate.addVertex(bendX(blended[X], blended[Y]), bendY(blended[X], blended[Y]), blended[Z]);

			if (hasColor) {
				delegate.setColor(Math.round(blended[RED]), Math.round(blended[GREEN]), Math.round(blended[BLUE]), Math.round(blended[ALPHA]));
			}

			if (hasUv) {
				delegate.setUv(blended[U], blended[V]);
			}

			if (hasUv1) {
				delegate.setUv1(Math.round(blended[UV1_U]), Math.round(blended[UV1_V]));
			}

			if (hasUv2) {
				delegate.setUv2(Math.round(blended[UV2_U]), Math.round(blended[UV2_V]));
			}

			if (hasNormal) {
				delegate.setNormal(blended[NORMAL_X], blended[NORMAL_Y], blended[NORMAL_Z]);
			}

			if (hasLineWidth) {
				delegate.setLineWidth(blended[LINE_WIDTH]);
			}
		}

		private float length(int from, int to) {
			float dx = x[to] - x[from];
			float dy = y[to] - y[from];
			return (float) Math.sqrt(dx * dx + dy * dy);
		}

		private static int cells(float length) {
			return Mth.clamp(Mth.ceil(length / cellSize), 1, MAX_CELLS);
		}
	}
}
