# Porting TVOMF to a new Minecraft version

Everything here was learned the slow way on Minecraft 26.2. Read it before touching a new version;
it should turn a port into a checklist.

## The short version

1. Make a branch for the new version (`mc/26.3`); leave the old one alone.
2. In `gradle.properties` bump `minecraft_version`, `loader_version`, `loom_version`,
   `fabric_api_version`, and the optional `modmenu_version` and `cloth_config_version`. In
   `src/main/resources/fabric.mod.json` bump the `minecraft` and `fabricloader` ranges. Bump
   `version`.
3. `gradlew build`. Fix what does not compile (see "Game code the mod touches").
4. `run-visual-test.ps1`. Then read the log for lines from `(tvomf)`: a hook that no longer takes
   is reported there as `Could not hook ...`, and once more in chat after five seconds in a world.
5. Look at the screenshots in `build/run/clientGameTest/screenshots` and at `sway_trace.csv`
   (see "Checking a port").
6. Repeat with other mods: `rigs.ps1 -GameVersion <version> -Download` fetches small test sets into
   `rigs/`, and `run-rig-tests.ps1` runs the visual test once per set.
7. `run-config-test.ps1` for the settings file, then [BETA-CHECKLIST.md](BETA-CHECKLIST.md) for what
   still needs a person.

`gradlew clean` deletes `build/`, and with it the test client's `mods` folder, worlds and
settings. That is why the test sets are kept in `rigs/`, outside `build/`; refill the test
client from there with `rigs.ps1 -Use`.

## How the mod is put together

All game-facing code is in `src/client`. Nothing is replaced or redirected; every hook adds code
around the game's own, which is why it gets along with other mods.

| Class | Job |
|---|---|
| `HudSway` | The sway and bob offset for this frame. Pure maths plus reading the camera and player. |
| `HudCurve` | The bend and sprint zoom, applied to GUI vertices and clipping rectangles. |
| `PipShift` | Carries the sway over to 3D models drawn on the HUD. |
| `HudElements` | Wraps every HUD element registered with Fabric API, for per-element offsets. |
| `HookStatus` | Mixin plugin that reports hooks which did not take. |
| `mixin/*` | The hooks themselves; thin, they only call into the classes above. |
| `MotionHudConfig`, `MotionHudCommands`, `compat/*` | Settings file, `/tvomf` command, Cloth Config screen. |

## Game code the mod touches

Names are Mojang's own (26.x is not obfuscated). If one of these moved, this is where to look.

### Hooks (mixins)

| Mixin | Target | Kind | Why |
|---|---|---|---|
| `HudMixin` | `Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` | `@WrapMethod` | Updates the sway once per frame and translates the pose around the whole HUD. |
| `HudMixin` | `Hud.extractDeferredSubtitles()` | `@WrapMethod` | Fabric API draws the subtitles layer, and every element mods add after it (Jade, JourneyMap), from here, outside the method above. Without this they do not sway. |
| `GuiRendererMixin` | `GuiRenderer.render` | `@Inject` HEAD | Computes this frame's bend before the GUI mesh is built. |
| `GuiRendererMixin` | `GuiRenderer.addElementToMesh`, the call to `GuiElementRenderState.buildVertices(VertexConsumer)` | `@WrapOperation` | Hands each element a vertex consumer that bends what is written to it. |
| `GuiRendererMixin` | `GuiRenderer.enableScissor(ScreenRectangle, RenderPass)` | `@ModifyVariable` | Bends clipping rectangles to match, and cuts them back to the screen. |
| `GuiRenderStateMixin` | `GuiRenderState.addPicturesInPictureState` | `@Inject` HEAD | Notes the sway in force when a 3D model is recorded. |
| `GuiRenderStateMixin` | `GuiRenderState.addBlitToCurrentLayer(BlitRenderState)` | `@ModifyVariable` | Shifts the quad a model's texture is drawn with. |
| `PictureInPictureRendererMixin` | `PictureInPictureRenderer.prepare` | `@Inject` HEAD and RETURN | Marks which model the next such quad belongs to. |
| `GuiRenderStateMixin` | `GuiRenderState.addGuiElement` and `addText` | `@Inject` HEAD | Notes elements and text recorded inside a "keep flat" section. |
| `HudMixin` | `Hud.extractDebugOverlay(GuiGraphicsExtractor)` | `@WrapMethod` | Makes the F3 debug screen one such section. The game draws it outside `extractRenderState`, so it never sways, but it shares the GUI mesh and would bend. |

