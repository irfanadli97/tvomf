# Alpha to beta checklist

What a build has to pass before its label moves from alpha to beta. Written for
`0.1.1-alpha.1+26.3`, but it applies to any new game version.

**Beta means:** no known crash, every feature works with the mod alone and alongside the common
rendering and HUD mods, and what remains are rough edges, not breakage.

Each item is either **auto** (the scripts in this repo can check it) or **play** (someone has to
do it in the game). Record the result and the build's jar checksum next to each section.

## Exit criteria

All of these, on the same jar:

- [ ] Every item in sections 1 to 6 passes, or has a written reason it does not apply.
- [ ] No crash and no `(tvomf)` warning in the log across all of it.
- [ ] At least two hours of ordinary play in one session without a restart.
- [ ] Section 5 done with all of rig A and rig B, and every rig C mod that exists for the version.
- [ ] Any defect found is fixed and its section rerun, or it is listed in the release notes.

A crash, a feature that silently does nothing, or a HUD element that becomes unreadable or
unclickable blocks beta. Cosmetic differences from 26.2 do not, if they are written down.

## 1. Build and hooks (auto)

- [ ] `gradlew build` succeeds from a clean checkout of the branch.
- [ ] The GitHub Actions build for the branch is green.
- [ ] The log has `Wrapped N HUD elements` with N at least 23 (the vanilla count on 26.2).
- [ ] The log has no `Could not hook` line, and no chat message starting `[tvomf] Not available`.
- [ ] With one hook deliberately broken (rename a target method), the game still starts, the
      log names the lost feature, and chat reports it once. Restore the hook afterwards.

## 2. Visual test, mod alone (auto, then look)

Run `run-visual-test.ps1` and check each screenshot against the 26.2 one of the same name.

- [ ] `2_flat_swayed`: every element moved by the same amount; nothing left behind.
- [ ] `3_curved_rest` and `4_curved_swayed`: hotbar and bars follow the curve; no tearing.
- [ ] `6a_busy_sphere_rest` against `6b_busy_cylinder_rest`: the two shapes differ as described.
- [ ] `5_busy_flat` against `6_busy_curved_swayed`: boss bar, scoreboard, titles, action bar,
      effects and toasts all present and legible.
- [ ] `4d` and `4e` (sway pushed off screen): the client is still running afterwards.
- [ ] `9_mobs_curved_fine_split` against `7_mobs_flat`: text is not garbled when finely split.
- [ ] `sway_trace.csv`: the offset changes smoothly, no sawtooth, and returns towards zero.
- [ ] `make-media.ps1 -ModSet solo` finishes and both clips play smoothly at full speed.

## 3. Features by hand (play)

In a survival world, defaults first, then your own settings.

- [ ] Turn slowly, then flick: the HUD lags and eases back; no jitter at either speed.
- [ ] Look straight up and down past the limit: no jump in the HUD.
- [ ] Jump, fall three blocks, fall thirty blocks: dips and lifts, settles, no runaway offset.
- [ ] Walk, stop, walk: bob fades in and out; it stops in the air and while swimming still.
- [ ] Sprint and stop: curve and zoom ease in and out over the set transition time.
- [ ] Transition time at 0 (instant) and at 10 s (very slow) both behave.
- [ ] Each toggle off in turn (sway, bob, curve, sprint curve, crosshair sway): only that stops.
- [ ] Sprint zoom at 0 and at 20.
- [ ] Sphere and cylinder at strength 100: nothing leaves the screen or overlaps the crosshair.
- [ ] Move one element with `/tvomf element move`, by pixels and by percent; reset it.
- [ ] Every `/tvomf config` subcommand runs and `config list` shows the new value.
- [ ] `/tvomf config reload` picks up a hand edit of `config/tvomf.json`.

## 4. Game situations (play)

- [ ] F1 hides the HUD and restores it; F3 debug screen is readable and stays flat enough to read.
- [ ] Inventory, crafting table, chest, anvil: flat, still, and every slot clicks where it looks.
- [ ] Chat open: typing, clicking a link or name, and scrolling all work.
- [ ] Escape menu, options, and the settings screen: flat and clickable; the curve returns after.
- [ ] Death screen and respawn: no HUD jump on respawn.
- [ ] `/tp` a long way, a Nether portal, and the End: no HUD jump on arrival.
- [ ] Sleep in a bed: the dark overlay covers the whole screen and does not sway.
- [ ] Spyglass, carved pumpkin, Nether portal overlay, powder snow frost: full screen, no gaps.
- [ ] Boat, horse (mount health and jump bar), elytra flight, swimming, spectator mode.
- [ ] Boss bar from a Wither or Dragon; tab player list; a title and an action bar message.
- [ ] Resize the window small and large, toggle fullscreen, and change GUI scale 1, 2, 3, auto.
- [ ] Cap the frame rate at 30 and uncap it: the sway feels the same strength.
- [ ] Join a multiplayer server; one with a server resource pack if you have one.
- [ ] Two hours in one session: no drift in HUD position, no growing stutter.

