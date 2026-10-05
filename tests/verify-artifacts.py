"""Check distributable jars after ./gradlew clean build. Uses only Python's standard library."""
import io
import json
import re
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
jars = sorted(root.glob("versions/*/build/libs/amnetic-*.jar"))
jars += sorted(root.glob("neoforge/build/libs/amnetic-*.jar"))
jars = [p for p in jars if not any(p.stem.endswith(s) for s in ("-sources", "-slim", "-unmapped", "-dev"))]
assert len(jars) == 9, f"Expected 8 Fabric/Forge artifacts and one NeoForge artifact, found {len(jars)}"
for path in jars:
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        assert not any("RuntimeSmoke" in n for n in names), f"Smoke test leaked into {path}"
        config = json.loads(jar.read("amnetic.mixins.json"))
        assert re.fullmatch(r"JAVA_\d+", config["compatibilityLevel"]), path
        for mixin in config.get("mixins", []) + config.get("client", []):
            entry = (config["package"] + "." + mixin).replace(".", "/") + ".class"
            assert entry in names, f"Missing mixin {entry} in {path}"
        for entry in ("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
            if entry in names:
                assert b"${" not in jar.read(entry), f"Unexpanded metadata in {path}: {entry}"
        assert any(n.startswith("LICENSE") for n in names), f"Missing license in {path}"
        assert "org/lwjgl/Version.class" not in names, f"Bundled Minecraft's LWJGL classes in {path}"
        if "META-INF/jarjar/metadata.json" in names:
            nested = [entry["path"] for entry in json.loads(jar.read("META-INF/jarjar/metadata.json"))["jars"]]
        else:
            nested = [entry["file"] for entry in json.loads(jar.read("fabric.mod.json"))["jars"]]
        nested_classes = set()
        for entry in nested:
            assert entry in names, f"Missing nested jar {entry} in {path}"
            with zipfile.ZipFile(io.BytesIO(jar.read(entry))) as library:
                nested_classes.update(library.namelist())
        for entry in ("de/javagl/jgltf/model/io/GltfModelReader.class", "com/fasterxml/jackson/databind/ObjectMapper.class", "org/lwjgl/assimp/Assimp.class"):
            assert entry in nested_classes, f"Missing runtime library {entry} in {path}"
    print(f"PASS {path.relative_to(root)}")