Every hook is optional (`"required": false`, `defaultRequire: 0`). A hook that fails switches its
feature off; it does not stop the game.

### Other game and library API

- `Camera.yRot()`, `xRot()`, `position()` via `GameRenderer.mainCamera()`; `DeltaTracker`.
- `LocalPlayer.isSprinting()`, `AbstractClientPlayer.avatarState()` and its
  `getBackwardsInterpolatedWalkDistance` and `getInterpolatedBob` (the game's own view-bob cycle).
- `VertexConsumer`, `RenderPipeline.getPrimitiveTopology()`, `PrimitiveTopology.QUADS`.
- `BlitRenderState` (a record; `PipShift` rebuilds one with a shifted pose),
  `PictureInPictureRenderState.pose()` and `IDENTITY_POSE`, `ScreenRectangle`.
- Fabric API: `HudElementRegistry.replaceElement`, `VanillaHudElements`, client commands,
  lifecycle events. **Internal, may change without notice:**
  `HudElementRegistryImpl.ROOT_ELEMENTS` and `HudLayer`, used to list elements other mods
  registered. If they change, `HudElements` falls back to the vanilla elements only.
- Optional at runtime: Mod Menu API, Cloth Config.
- Test code only: Fabric's client game-test API, `Screenshot.takeScreenshot`,
  `GameRenderer.extract` and `render`, `ServerLevel.findClosestBiome3d`.

## Things that were not obvious

- **Two kinds of GUI element.** Text, sprites and items carry the pose they were recorded with, so
  translating the pose moves them. 3D models ("picture in picture": entities, player models) are
  recorded with a fixed screen rectangle and ignore the pose; they are rendered to a texture and
  placed with an ordinary textured quad later. That is why `PipShift` exists.
- **The bend happens on vertices, not on a texture.** `HudCurve.Curved` collects each quad's four
  corners and re-emits it, split into a grid when larger than 16 GUI pixels. It must pass on
  *every* vertex value an element writes (`setUv1`, `setUv2`, `setNormal`, `setLineWidth`, not
  only position, colour and UV) and blend them across the split. Dropping them crashes the game
  with `Missing elements in vertex`; copying instead of blending makes JourneyMap's mob markers
  flicker.
- **Screen-sized quads from other mods.** JourneyMap draws its minimap into a screen-sized texture
  and pastes it as one quad. Splitting large quads is what makes it bend with its frame.
- **Clipping rectangles.** The game throws if a scissor rectangle reaches past the top or left of
  the window or is empty. It never makes one itself, but a swayed or shifted one can end up there,
  so `HudCurve.bendScissor` always cuts them back to the screen.
- **Frame timing.** The sway must use the game's own frame delta (`DeltaTracker`), not a separate
  clock, or it stutters on fast camera moves. The integration in `HudSway` is exact for any frame
  rate; do not replace it with "add then decay".
- **Screens.** Screens share the GUI mesh with the HUD, so the bend fades out while any screen is
  open. Models recorded by screens are recorded with no sway in force and are left alone.
- **Things that must stay flat are marked, not measured.** A full-screen overlay is several
  pieces (the spyglass is a scope texture and four black bars). Deciding piece by piece from its
  size whether to bend it left seams onto the world while the scope was still growing. The whole
  overlay, and the F3 screen, are instead recorded inside a "keep flat" section
  (`HudCurve.beginFlat`); elements are noted by identity, and text by the pose object its glyphs
  share, since text is only split into glyphs later.
- **Checking hooks.** Plain `@Inject` and `@ModifyVariable` hooks can be confirmed by looking for
  the handler call in the patched class (`HookStatus.postApply`). MixinExtras hooks
  (`@WrapMethod`, `@WrapOperation`) are woven in after that point, so they set a flag the first
  time they run instead.

## Checking a port

`run-visual-test.ps1` starts a client, makes a flat world and writes screenshots with a fixed sway
and with the curve on and off. Look for:

- `2_flat_swayed`: every HUD element, including other mods', has moved by the same amount.
- `3_curved_rest`, `6b_busy_cylinder_rest`: wide elements follow the curve; nothing is torn.
- `4b_sneak_flat_swayed` against `4a_sneak_flat_rest`: a mod's player model (BedrockIfy's paper
  doll, if installed) moved with the text beside it.