## 5. Other mods on this game version (auto where the rig can, then play)

Not the author's whole personal mod list: that is fifty-odd mods picked for taste, and most of
them never touch the HUD. Three small rigs instead, built in `build/run/clientGameTest/mods`.
Rerun section 2 with each, then play sections 3 and 4 with rigs A+B+C together.

**Rig A, what nearly everyone runs** (each has over 100 million downloads on Modrinth). These
change how the game draws, so they are the ones most able to break the curve.

- [ ] Sodium, Lithium, FerriteCore, Entity Culling, ImmediatelyFast, Mod Menu, Cloth Config.
- [ ] The same with Iris and a shader pack switched on.

**Rig B, the common HUD mods.** One of each kind, the most downloaded first.

- [ ] A minimap: Xaero's Minimap (the common one) and JourneyMap. Map and frame bend
      together; markers do not flicker while moving. The scripted run can load both at once,
      since it only takes screenshots. For play, use one at a time, as players do: Xaero's for
      the main pass, then swap in JourneyMap for a short second look.
- [ ] Jade: tooltip sways, curves, and its text is not clipped.
- [ ] AppleSkin: overlays stay on the food bar.
- [ ] A recipe viewer (JEI, EMI or REI): its overlay in the inventory stays flat and clickable.
- [ ] Simple Voice Chat, if it exists for the version: its HUD icons sway and stay legible.

**Rig C, suspects.** Rarely installed, but they draw or move HUD parts in unusual ways, so they
are where a problem is most likely.

- [ ] BedrockIfy: paper doll sways with the HUD; screen safe area still works.
- [ ] Raised: offsets add to the sway; moved chat is still clickable.
- [ ] Scoreboard Overhaul, Chat Animation, Smooth GUI, Inventory Profiles Next.
- [ ] Anything else that reports a HUD or rendering conflict in the issues.

**Settings screen**

- [ ] Mod Menu with Cloth Config: the settings screen opens, every slider saves, and a value set
      by command to a finer step is not rounded when the screen is saved untouched.
- [ ] Without Mod Menu and Cloth Config: the game starts and commands still work.

The author's full everyday set is a bonus run, not a requirement.

## 6. Settings file (auto and play)

- [ ] No config file: one is created with the defaults.
- [ ] A `gd656motionhud.json` from before the rename and no `tvomf.json`: settings carry over.
- [ ] A `tvomf.json` from the previous game version: loads unchanged.
- [ ] A broken file (cut it off halfway): the game starts on defaults and the log says why.
- [ ] Out-of-range and non-numeric values typed by hand: no crash, sane fallback.
- [ ] An element id that does not exist under `elements`: ignored with a log line.

## Short form, for a version that shares its code with one already at beta

Written after the first full pass (1.21.11, October 2026), from what that pass did and did not
turn up. It replaces sections 3 to 6 above, not sections 1 and 2.

**When it applies.** The version being tested is the same code as a version that has passed the
full list, differing only by the renames in the port log (26.2 and 26.3 against 1.21.11 are
this). A version that draws its GUI differently, or a release that changes how the HUD is drawn,
gets the full list again.

**What the first pass taught**

| Found by | What it found |
|---|---|
| Scripted settings-file test | A start-up crash on a mistyped settings file |
| Scripted hook report | A hook that silently missed after a port |
| Hand play, with a screenshot taken mid-motion | Seams in the spyglass view while it opened |
| Hand play | The debug screen bending when it should not |
| Hand play, one item at a time | Nothing, across window and GUI-scale changes, frame-rate caps, every toggle and command, and eleven of the other mods one by one |

So: scripts catch what has a right answer, hands catch what only shows in motion, and going
through other mods one at a time was the slowest part and found the least. One pass is a small
sample; this is a judgement about where to spend time, not proof those areas are safe.

**A. Scripted (no one at the keyboard)**

