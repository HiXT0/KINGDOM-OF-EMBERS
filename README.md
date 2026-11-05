# Kingdom of Embers

A single-player fantasy RPG written in Java. Travel through a corrupted kingdom, fight monsters, level up, learn skills, and defeat the boss, the **Ember Lord**.

Available in two versions that share the same game logic:

- **Console version** (`KingdomOfEmbers.java`): text-based RPG
- **Graphical version** (`KingdomOfEmbersGUI.java`): Java Swing window with tile maps, sprites and a battle screen

## Screenshots

<!-- Add screenshots here after your playthrough -->
| Map | Battle | Skills |
|-----|--------|--------|
| ![Map](screenshots/map.png) | ![Battle](screenshots/battle.png) | ![Skills](screenshots/skills.png) |

## Features

- **Five areas:** Ashfall Village, Whispering Forest, Abandoned Ruins, Underground Dungeon, Ember Lord's Lair. Each has its own tile map and colours, and higher areas require a higher level.
- **Turn-based combat:** attack, Power Strike (with cooldown), potion, flee, or cast a skill. Attacks have a 15% critical hit chance.
- **Visible monsters:** monsters roam the map and chase the hero when it gets close. Touching one starts a battle.
- **Enemies:** Goblin, Wolf, Skeleton Warrior, Dark Knight, and the Ember Lord, who uses a powerful special attack every third turn.
- **Progression:** defeating enemies gives XP and gold. Levelling up raises health, attack, defence and mana.
- **Mana and skills:**

  | Skill | Unlocks at | Effect |
  |-------|-----------|--------|
  | Fireball | Level 2 | Damage that ignores enemy defence |
  | Heal | Level 3 | Restores 40% of maximum health |
  | Guard | Level 4 | Halves the next hit taken |
  | Ember Storm | Level 6 | Heavy damage attack |

- **Village:** rest at the inn, buy potions and weapon upgrades, and talk to the Elder NPC for a quest (slay 5 monsters).
- **Save and load:** progress is stored in a save file.

## How to run

Requires Java (JDK 11 or newer). Put both `.java` files in the same folder.

**Graphical version**

```bash
javac KingdomOfEmbers.java KingdomOfEmbersGUI.java
java KingdomOfEmbersGUI
```

**Console version**

```bash
java KingdomOfEmbers.java
```

## Controls (graphical version)

| Key | Action |
|-----|--------|
| Arrow keys / WASD | Move |
| 1-5 | Travel between areas |
| P | Use a potion |
| H | Cast Heal |
| K | Save |
| L | Load |

**In battle**

| Key | Action |
|-----|--------|
| 1 | Attack |
| 2 | Power Strike |
| 3 | Potion |
| 4 | Flee |
| 5-8 | Skills |

## Design

The code is object-oriented:

- `Fighter` is the base class for all combatants.
- `Hero` and `Enemy` extend `Fighter`.
- `Wolf` and `Boss` extend `Enemy` and override how they attack (polymorphism).
- The GUI reuses the console game's classes, keeping game logic separate from display.

## Known limitations

- The project was built in two days, so scope was deliberately reduced from the original LibGDX proposal to a console game plus a Swing GUI.
- Graphics are simple tile and sprite drawings, with no sound.
- There is a single save slot, and MP is not saved (it is restored to full when a game is loaded).
- The graphical version is still being playtested for balance and bugs.

## Future work

- Port the game to **LibGDX** for a fuller 2D experience (the original proposal)
- Treasure chests and an armour system
- Additional quests and NPCs
- Sound effects and music
- Floating damage numbers and battle animations

## Tools

Java, Java Swing, Visual Studio Code, Git and GitHub.
