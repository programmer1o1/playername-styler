# PlayerName Styler

Add configurable nicknames and styled player names across chat, tab list, and above-head nameplates.

## Features

- **Per-player nicknames** (`/nickname`)
- **Configurable formatting** for chat, tab list, and nameplates (TOML)
- **Placeholder system** (built-ins + API for other mods to register their own placeholders)
- **Styling support**
  - Minecraft `&` color/format codes (`&a`, `&6`, `&l`, `&r`, etc.)
  - Hex colors (`&#RRGGBB` and `<#RRGGBB>`)
  - Tags like `<bold>`, `<red>`, `<reset>`
  - `<gradient:#RRGGBB:#RRGGBB>text</gradient>` and `<rainbow>text</rainbow>`
- **LuckPerms prefix/suffix integration** *(optional via `%prefix%` / `%suffix%`; empty when LuckPerms is not installed)*
- **Simple JSON nickname storage** in your config folder (`config/nicknames.json`)
- **Real username lookup** with `/realname`
- **Nameplate renderer is configurable** (`text_display` default, with `armor_stand` fallback)

## Commands

- `/nickname` — show your current nickname
- `/nickname <nickname>` — set your nickname
- `/nickname clear` — clear your nickname
- `/nickname set <player> <nickname>` — admin set (OP level 2)
- `/nickname clear <player>` — admin clear (OP level 2)
- `/nickname reload` — reload nicknames file (OP level 2)
- `/realname` — list all nicknames
- `/realname <search>` — search nickname/real name

## Configuration

Config file: `config/playernamestyler-common.toml`

- `chatFormat`, `tabListFormat`, `nameplateFormat` accept placeholders like:
  - `%displayname%`, `%username%`, `%prefix%`, `%suffix%`, `%chatmessage%`
  - `%world%` / `%dimension%`, `%biome%`, `%ping%`, `%x%/%y%/%z%`, `%health%`, etc.
- `nameplateRenderer` can be `text_display` or `armor_stand`

## Compatibility

- **Loader:** NeoForge
- **Minecraft:** 1.21.1–1.21.11 (separate build per listed version)

![Example of Styled Nickname](https://cdn.modrinth.com/data/cached_images/f3ed860f50aafefa2776d13cbd735951780ad2c4.png)
