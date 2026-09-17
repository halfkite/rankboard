"""Package the NeoForge 1.21.x implementation jars behind one mod entrypoint."""

from __future__ import annotations

import argparse
import io
import re
import zipfile
from pathlib import Path


VARIANTS_BY_FAMILY = {
    "1.21": ("1.21.1", "1.21.4", "1.21.8", "1.21.11"),
    "26.1": ("26.1", "26.1.1", "26.1.2"),
}
OUTER_ENTRIES = (
    "META-INF/MANIFEST.MF",
    "rankboard.mixins.json",
    "cn/bamgdam/rankboard/UniversalBridge.class",
    "cn/bamgdam/rankboard/UniversalNeoForgeEntrypoint.class",
    "cn/bamgdam/rankboard/mixin/PlayerEntityNameMixin.class",
    "cn/bamgdam/rankboard/mixin/ServerPlayerListNameMixin.class",
)


def copy_zip_entry(output: zipfile.ZipFile, source: zipfile.ZipFile, name: str) -> None:
    info = source.getinfo(name)
    output.writestr(info, source.read(name))


def strip_variant(source: zipfile.ZipFile) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as output:
        for info in source.infolist():
            name = info.filename
            if name in {
                "META-INF/neoforge.mods.toml",
                "rankboard.mixins.json",
                "cn/bamgdam/rankboard/UniversalBridge.class",
                "cn/bamgdam/rankboard/UniversalNeoForgeEntrypoint.class",
                "cn/bamgdam/rankboard/mixin/PlayerEntityNameMixin.class",
                "cn/bamgdam/rankboard/mixin/ServerPlayerListNameMixin.class",
            }:
                continue
            output.writestr(info, source.read(info))
    return buffer.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("variants", type=Path, help="Directory containing 1.21.1.jar etc.")
    parser.add_argument("output", type=Path)
    parser.add_argument(
        "--family",
        choices=tuple(VARIANTS_BY_FAMILY),
        default="1.21",
        help="Minecraft family represented by the nested variants",
    )
    args = parser.parse_args()

    variants = VARIANTS_BY_FAMILY[args.family]
    variant_paths = [args.variants / f"{version}.jar" for version in variants]
    missing = [path for path in variant_paths if not path.is_file()]
    if missing:
        raise SystemExit("Missing variant jars: " + ", ".join(map(str, missing)))

    minecraft_range = "[1.21,1.22)" if args.family == "1.21" else "[26.1,26.2)"
    neoforge_range = "[21.0,)" if args.family == "1.21" else "[26.1,)"
    with zipfile.ZipFile(variant_paths[0]) as base:
        metadata = base.read("META-INF/neoforge.mods.toml").decode("utf-8")
        metadata = re.sub(
            r'versionRange="\[[^"]+\]"',
            f'versionRange="{minecraft_range}"',
            metadata,
            count=1,
        )
        metadata = re.sub(
            r'(modId="minecraft"\s*\n\s*type="required"\s*\n\s*versionRange=)"[^"]+"',
            rf'\1"{minecraft_range}"',
            metadata,
            count=1,
        )
        metadata = re.sub(
            r'(modId="neoforge"\s*\n\s*type="required"\s*\n\s*versionRange=)"[^"]+"',
            rf'\1"{neoforge_range}"',
            metadata,
            count=1,
        )

        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as output:
            output.writestr("META-INF/neoforge.mods.toml", metadata.encode("utf-8"))
            for entry in OUTER_ENTRIES:
                copy_zip_entry(output, base, entry)
            for version, path in zip(variants, variant_paths):
                with zipfile.ZipFile(path) as variant:
                    nested = strip_variant(variant)
                output.writestr(
                    f"META-INF/rankboard-variants/{version}.jar",
                    nested,
                )

    print(args.output)


if __name__ == "__main__":
    main()