- `4d` and `4e`, sway pushed off screen: the client is still running. This is the scissor crash.
- `9_mobs_curved_fine_split` against `7_mobs_flat`: minimap markers and text look the same when
  quads are split very finely.
- `extra_data_users.txt`: which element types write the extra vertex values.
- `sway_trace.csv`: the offset during a long fall changes smoothly, with no sawtooth.

`make-media.ps1` reshoots the stills and clips for the README once the port is right.

## Port log

The code on `main` is the 26.2 build. Each other game version has its own branch, `mc/<version>`,
and a fix is carried between them one branch at a time.

### 26.2 to 26.3 (branch `mc/26.3`)

Every hook still took unchanged. Two compile fixes and one test fix:

- `PrimitiveTopology` moved from `com.mojang.blaze3d` to `com.mojang.renderpearl.api.pipeline`
  (26.3 moved much of the low-level rendering API into a `renderpearl` package).
- `VertexConsumer` gained `setUv3(float, float)`. `HudCurve.Curved` stores and blends it like the
  other values.
- Test only: `GameRenderer.render` lost its parameters, so the frame-capture hook's handler had
  to drop them too. A handler whose parameters do not match the target fails the mixin outright;
  in the mod itself that would switch the feature off, in the test mod it stops the client.

Checked with the visual test, mod alone. Not yet checked with other mods on 26.3.

### 26.2 back to 1.21.11 (branch `mc/1.21.11`)

1.21.11 already draws the GUI the way 26.2 does (recorded elements, a GUI mesh, picture-in-picture
models), so every feature carried over. The work was build setup and names:

- **Build:** 1.21.x is obfuscated. The plugin is `fabric-loom` (the remapping one) instead of
  `net.fabricmc.fabric-loom`, with `mappings loom.officialMojangMappings()`, and mod
  dependencies become `modImplementation` and `modCompileOnly`. Java 21 instead of 25, in
  `build.gradle`, `fabric.mod.json` and both mixin configs.
- **Renames (26.2 name, then 1.21.11 name):**
  - `Hud` is `Gui`; `extractRenderState` is `render`; `extractDeferredSubtitles` is
    `renderDeferredSubtitles`; `GuiGraphicsExtractor` is `GuiGraphics`.
  - Package `net.minecraft.client.renderer.state.gui` is `net.minecraft.client.gui.render.state`.
  - `GuiRenderState.addPicturesInPictureState` and `addBlitToCurrentLayer` are
    `submitPicturesInPictureState` and `submitBlitToCurrentLayer`.
  - `PictureInPictureRenderer.prepare` has no `FeatureRenderDispatcher` parameter.
  - `RenderPipeline.getPrimitiveTopology()` and `PrimitiveTopology.QUADS` are
    `getVertexFormatMode()` and `VertexFormat.Mode.QUADS`.
  - `GameRenderer.mainCamera()` is `getMainCamera()`; `Minecraft.gui.screen()` is the field
    `Minecraft.screen`; `LocalPlayer.sendSystemMessage` is `displayClientMessage(text, false)`.
  - Fabric API: `HudElement.extractRenderState` is `render`; `ClientCommands` is
    `ClientCommandManager`; `VanillaHudElements.MOB_EFFECTS` is `STATUS_EFFECTS`.
  - Test only: there is no `GameRenderer.extract`; both capture hooks go on `render`.
- **Trap:** a class name inside an `@At(target = "Lnet/minecraft/...;")` string uses slashes, so
  a find-and-replace on the dotted package name misses it. The hook then silently did not take,
  and the hook report caught it ("Not available on this game version: the curved HUD").
- **Trap:** after switching branch or moving the project folder, delete
  `.gradle/configuration-cache` if a build finishes suspiciously fast and produces no new jar.

Checked with the visual test, mod alone, run on the build machine's Java 25. Not yet checked on
a Java 21 runtime or with other mods on 1.21.11.

## Releasing

1. Merge or tag on the version's branch; CI (`.github/workflows/build.yml`) builds the jar on
   every push and keeps it as an artifact.
2. Create a GitHub release tagged `v<mod version>+<minecraft version>` and attach the jar.
3. Keep the credit to GD656MotionHUD and the AI disclosure in the README.