- [ ] Sections 1 and 2.
- [ ] `run-rig-tests.ps1` for the version: every rig that exists for it, and all of them together.
- [ ] `run-config-test.ps1`: all 27 checks.
- [ ] The visual test once more on the oldest Java the version supports, if that is not the Java
      the tests normally run on (`-PtestJava`).

**B. One sitting by hand, about forty minutes, all rigs loaded together (one minimap)**

If anything here fails, split the mods in half and repeat until the one responsible is found;
that is the only time mods are tested apart.

- [ ] Loads with no `[tvomf] Not available...` line in chat.
- [ ] Flick, jump, walk and sprint: the whole HUD, other mods' parts included, moves as one.
- [ ] Curve at 100 in both shapes: nothing torn, doubled or left flat.
- [ ] Spyglass in and out several times, with a screenshot mid-opening: no gaps round the scope.
- [ ] One other full-screen overlay (Nether portal or carved pumpkin): whole and still.
- [ ] F3: flat and readable.
- [ ] Minimap markers steady while moving; a tooltip mod's text not cut off.
- [ ] A 3D model on the HUD (BedrockIfy's paper doll), flicked hard up and left: sways, no crash.
- [ ] Inventory: slots and any mod buttons click where they look; the curve returns on closing.
- [ ] Chat moved by Raised, if installed: a click lands where the text is drawn.
- [ ] Shaders on, then toggled off and on while moving: the HUD does not jump or flatten.
- [ ] Settings screen: set a fine value by command, open and save untouched, value unchanged.

**C. One long session**

- [ ] Two hours in a single launch of ordinary play, online if possible, then: HUD resting where
      it started, sway as smooth, frame rate not sagged, and a clean log. Everyday play on the
      version counts, as long as the launch was not interrupted and the log is checked after.

**Dropped from the hand list, and why**

- Window resize, fullscreen, GUI scale and frame-rate caps: no findings, and nothing in the code
  depends on them beyond reading the screen size each frame.
- Each toggle and each command one by one: no findings, and each is a single switch.
- Death, teleport, bed, mounts and swimming: no findings; the long session covers them as they
  come up in ordinary play.
- Other mods one rig at a time: replaced by one combined pass, split only on a failure.

The exit criteria stay as they are, with "sections 3 to 6" read as "A, B and C".

## Recording a run

```
Build:      tvomf-<version>.jar   SHA-256 <checksum>
Game:       Minecraft <version>, Fabric Loader <version>, Fabric API <version>
Tester:     <name>          Date: <date>
Sections:   1 pass | 2 pass | 3 pass | 4 two items failed (below) | 5 partial | 6 pass
Failures:   <what, how to reproduce, screenshot or log line>
Decision:   stays alpha / promote to beta
```

## Run log

### 1.21.11, tvomf-0.1.1-alpha.1+1.21.11-fabric.jar (SHA-256 ED94E903...DEAF51)

- 2026-10-05, scripted: sections 1 and 2 with the mod alone, and section 2 once per rig (A, A with
  Iris and Complementary Reimagined, B, C, A+B+C). All finished, no warnings, no crash signs.
  Run on Java 25.
- 2026-10-08, played by irfanadli97: **rig A pass, all items green** (startup, sway and motion,
  curve and text, screens, stress) with Sodium, Lithium, FerriteCore, Entity Culling,
  ImmediatelyFast, Mod Menu and Cloth Config. Java version of the instance: not recorded yet.
- Still open: rig A with Iris (played), rigs B and C (played), sections 3, 4 and 6 in full, the
  two-hour session.
- 2026-10-08, played by irfanadli97, rig A with Iris and shaders (instance on Java 25.0.4.1):
  **defect** - with the spyglass, gaps onto the world open at the screen edges or between the
  scope and its black bars, visible only in motion. Not reproduced in the scripted run (four
  frames, mod alone, no shaders). Also asked for: the F3 debug screen should not bend.
- 2026-10-08, `tvomf-0.1.1-alpha.2+1.21.11-fabric.jar`: full-screen overlays and the debug screen
  are now exempt from the curve and sprint zoom as whole units. Scripted run passes with spyglass
  and F3 frames added. **Spyglass fix unconfirmed** until replayed by hand; the rest of the Iris
  pass is still open.
- 2026-10-08, alpha.2 replayed by irfanadli97: spyglass still wrong. Their screenshot (scope
  half open) showed seams of sky and grass between the scope and the bars, widest mid-screen.
  Cause found: the change that marks full-screen overlays as one flat unit had not been applied
  to the code, so alpha.2 only contained the F3 half. Reproduced in the scripted run by
  capturing the first frames of the scope opening (cylinder, strength 100): gaps before, none
  after.
- 2026-10-08, `tvomf-0.1.1-alpha.3+1.21.11-fabric.jar`: overlay fix now really in. Scripted run
  passes; awaiting replay by hand.
- 2026-10-08, alpha.3 replayed by irfanadli97: **rig A with Iris and shaders, all items green**,
  spyglass and F3 included. Rig A is now complete for 1.21.11 (played on Java 25; Java 21 still
  unverified). Still open: rigs B and C played, sections 3, 4 and 6 in full, the two-hour session.

### Fix carried to the other versions, 2026-10-08

- 26.2: `tvomf-0.1.2+26.2-fabric.jar` (SHA-256 7CDCBA9C...10948D). Scripted run passes alone and
  with the author's 56-mod set; spyglass-opening and F3 frames checked. Installed in the author's
  26.2 instance. Not yet played by hand.
- 26.3: `tvomf-0.1.2-alpha.1+26.3-fabric.jar` (SHA-256 FBC91B52...FD8983). Scripted run passes
  with the mod alone; spyglass-opening and F3 frames checked.

### 1.21.11, rig B (on top of rig A, shaders off), alpha.3

- 2026-10-08, played by irfanadli97: **startup, Xaero's Minimap and Jade green.** Still to play
  from this rig: AppleSkin, JEI, Simple Voice Chat, the everything-together items, and the
  optional JourneyMap swap.
- 2026-10-08, played by irfanadli97: **the rest of rig B green** - AppleSkin, JEI, Simple Voice
  Chat, the everything-together items (including spyglass, F3 and fifteen minutes of play), as
  reported: "all of the remaining above". Whether that included the optional JourneyMap swap was
  not stated separately. **Rig B is complete for 1.21.11.**
- Still open for 1.21.11: rig C played, the parts of sections 3, 4 and 6 the rig passes did not
  touch, the two-hour session, and a run on Java 21.

### Section 6 (settings file), scripted, 2026-10-09

- Run against alpha.3 on 1.21.11: **defect** - a settings file with text where a number belongs
  (`"maxOffset": "lots"`) stopped the game from starting. The loader only caught one kind of
  error and this is another kind.
- Fixed in `tvomf-0.1.1-alpha.4+1.21.11-fabric.jar` (SHA-256 ED8656A0...C969E1): any unreadable
  file falls back to defaults and is kept as `tvomf.json.broken`; values outside their documented
  ranges are pulled to the nearest end and logged; an offset for an element id nothing has is
  logged.
- `run-config-test.ps1`: 27 of 27 checks pass (no file, old file name, file from another
  version, cut-off file, out-of-range values, text for a number, unknown element ids), with the
  expected log lines. A real cold start with the crashing file also starts and runs the full
  visual test. Java 25.
- Scripted rig runs repeated on alpha.4 (A, A with Iris and shaders, B, C, A+B+C): all finished,
  no warnings, no crash signs.
- The hand-played passes so far (rig A, rig A with Iris, rig B) were on alpha.3. alpha.4 changes
  only how the settings file is read, not how anything is drawn; they are carried over on that
  basis, with a short smoke check asked of the tester on alpha.4.
- Not scriptable, still open: "a value set by command to a finer step is not rounded when the
  settings screen is saved untouched" (needs Mod Menu and Cloth Config open in the game).
- Same fix on 26.2 (`tvomf-0.1.3+26.2-fabric.jar`, 27 of 27, full 56-mod visual test passes,
  installed in the author's instance) and 26.3 (`tvomf-0.1.2-alpha.2+26.3-fabric.jar`, 27 of 27).

### 1.21.11, rig C (on top of rig A, rig B removed), alpha.4

- 2026-10-09, played by irfanadli97: **alpha.4 smoke check green** (the carry-over of the alpha.3
  passes now has its hand check). **BedrockIfy and Raised green**, including the paper doll under
  a hard up-left flick and clicks on chat moved by Raised. Still to play from this sitting:
  Scoreboard Overhaul, Chat Animation, Smooth GUI, Inventory Profiles Next, all six together,
  the leftover section 3 and 4 items, and the settings-screen rounding item.
- 2026-10-09, played by irfanadli97: **Scoreboard Overhaul, Smooth GUI and Inventory Profiles
  Next green.** Still to play from this sitting: Chat Animation, all six together with ten
  minutes of play, the leftover section 3 and 4 items, and the settings-screen rounding item.
- 2026-10-09, played by irfanadli97: **Chat Animation green.** All six rig C mods have now passed
  one by one. Still to play from this sitting: all six together with ten minutes of play, the
  leftover section 3 and 4 items, and the settings-screen rounding item.
- 2026-10-09, played by irfanadli97: **leftover section 3 and 4 items green** - death and
  respawn, a long teleport, Nether portal overlay, bed and carved pumpkin, boat, horse and
  swimming, sprint transition at 0 and 10, sprint zoom at 0 and 20, each toggle off in turn,
  and `/tvomf element move` with reset. Not yet reported separately: the all-six-together item
  with ten minutes of play, and the settings-screen rounding item.
- 2026-10-09, played by irfanadli97: **all six rig C mods together with ten minutes of play
  green, and the settings-screen rounding item green.** **Rig C is complete for 1.21.11**, and
  with it sections 3, 4, 5 and 6. Remaining before beta: two hours in one session, and a run on
  Java 21.

### Java 21, scripted, 2026-10-09 (1.21.11, alpha.4 code)

- Run on the Minecraft launcher's own Java 21.0.3 (`java-runtime-delta`), confirmed by the debug
  screen in the run's screenshots reading "Java: 21.0.3".
- Visual test with the mod alone: finished, 23 screenshots, no warnings, no crash signs.
- Settings-file test: 27 of 27.
- Visual test with rigs A+B+C (22 mods): finished, 23 screenshots, no warnings, no crash signs.
- Not done on Java 21: any hand play. The author's instance runs Java 25.
- **Remaining before beta: two hours in one session.**
- 2026-10-09: for the two-hour session the author's 26.2 settings were copied into the 1.21.11
  instance (TVOMF: cylinder 9, sprint curve 18, zoom 2.5, sway 44 / 0.37 / 0.25, bob 2 and 3.5;
  plus the HUD mods' settings, game options and server list). The session is to be played online
  with the author's chosen mods on alpha.4.

### Two hours in one session, 2026-10-10 (1.21.11, alpha.4)

- Played by irfanadli97, online, with the author's own settings and 29 mods loaded (the rigs
  plus Just Zoom and others of the author's choosing), Java 25.
- From the game's log: one launch at 18:39:59, still running at 21:10:17, so **2 h 30 min in a
  single session**, with three server joins.
- No crash report. No `Could not hook` or `Not available` line. No stack trace naming the mod.
- One line names the mod: Sodium's standing note that the mod's vertex writer does not use its
  optimised path ("may cause reduced rendering performance"). It has been there since the first
  build and is expected.
- Other errors in the log belong elsewhere: resource-pack metadata notices from Raised and
  Scoreboard Overhaul, Iris shader-program notices, profile key retrieval, and one network
  disconnect ("Connection reset") at 21:00.
- Earlier the same build had 123 minutes of play across five shorter sessions with the same
  clean result.
- Awaiting the tester's own end-of-session observation (HUD rest position, sway smoothness,
  frame rate against the start) before the decision is recorded.
- Tester's observation at the end of the session: the HUD rested where it did at the start, the
  sway was as smooth as in the first minutes, and the frame rate had not sagged.

### Decision, 2026-10-10: 1.21.11 is promoted to beta

```
Build:      tvomf-0.1.1-alpha.4+1.21.11-fabric.jar   SHA-256 ED8656A041631C0E26D4F7A1549FAB4276E7A39A04648D2D68CD3B23E2C969E1
Game:       Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6
Tester:     irfanadli97 (hand play), scripted runs by the test client
Sections:   1 pass | 2 pass (Java 25 and Java 21) | 3 pass | 4 pass | 5 pass (rigs A, A with Iris, B, C) | 6 pass
Session:    2 h 30 min in one launch, online, 29 mods, clean log
Decision:   promote to beta
```

Notes that qualify the record, none of them blocking:

- The hand-played passes for rig A, rig A with Iris and rig B were on alpha.3. alpha.4 differs
  only in how the settings file is read; the scripted rig runs were repeated on alpha.4 and the
  tester's smoke check, rig C, the leftovers and the long session were all on alpha.4.
- All hand play was on Java 25. Java 21 is covered by scripted runs only.
- The beta jar is the alpha.4 code with the version label changed to beta.1.
- `tvomf-0.1.1-beta.1+1.21.11-fabric.jar` (SHA-256 DC84D2E7460EE1EBC253A85E782573C7CFF2FE406FA0B61AF85959067AE673A4) built from the same code; the scripted visual test and the 27 settings-file checks were rerun on it and pass. Not yet released.
