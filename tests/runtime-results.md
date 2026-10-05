# Runtime results — 5 October 2026

Tested on Windows 11 with an NVIDIA RTX 5070 and OpenGL 4.6. Forge 1.20.1 used
Java 17; Forge 1.21.x used Java 21; Minecraft 26.1.2 used Java 25.

| Target | Result |
| --- | --- |
| Forge 1.20.1 / 47.1.3 | Title screen; glTF/Jackson parsing and Assimp native loading passed |
| Forge 1.21.1 / 52.1.16 | Title screen; glTF/Jackson parsing and Assimp native loading passed |
| Forge 1.21.11 / 61.2.1 | Title screen; glTF/Jackson parsing and Assimp native loading passed |
| Fabric 26.1.2 | World test passed: 240 ticks, 1,243 lighting and post-processing frames |
| Forge 26.1.2 / 64.1.3 | World test passed: 240 ticks, 1,308 lighting and post-processing frames |
| NeoForge 26.1.2 / 26.1.2.109 | World test passed: 240 ticks, 1,288 lighting and post-processing frames |
| Quilt 26.1.2 / 0.31.0-beta.4 | World test passed: 240 ticks, 1,282 lighting and post-processing frames |

Each world run also read the bundled glTF fixture, loaded Assimp, enabled a point
light, bloom and color grading, reloaded resources, checked nested vertex-array
restoration and shut down. These are development-client checks. Older-version
world rendering, shader packs, other GPUs and other operating systems were not
tested. Frame counts depend on the machine and are not performance benchmarks.

The Forge 1.20.1 packaged-jar check also passed with the development source
directories and extra library classpath removed. Forge discovered all six nested
libraries; the glTF/Jackson and Assimp probes passed before reaching the title screen.

The reproducible commands are in [README.md](README.md). Full build and packaged
jar checks cover all four Fabric targets, all four Forge targets, NeoForge and
the examples. GitHub Actions runs the packaged-jar check after building.
