# MaceSMP

**A lightweight Lifesteal-style plugin focused entirely on the Mace.**

Players spawn with a bound starter mace. Every time you kill another player you earn **mace points**. Spend those points in a clean GUI shop (`/maceshop`) to upgrade your mace with powerful enchants.

Vanilla Enchanting Table, Anvil (books), and Grindstone are completely blocked for maces — the only way to enchant them is through the point system.

**Target:** Paper 1.21.4+ (Java 21)  
**Version:** b2.5  
**Author:** UniDex101

---

## Features

- Starter mace given on first join (bound to the player)
- Crafted maces are automatically bound to the crafter
- Kill → Points system with anti-farm protection (last 3 unique victims)
- Modern double-chest GUI shop (`/maceshop`)
- 6 buyable enchants with configurable max levels:
  - Density (V)
  - Breach (IV)
  - Wind Burst (III)
  - Fire Aspect (II)
  - Unbreaking (III)
  - Mending (I)
- Strong protection against vanilla enchanting bypasses
- Pure renaming on anvil is still allowed
- Full MiniMessage support for messages
- Simple YAML player data storage

---

## Screenshots

### GUI Shop
![Mace Enchant Shop](https://raw.githubusercontent.com/UniDex101/macesmp-paper/main/imgs/shop.png)

### Owned Mace
![Owned Mace](https://raw.githubusercontent.com/UniDex101/macesmp-paper/main/imgs/owner.mace.png)

### Stats
![Mace Stats](https://raw.githubusercontent.com/UniDex101/macesmp-paper/main/imgs/chat.stats.png)

### Crafting a Mace
![Crafting Recipe](https://raw.githubusercontent.com/UniDex101/macesmp-paper/main/imgs/craft.recipe.png)

### Protection Examples
| Enchanting Table | Anvil (books blocked) | Grindstone |
|:----------------:|:---------------------:|:----------:|
| ![Enchant Table](https://raw.githubusercontent.com/UniDex101/macesmp-paper/main/imgs/etable.png) | ![Anvil](https://raw.githubusercontent.com/UniDex101/macesmp-paper/main/imgs/anvil.png) | ![Grindstone](https://raw.githubusercontent.com/UniDex101/macesmp-paper/main/imgs/grindstone.png) |

---

## Commands

| Command | Description | Permission | Aliases |
|---------|-------------|------------|---------|
| `/maceshop` | Opens the mace enchant GUI | `macesmp.shop` | `/mshop` |
| `/buy <enchant>` | Buy/upgrade an enchant (hold mace) | `macesmp.buy` | `/macebuy` |
| `/macestats` | View your points, kills, deaths & owned maces | `macesmp.stats` | `/mstats` |

All permissions default to `true`.

---

## Installation

1. Build it yourself [Tutorial](#Building from source)
2. Drop it into your server’s `plugins` folder.
3. Restart / reload the server.
4. (Optional) Edit `plugins/MaceSMP/config.yml` to your liking.

---

## Configuration
`(plugins/MaceSMP/config.yml)`

```yaml
points-per-kill: 1
last-kills-reset-minutes: 3
last-kills-max-size: 3
enchant-cost: 1                    # Points per level

max-levels:
  density: 5
  breach: 4
  wind_burst: 3
  fire_aspect: 2
  unbreaking: 3
  mending: 1
```

All messages support MiniMessage tags (<red>, <gold>, <yellow>, etc.).

---

## Building from source

**Requirements:** Java 21
```bash
gradlew build
```
The jar will appear in `build/libs/MaceSMP-b2.5.jar`

## Documentation

For a deep dive into the ownership system, anti-farm logic, protection listeners, GUI code, and how to add new enchants, see **DOCUMENTATION.md**.

## Future ideas

- Leaderboard (`/maceleaderboard`)
- Public mace shop NPC
- Custom enchants via PDC
- Visual effects on purchase
- Ability to reset mace enchants (expensive)
- Economy plugin integration

# LICENSE

CC0 ❤️❤️❤️
