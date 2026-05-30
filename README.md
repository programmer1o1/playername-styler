# PlayerName Styler (NeoForge)

Formats player display names in:
- Chat
- Tab list
- Above-head nameplate (default `text_display`, with `armor_stand` fallback)

Supports placeholders (see `config/playernamestyler-common.toml`) and optional LuckPerms prefix/suffix integration.

## Supported Minecraft versions

Stonecutter projects included in this repo:
- `1.21.1`
- `1.21.4`
- `1.21.6`
- `1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`

## Build

Build a single version:
- `./gradlew :v1_21_4:build`

Build and collect all jars into `all-jars/`:
- `./gradlew collectAllJars`

`all-jars/` is a build artifact folder and is gitignored.

Artifacts will be named like:
- `playernamestyler-2.0.2-mc1.21.4.jar`
- `playernamestyler-2.0.2-mc1.21.4-sources.jar`
