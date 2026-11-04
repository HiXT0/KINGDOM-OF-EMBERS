# Kingdom of Embers

A single-player, turn-based fantasy RPG written in **Java**. A mysterious corruption is burning the kingdom. Level up, gather gear, and defeat the **Ember Lord** to save it.

This is the console version of the project. A graphical LibGDX version is planned as future work.

## Features

- Turn-based combat: attack, power strike (with cooldown), potions, inspect enemy, flee
- Critical hits (15% chance, double damage)
- Enemies: Goblin, Wolf, Skeleton Warrior, Dark Knight, and the Ember Lord boss
- Boss special attack ("Ember Fury") every third turn
- XP and level progression (more HP, attack and defence per level)
- Gold, potions and a village shop
- Elder NPC with a quest (slay 5 monsters)
- 5 areas gated by player level
- Save and load (`embers.sav`)

## How to run

Requires **Java 11 or newer**.

```
java KingdomOfEmbers.java
```

Or compile first:

```
javac KingdomOfEmbers.java
java KingdomOfEmbers
```

## How to play

1. Start in **Ashfall Village**: rest, buy potions and upgrades, talk to the Elder.
2. **Travel** to other areas and **Explore** to fight enemies.
3. Gain levels to unlock tougher areas.
4. Reach level 7, enter the **Ember Lord's Lair**, and win the final battle.
5. Use **Save** any time; the game offers to continue from your save on startup.

## Object-oriented design

| Principle | Where it is used |
|---|---|
| Encapsulation | `Fighter` keeps stats protected and changes them via `takeDamage()` and `heal()` |
| Inheritance | `Hero` and `Enemy` extend `Fighter`; `Wolf` and `Boss` extend `Enemy` |
| Polymorphism | `attackRoll()` is overridden by `Wolf` (bonus bite) and `Boss` (Ember Fury) |
| Abstraction | The `fight()` method works with any `Enemy` without knowing its type |

## Screenshots

Add screenshots to a `screenshots/` folder and link them here:

```
![Combat](screenshots/combat.png)
![Victory](screenshots/victory.png)
```

## Future scope

- LibGDX graphical version with maps and sprites
- More quests, NPC dialogue and equipment
- Additional character classes and skill trees

## Tech

Java, Git, GitHub. Planned: LibGDX, Gradle.
