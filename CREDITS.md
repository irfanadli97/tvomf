# Credits

## GD656MotionHUD by Minecraft_GD656

There's a Visor On My Face started as a Fabric port of
[GD656MotionHUD](https://modrinth.com/mod/gd656motionhud) by
[Minecraft_GD656](https://modrinth.com/user/Minecraft_GD656), a Forge 1.20.1 mod released under
the MIT License. The idea of a HUD that sways against your camera, Battlefield-style, is theirs.

This project is not affiliated with, endorsed by, or supported by Minecraft_GD656. Please do not
send them bug reports about it.

### What comes from the original

The original mod has no public source repository, so the port was made by reading its released
jar (version 0.0.5-1.20.1-forge), which its MIT License permits.

- The sway model: camera rotation pushes the HUD the opposite way and the offset decays
  exponentially.
- The three sway settings and their defaults and ranges: `maxOffset` (25, 0-50), `damping`
  (0.25, 0.1-1.0) and `sensitivity` (0.1, 0.01-1.0).
- The `config list`, `config edit` and `config reset` commands and the wording of their messages.

### What is new here

Everything else was written for this project: the rewrite for Fabric on Minecraft 26.2, and the builds for 26.3 and 1.21.11 that followed, the
frame-rate independent sway and soft limit, vertical sway from jumping and falling, walking bob,
the curved HUD (sphere and cylinder), the sprint curve and zoom-out, per-element offsets, sway for
3D models on the HUD, the settings screen, and the visual test rig.

### Licence

The original's metadata declares the MIT License but its jar ships no licence text or copyright
line. The [LICENSE](LICENSE) file here therefore carries the standard MIT text with a copyright
line naming Minecraft_GD656 for the original work, taken from the author and release year shown on
its Modrinth page, alongside the line for this project's contributors.

## Libraries

- [Fabric API](https://github.com/FabricMC/fabric) and [Fabric Loader](https://github.com/FabricMC/fabric-loader)
- [MixinExtras](https://github.com/LlamaLad7/MixinExtras)
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config) for the settings screen
