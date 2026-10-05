# Testing

[Recorded results](runtime-results.md)

Build and check the distributable jars:

```sh
./gradlew clean build
python3 tests/verify-artifacts.py
```

The jar check covers loader metadata, mixin classes, nested model libraries, the
license and accidental inclusion of smoke-test classes or Minecraft's LWJGL core.
GitHub Actions runs it after the build.

## Client smoke tests

These tests need a desktop and an OpenGL-capable GPU. The init script adds a
temporary mixin to the selected development target; normal builds exclude it.
The test skips the accessibility welcome screen, checks glTF/Jackson and Assimp
loading, reaches the title screen and exits.

```sh
./gradlew :1.20.1-forge:runClient -I tests/runtime-smoke.gradle -Psmoke_project=:1.20.1-forge
./gradlew :1.21.1-forge:runClient -I tests/runtime-smoke.gradle -Psmoke_project=:1.21.1-forge
./gradlew :1.21.11-forge:runClient -I tests/runtime-smoke.gradle -Psmoke_project=:1.21.11-forge
```

For the world test, use a **copy** of a Minecraft 26.1.2 world named
`amnetic-smoke` in the target's run directory. Fabric, Quilt and Forge use
`versions/<minecraft>-<loader>/run/saves`; NeoForge uses
`neoforge/run/client/saves`. The test saves the copied world when it exits.

```sh
./gradlew :26.1.2-fabric:runClient -I tests/runtime-smoke.gradle -Psmoke_project=:26.1.2-fabric -Psmoke_world=true
./gradlew :26.1.2-fabric:runClient -Ploader_platform=quilt -I tests/runtime-smoke.gradle -Psmoke_project=:26.1.2-fabric -Psmoke_world=true
./gradlew :26.1.2-forge:runClient -I tests/runtime-smoke.gradle -Psmoke_project=:26.1.2-forge -Psmoke_world=true
./gradlew :neoforge:runNeoForgeClient -I tests/runtime-smoke.gradle -Psmoke_project=:neoforge -Psmoke_world=true
```

The world test enables a point light, bloom and color grading, checks lighting
and post-processing callbacks, reloads resources and verifies nested vertex-array
restoration. It exits after at least 240 world ticks. Look for `SMOKE PASS` and
check the log for Amnetic errors; a Gradle success alone does not establish that
every rendering pass succeeded.

Run `clean build` again before distributing jars after a smoke test. On Windows,
use `.\gradlew.bat` and `python` in place of the commands above.

## Forge packaged-jar check

In an isolated checkout:

```sh
./gradlew :1.20.1-forge:runClient -I tests/runtime-smoke.gradle -I tests/forge-packaged-smoke.gradle -Psmoke_project=:1.20.1-forge
```

This removes the development mod directories and extra library classpath, then
loads the jar and its nested dependencies from `build/packaged-smoke/run/mods`.
It uses the development mapping namespace so it can run against Forge's mapped
development client. Run `clean build` afterward to remove the temporary mixin.
