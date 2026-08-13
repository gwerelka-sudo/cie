# CIE — Custom Item Editor

A client-side Fabric mod for Minecraft **1.21.x** that lets you edit almost every property of an item directly in your hand, entirely through commands — no server plugins, no manual NBT editing, no external tools.

Everything is driven by a single command tree with full argument tab-completion.

## Aliases

The mod registers the same command tree under four names:

```
/cie
/commanditemeditor
/ie
/ei
```

Running the base command with no arguments opens an info screen.

---

## Table of contents

- [Item component editing](#item-component-editing)
- [Raw component access](#raw-component-access)
- [Text, names and lore](#text-names-and-lore)
- [Gradient generator](#gradient-generator)
- [Books](#books)
- [Signs](#signs)
- [Banners](#banners)
- [Item storage](#item-storage)
- [Mouse click history](#mouse-click-history)
- [Macros](#macros)
- [Undo / redo / repeat](#undo--redo--repeat)
- [Import / export](#import--export)
- [Math evaluator](#math-evaluator)
- [Inventory & stack utilities](#inventory--stack-utilities)
- [Fun & misc tools](#fun--misc-tools)
- [Output coloring](#output-coloring)
- [Feedback sounds](#feedback-sounds)
- [Language customization](#language-customization)
- [Storage locations](#storage-locations)

---

## Item component editing

All component editing lives under `/cie edit <component> ...`, each with `get`/`set`/`remove`/`clear`-style subcommands appropriate to the data type. Covers essentially every Minecraft 1.21.x item data component:

| Command | What it edits |
|---|---|
| `edit name` | Custom item name (supports MiniMessage) |
| `edit lore` | Lore lines — add, insert before/after, remove, replace, get whole/single line, in a chosen text format |
| `edit attribute` | Attribute modifiers (damage, speed, etc.) with slot group and operation (add/multiply/base) |
| `edit enchantment` | Enchantments and their levels |
| `edit enchantable` | Which enchantment categories/value the item accepts |
| `edit trim` | Armor trim material and pattern |
| `edit color` | Dye/leather armor color, map color |
| `edit count` | Stack count — `get`, `set <amount>`, `set @max`, `add <amount>`, `take <amount>`; and `count max` — `get`/`set`/`clear` for the item's max stack size |
| `edit durability` | Current damage / max durability |
| `edit equipable` | Equip slot, equip sound, whether it's swappable, dispensable, damage on hurt, camera overlay, allowed entities |
| `edit food` | Nutrition, saturation |
| `edit consumable` | Consume seconds, animation, sound, particles on consume, whether consuming has a consequence |
| `edit component` | Raw get/set/remove for **any** registered data component by id (escape hatch, see below) |
| `edit material` | Underlying item/material conversion |
| `edit deathprotection` | Death protection effects (like totem of undying) |
| `edit firework` | Firework explosion shape, colors, fade colors, flicker/trail flags, flight duration |
| `edit itemmodel` | Custom item model identifier |
| `edit custommodeldata` | Custom model data floats/flags/strings/colors |
| `edit banner` | Banner pattern layers |
| `edit weapon` | Weapon component: attack damage bonus, disable-blocking-for-seconds, item-breaks-from-block-damage |
| `edit sound` (break sound) | Sound played when the item breaks |
| `edit glider` | Whether the item acts as an elytra/glider |
| `edit cooldown` | Use-cooldown group and seconds |
| `edit remainder` (use remainder) | Item left behind after use (e.g. empty bottle) |
| `edit repairable` | Which items/tags can repair this item |
| `edit repaircost` | Anvil repair cost (XP levels) |
| `edit swing` (swing animation) | Custom swing animation behavior |
| `edit rarity` | `common` / `uncommon` / `rare` / `epic` |
| `edit jukebox` | Jukebox-playable song |
| `edit damageresistant` | Damage-type tag the item is resistant to |
| `edit blockattacks` | Shield-like block component: block sound, disable-sound, damage reduction, bypassed-by tag |
| `edit stew` (suspicious stew effects) | Status effects granted by suspicious stew |
| `edit entity` | Entity-related settings (spawn egg entity type, etc.) |
| `edit spawner` | Spawner block-entity settings on spawner items |
| `edit glint` | Force enchantment glint on/off/default |
| `edit tooltip` | Tooltip display: which components show in the tooltip, hide flags, hide-all toggle |
| `edit potion` | Potion contents: base potion, custom color, custom effects with duration/amplifier |
| `edit unbreakable` | Unbreakable flag (and whether it still shows in tooltip) |

### Raw component access

`/cie edit component get|set|remove <type>` is a generic escape hatch: read, overwrite, or delete **any** data component in the game's registry by its identifier, passing the value as raw text/SNBT. `component clear` wipes every component on the item except the custom name and lore (those are managed separately by `/cie edit name` / `/cie edit lore`).

---

## Text, names and lore

- Full **MiniMessage** markup support for names and lore (colors, gradients, hover/click events, etc. — rendered through a bundled MiniMessage-to-vanilla-Text bridge)
- `get` variants of name/lore/book text accept a **format** argument, letting you pull the current text back out formatted as legacy codes, MiniMessage, JSON, etc.

## Gradient generator

`/cie gradient` — a full text color generator producing ready-to-paste colored strings.

**Generation types** (`/cie gradient create ...`):
- `gradient <format> <text> <hex1,hex2,...>` — linear interpolation across any number of color stops
- `rainbow <format> <text> [saturation] [brightness] [shade] [step]` — HSB rainbow; `saturation`/`brightness` 0–100, `shade` 1–7 picks the starting hue, `step` 1–100 controls how much of the color wheel is spread across the text
- `alternation <format> <text> [distance] [hex1,hex2,...]` — colors alternate in blocks of `distance` characters
- `random <format> <text> <colorsCount>` — gradient over 1–50 randomly generated colors

**Built-in output formats** (`/cie gradient format ...`): `legacy`, `legacy&`, `minimessage`, `json` (a real JSON text-component array), `old` and `motd` (per-character `&x`/`§x` legacy codes), `bbcode`, `xml`. `format list` shows all formats, `format <name> get` previews one.

**Custom formats** (`/cie gradient format custom ...`): `create <name> <prefix> <suffix> [separator]`, `list`, `<name> get`, `clear <name>` (reset without deleting), `remove <name>`. Stored on disk so they persist between sessions.

Use `<spc>` inside gradient text as an explicit space placeholder.

## Books

`/cie edit book` — full written-book editing: author, title, individual pages (get/set/insert/remove), with the same text-format `get` support as name/lore.

## Signs

`/cie edit sign` — edit the text stored on a sign item/block entity (front/back lines, glowing text, color), including type-aware suggestions for every wood sign variant registered in the game (excluding hanging signs, handled separately).

## Banners

`/cie edit banner` — add, edit, and reorder banner pattern layers and their dye colors.

---

## Item storage

`/cie storage` — a persistent, chest-like storage system with **100 fixed numbered pages**, each holding 54 slots (double-chest sized).

- `/cie storage` — open the currently active page
- `/cie storage <name>` — open a specific page by name/number
- `/cie storage page list` — list all pages
- `/cie storage page open <name>` — open a page by name
- `/cie storage page clear <name>` (+ `confirm`) — wipe a single page
- `/cie storage page rename <name> <newName>` — rename a page (defaults to its number)
- `/cie storage page lock <name> <true|false>` — lock a page: taking an item from a locked page leaves the original in place and gives you a copy instead (an infinite item dispenser)
- `/cie storage clear` (+ `confirm`) — wipe **all** pages
- `/cie storage save <name>` — save the item in hand into the given page's next free slot

Pages are lazily created on first save; each page persists as its own file, and corrupted page files are automatically detected and quarantined on startup rather than breaking the whole storage system, with a chat notification on world join.

## Mouse click history

`/cie mouseHistory` — the mod passively records every item you click on in **any** inventory screen (chests, crafting tables, your own inventory) — up to 54 most recent clicks.

- `/cie mouseHistory` — opens the history as a real double-chest-style screen you can drag items out of
- `/cie mouseHistory getLast` — instantly puts the last clicked item into your hand
- `/cie mouseHistory clear` — clears the history (in-memory only, does not persist between game restarts)

## Macros

`/cie macro` — record a sequence of executed `/cie` commands and replay them as one unit.

- Start/stop recording; commands run while recording are captured into a macro
- Macros auto-name themselves (`macro_1`, `macro_2`, ...) if you don't give one
- `records play <name>` replays a saved macro through the same command dispatcher used for live input
- Macros persist to disk between sessions

## Undo / redo / repeat

- `/cie undo` / `/cie redo` — step backward/forward through your edit history for the item in hand (snapshots pushed before every mutating command); in-memory only, resets on game restart
- `/cie repeat` — re-executes the last command you ran

## Import / export

- `/cie export giveCommand` — export the item in hand as a vanilla `/give` command string
- `/cie export JSON` — export the item as a JSON data-component representation
- `/cie import <snbt>` — import an item from raw SNBT text back into your hand

## Math evaluator

`/cie math` — a small in-chat calculator, useful when building attribute values, durations, etc.

- `/cie math <expression>` — evaluate an arithmetic expression and print the result
- `/cie math history [count]` — show your last evaluated expressions (default 10)
- `/cie math random <range>` — generate a random number in a given range

## Inventory & stack utilities

- `/cie give` — give yourself items with fine-grained control
- `/cie clearinv` — clear your inventory, or scoped variants: `hotbar`, `armor`, `offhand`, `hand`, `inventory`
- `/cie stack` — stack-related helpers alongside `edit count`

## Fun & misc tools

- `/cie diff` — compares the items in your main hand and off hand and reports the differences between them
- `/cie chaos [overwrite]` — randomizes a curated "safe" set of item components for fun/testing; `overwrite=true` clears that component set first for a "clean" reroll, `false` (default) layers more randomness on top of whatever's already there
- `/cie stats` — shows internal mod statistics
- `/cie reloadConfig` — reloads the mod's language strings and color config from disk without restarting the game (separate from `/cie storage reload`, which only affects item storage)

## Output coloring

`/cie coloring <get|set|reset> <key|value|count|bracket> [hex or &color]` — customize the colors the mod uses when printing structured JSON/`give`-style output in chat. Persisted to disk, resettable per-slot to the built-in defaults.

## !!NEW!! Chat color picker

- Open chat and press small button in down left corner of your screen to open color picker.
- Save colors to presets
- Create example gradients
- Change output formats via format button
- Paste the color without copy to clipboard with LBM click on line

## !!NEW!! Armor stand UI editor

- Change the rotation of armor stand's body parts with scrollers
- Change all flags (small, marker, invisible) via check boxes
- Change armor stand's equipment from storage/offhand/new item

## !!NEW!! Screen color picker

`/cie pickColor` - pick color from your screen and copy it to clipboard

## Feedback sounds

`/cie sound` — configure the sound the mod plays for its own feedback, split into 4 independent categories: `success`, `error`, `warn`, `get`. Each can be toggled on/off and assigned any vanilla `SoundEvent` id. Persisted to disk.

## Language customization

`/cie language` — the mod's entire text is externalized to JSON language files on disk instead of being baked into the jar.

- Ships with `ru_ru` and `en_us` out of the box (seeded to disk on first launch)
- `/cie language` — list available languages and switch the active one on the fly, no restart required
- Create a brand-new language as a copy of `en_us.json` under a new name, then hand-edit every string
- Files you've edited are never silently overwritten by the mod

---

## Storage locations

All persistent mod data lives under `.minecraft/cie/`:

```
cie/
├── languages/
│   ├── en_us.json
│   ├── ru_ru.json
│   ├── current.txt
│   └── <your custom language>.json
├── storage/
│   └── pages/
│       ├── page_1.json ... page_100.json
│       └── corrupt/            # quarantined broken page files
├── macros/
│   └── <macro name>.json
├── formatting/
│   └── custom/
│       └── formats.json        # custom gradient output formats
├── coloring.json                # output color scheme
└── sounds.json                  # feedback sound settings
```

---

## Requirements

- Minecraft **1.21.x**
- **Fabric Loader** + **Fabric API**
- Client-side only — does **not** need to be installed on the server
