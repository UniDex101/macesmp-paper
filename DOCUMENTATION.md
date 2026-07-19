# MaceSMP - Documentation

**Version:** b2.1 (GUI Update)
**Target:** Paper 1.21.4+ (Java 21)
**Author:** UniDex101

---

## Table of Contents
1. [Overview](#overview)
2. [Core Features](#core-features)
3. [Project Structure](#project-structure)
4. [Key Systems Explained](#key-systems-explained)
   - Ownership System (PDC + Lore)
   - Points & Kill System + Anti-Farm
   - Protection System (Anvil / Enchant Table / Grindstone)
   - The New GUI Shop
5. [Configuration (config.yml)](#configuration-configyml)
6. [How to Make Common Changes](#how-to-make-common-changes)
   - Change upgrade cost
   - Add / remove an enchant from the shop
   - Change max levels
   - Modify GUI layout or icons
   - Change messages / colors
   - Add a new feature
7. [Building the Plugin](#building-the-plugin)
8. [Important Notes & Gotchas](#important-notes--gotchas)
9. [Future Ideas](#future-ideas)

---

## Overview

MaceSMP is a lightweight Lifesteal-inspired plugin focused on the **Mace** weapon.  
Players get a bound starter mace, earn points by killing other players (with anti-farm protection), and spend those points to upgrade their mace with special enchants using `/maceshop` (now a nice GUI) or `/buy`.

The plugin heavily protects the mace economy:
- Maces can **only** be enchanted via the point system (`/buy` or GUI).
- Vanilla Enchanting Table, Anvil (with books), and Grindstone are blocked for maces.

---

## Core Features

- Starter mace on first join (bound to player)
- Crafted maces are automatically bound
- Kill → Points system with anti-farm (last 3 unique victims)
- 6 buyable enchants: `density`, `breach`, `wind_burst`, `fire_aspect`, `unbreaking`, `mending`
- Strong protection against vanilla enchanting bypasses
- Modern Adventure text everywhere
- New **double-chest GUI shop** (`/maceshop`)

---

## Project Structure

```
src/main/java/com/macesmp/
├── MaceSMP.java                 # Main plugin class (registers everything)
├── PlayerData.java              # Player stats + points (YAML persistence)
├── commands/
│   ├── BuyCommand.java          # /buy logic
│   ├── MaceShopCommand.java     # /maceshop → opens the GUI
│   └── MaceStatsCommand.java    # /macestats
├── listeners/
│   ├── CraftListener.java       # Tags crafted maces as owned
│   ├── DeathListener.java       # Handles kills → points + anti-farm
│   ├── MaceProtectionListener.java  # Blocks vanilla enchanting on maces
│   ├── PlayerJoinListener.java  # Gives starter mace + welcome
│   └── ShopListener.java        # Handles clicks inside the GUI shop
└── util/
    └── MaceUtils.java           # Helper methods (ownership, enchants, messages)
```

**Resources:**
- `config.yml` — All settings and messages
- `plugin.yml` — Command + permission definitions

---

## Key Systems Explained

### 1. Ownership System (PDC + Lore)

Every mace stores its owner in two places:
- **PersistentDataContainer** (hidden, using `NamespacedKey("macesmp", "owner")`)
- **Lore** (visible: "Bound to: PlayerName")

**Why both?**
- PDC = reliable machine-readable ownership
- Lore = players can see who owns it + they can freely rename the mace via anvil (we never touch `displayName`)

**Important methods in `MaceUtils.java`:**
- `setOwner(item, uuid)`
- `getOwner(item)`
- `isOwnedBy(item, player)`
- `createStarterMace(player)`
- `hasOwnedMace(player)`

**Tip:** Never put the owner name in `displayName()`. Always use lore only.

### 2. Points & Kill System + Anti-Farm

- Every valid kill gives `points-per-kill` points (default 1).
- `DeathListener` uses a **FIFO queue** (`lastKills`) per player.
- Queue size controlled by `last-kills-max-size` (default 3).
- Queue resets every `last-kills-reset-minutes` (default 5).
- If you kill the same person again while they're still in your queue → no points.

This prevents kill-farming.

### 3. Protection System (`MaceProtectionListener`)

This is the most important file for keeping the economy fair.

**What it does:**
- **Enchantment Table**: When a mace is placed → all 3 offer slots are cleared. No enchants can be chosen.
- **Anvil**:
  - Pure renaming → **allowed**
  - Trying to combine with enchanted book or another mace → blocked
  - Repairing with items (no enchants) → allowed
- **Grindstone**: Completely blocked for maces (bought enchants stay forever)

**Why we use `Prepare*` events:**
They fire before the action happens, so we can cancel or modify the result safely.

**Recent fix:** Switched to `AnvilView.getRenameText()` to remove deprecation warning.

### 4. The New GUI Shop (`/maceshop`)

**Files involved:**
- `MaceShopCommand.java` → Builds and opens the 54-slot inventory
- `ShopListener.java` → Listens for clicks and processes purchases

**How it works:**
1. Player runs `/maceshop`
2. `MaceShopCommand.openShopGUI()` creates a double chest
3. It checks what the player is currently holding
4. For each enchant it shows current level (from held mace) vs max level
5. Items are tagged with `PersistentDataContainer` key `shop_enchant` = "density" etc.
6. When player clicks → `ShopListener` reads the tag, re-validates they still hold an owned mace, then runs the purchase logic.

**Design choices:**
- Black stained glass for clean look
- Themed icons per enchant
- Live level display when holding mace
- Auto-closes after successful purchase (simple & safe)

---

## Configuration (config.yml)

```yaml
points-per-kill: 1
last-kills-reset-minutes: 5
last-kills-max-size: 3
enchant-cost: 1                    # Points needed per level

max-levels:
  density: 5
  breach: 4
  wind_burst: 3
  fire_aspect: 2
  unbreaking: 3
  mending: 1

messages:
  prefix: "<dark_gray>[<gold>MaceSMP</gold>]</dark_gray> "
  # ... other messages
```

**Tip:** You can use MiniMessage or legacy `&` codes in messages.

---

## How to Make Common Changes

### Change the cost of an upgrade
→ Edit `config.yml` → `enchant-cost: 2`

### Add or remove an enchant from the shop
1. Add/remove it from these places:
   - `MaceShopCommand.java` → `ENCHANT_ORDER` list and `ENCHANT_ICONS` map
   - `ShopListener.java` → (only if you added new logic)
   - `BuyCommand.java` → `ENCHANT_NAMES` list (for tab complete)
   - `MaceUtils.java` → `getEnchantByName()` and `getEnchantDisplayName()`
   - `config.yml` → add to `max-levels`

2. Rebuild the plugin.

### Change max level of an enchant
-> Just edit `config.yml` under `max-levels`.

### Modify the GUI layout or icons
Edit `MaceShopCommand.java`:
- Change `slots` array to move items around
- Change `ENCHANT_ICONS` map to use different `Material`
- Edit `createEnchantItem()` method to change lore or formatting

### Change messages / colors
Edit `config.yml` under `messages:` section.
The plugin uses MiniMessage-style tags (`<red>`, `<yellow>`, etc.).

### Make `/buy` and GUI share the same purchase logic (probable future refactor)
Currently the purchase logic is duplicated between `BuyCommand` and `ShopListener`.

**Best practice:** Extract the core logic into a method in `BuyCommand` like:

```java
public boolean attemptPurchase(Player player, String enchantName) {
    // all the checks + apply logic here
    return true; // success
}
```

Then call it from both places.

---

## Building the Plugin

```bash
gradlew clean build
```

The jar will appear in `build/libs/MaceSMP-b2.1.jar` (or whatever version is set in `build.gradle.kts`).

**Requirements:**
- Java 21
- Paper 1.21.4 API (already declared in `build.gradle.kts`)

---

## Important Notes & Gotchas

1. **Never modify `displayName`** of a mace in code. Only use `lore`. This lets players rename freely.
2. Always re-check `isOwnedBy()` when the player clicks in the GUI (they might have swapped items).
3. The protection listener only affects **maces**. Everything else works normally.
4. `PlayerData` is saved on disable and when points change. For high-traffic servers you may want async saving later.
5. `NamespacedKey` must be created with the plugin instance for best practice (`new NamespacedKey(plugin, "key")`).

---

## Future Ideas

- Leaderboard command (`/maceleaderboard`)
- Public mace shop NPC / villager
- More enchants (custom ones via PDC)
- Visual effects when buying (particles + sound)
- Ability to reset a mace's enchants (expensive)
- Integration with economy plugins (sell points, give them to other players)
