import java.io.*;
import java.util.*;

/**
 * Kingdom of Embers - console RPG (2-day MVP).
 * Run with:  java KingdomOfEmbers.java      (Java 11+)
 */
public class KingdomOfEmbers {

    static final Random rnd = new Random();
    static final Scanner in = new Scanner(System.in);
    static final String SAVE_FILE = "embers.sav";

    // ---------- Characters (Inheritance + Encapsulation) ----------

    static abstract class Fighter {
        protected String name;
        protected int hp, maxHp, atk, def;

        Fighter(String name, int hp, int atk, int def) {
            this.name = name;
            this.hp = this.maxHp = hp;
            this.atk = atk;
            this.def = def;
        }

        boolean alive() { return hp > 0; }
        void takeDamage(int d) { hp = Math.max(0, hp - d); }
        void heal(int a) { hp = Math.min(maxHp, hp + a); }
        int attackRoll() { return atk + rnd.nextInt(3); }
    }

    static class Hero extends Fighter {
        int level = 1, xp = 0, potions = 3, gold = 0;
        int kills = 0, quest = 0, questKills = 0; // quest: 0 none, 1 active, 2 done
        int specialCooldown = 0;
        int area = 0;

        static final int KNIGHT = 0, MAGE = 1, ROGUE = 2;
        static final String[] CLASS_NAMES = { "Knight", "Mage", "Rogue" };
        int heroClass = KNIGHT;

        // Shop purchases (used by KingdomOfEmbersGame and ShopWindow)
        int bigPotions = 0, ethers = 0, amulets = 0, charms = 0;
        String weaponId = "", armorId = "";                       // equipped gear ("" = none), see ShopItems
        final Set<String> owned = new LinkedHashSet<>();          // weapons and armor bought so far
        final Set<String> books = new LinkedHashSet<>();          // skill books learned

        Hero(String name) { super(name, 50, 10, 2); }

        Hero(String name, int heroClass) {
            super(name, 50, 10, 2);
            this.heroClass = heroClass;
            if (heroClass == KNIGHT)    { hp = maxHp = 65; atk = 9;  def = 4; }
            else if (heroClass == MAGE) { hp = maxHp = 42; atk = 8;  def = 1; }
            else                        { hp = maxHp = 50; atk = 10; def = 2; }
        }

        String className() { return CLASS_NAMES[heroClass]; }

        int xpToNext() { return level * 30; }

        boolean gainXp(int amount) {
            xp += amount;
            boolean leveled = false;
            while (xp >= xpToNext()) {
                xp -= xpToNext();
                level++;
                maxHp += 10; atk += 2; def += 1;
                hp = maxHp;
                leveled = true;
            }
            return leveled;
        }
    }

    // ---------- Enemies (Polymorphism) ----------

    static class Enemy extends Fighter {
        final int xpReward, goldReward;

        Enemy(String name, int hp, int atk, int def, int xp, int gold) {
            super(name, hp, atk, def);
            this.xpReward = xp;
            this.goldReward = gold;
        }

        String describe() {
            return name + " | HP " + hp + "/" + maxHp + " | ATK " + atk + " | DEF " + def;
        }
    }

    static class Wolf extends Enemy {
        Wolf() { super("Wolf", 30, 7, 1, 20, 10); }

        @Override
        int attackRoll() { // fast and aggressive: sometimes a bonus bite
            int dmg = super.attackRoll();
            if (rnd.nextInt(100) < 30) {
                System.out.println("The wolf bites again, viciously!");
                dmg += 4;
            }
            return dmg;
        }
    }

    static class Boss extends Enemy {
        private int turns = 0;

        Boss() { super("Ember Lord", 220, 18, 7, 200, 100); }

        @Override
        int attackRoll() { // special attack every 3rd turn
            turns++;
            if (turns % 3 == 0) {
                System.out.println(">>> The Ember Lord unleashes EMBER FURY! <<<");
                return super.attackRoll() * 2;
            }
            return super.attackRoll();
        }
    }

    // ---------- World ----------

    static final String[] AREA_NAMES = {
        "Ashfall Village", "Whispering Forest", "Abandoned Ruins", "Underground Dungeon", "Ember Lord's Lair"
    };
    static final int[] AREA_MIN_LEVEL = {1, 1, 3, 5, 7};

    static Enemy spawn(int area) {
        switch (area) {
            case 1:  return rnd.nextBoolean() ? new Enemy("Goblin", 25, 6, 1, 15, 8) : new Wolf();
            case 2:  return new Enemy("Skeleton Warrior", 45, 9, 4, 30, 15);
            case 3:  return new Enemy("Dark Knight", 70, 13, 6, 50, 25);
            default: return new Boss();
        }
    }

    // ---------- Input helper ----------

    static int ask(String prompt, int lo, int hi) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v >= lo && v <= hi) return v;
            } catch (NumberFormatException ignored) { }
            System.out.println("Invalid choice.");
        }
    }

    // ---------- Combat ----------

    /** Returns 1 = victory, 0 = fled, -1 = hero died. */
    static int fight(Hero hero, Enemy enemy) {
        boolean isBoss = enemy instanceof Boss;
        System.out.println("\n*** A wild " + enemy.name + " appears! ***");

        while (hero.alive() && enemy.alive()) {
            System.out.println("\n" + hero.name + " HP " + hero.hp + "/" + hero.maxHp
                    + "   vs   " + enemy.name + " HP " + enemy.hp + "/" + enemy.maxHp);
            System.out.println("1) Attack  2) Special" + (hero.specialCooldown > 0 ? " (cooldown " + hero.specialCooldown + ")" : "")
                    + "  3) Potion (" + hero.potions + ")  4) Inspect  5) Flee");
            int choice = ask("> ", 1, 5);
            boolean usedTurn = true;

            switch (choice) {
                case 1: {
                    heroStrike(hero, enemy, 1.0, "You slash the " + enemy.name);
                    break;
                }
                case 2: {
                    if (hero.specialCooldown > 0) {
                        System.out.println("Special attack is recharging.");
                        usedTurn = false;
                    } else {
                        heroStrike(hero, enemy, 2.0, "You unleash a POWER STRIKE on the " + enemy.name);
                        hero.specialCooldown = 3;
                    }
                    break;
                }
                case 3: {
                    if (hero.potions <= 0) {
                        System.out.println("No potions left!");
                        usedTurn = false;
                    } else {
                        hero.potions--;
                        int before = hero.hp;
                        hero.heal(30);
                        System.out.println("You drink a potion and recover " + (hero.hp - before) + " HP.");
                    }
                    break;
                }
                case 4: {
                    System.out.println(enemy.describe());
                    usedTurn = false;
                    break;
                }
                case 5: {
                    if (isBoss) {
                        System.out.println("You cannot escape the Ember Lord!");
                    } else if (rnd.nextInt(100) < 50) {
                        System.out.println("You escaped!");
                        return 0;
                    } else {
                        System.out.println("Couldn't get away!");
                    }
                    break;
                }
            }

            if (!usedTurn) continue;
            if (hero.specialCooldown > 0 && choice != 2) hero.specialCooldown--;

            if (enemy.alive()) {
                int dmg = Math.max(1, enemy.attackRoll() - hero.def);
                hero.takeDamage(dmg);
                System.out.println("The " + enemy.name + " hits you for " + dmg + " damage.");
            }
        }

        if (!hero.alive()) return -1;

        System.out.println("\nYou defeated the " + enemy.name + "!");
        hero.gold += enemy.goldReward;
        hero.kills++;
        if (hero.quest == 1) hero.questKills++;
        System.out.println("+" + enemy.xpReward + " XP, +" + enemy.goldReward + " gold.");
        if (hero.gainXp(enemy.xpReward)) {
            System.out.println("*** LEVEL UP! You are now level " + hero.level + ". HP, ATK and DEF increased! ***");
        }
        if (rnd.nextInt(100) < 25) {
            hero.potions++;
            System.out.println("The enemy dropped a health potion!");
        }
        return 1;
    }

    static void heroStrike(Hero hero, Enemy enemy, double mult, String text) {
        int raw = hero.attackRoll();
        boolean crit = rnd.nextInt(100) < 15;
        if (crit) { raw *= 2; }
        int dmg = Math.max(1, (int) (raw * mult) - enemy.def);
        enemy.takeDamage(dmg);
        System.out.println(text + " for " + dmg + " damage" + (crit ? " - CRITICAL HIT!" : "."));
    }

    // ---------- Village ----------

    static void villageMenu(Hero hero) {
        System.out.println("\n--- " + AREA_NAMES[0] + " ---");
        System.out.println("1) Rest at the inn (free, full heal)");
        System.out.println("2) Shop");
        System.out.println("3) Talk to the Elder");
        System.out.println("4) Back");
        int c = ask("> ", 1, 4);
        if (c == 1) {
            hero.hp = hero.maxHp;
            System.out.println("You rest and feel fully restored.");
        } else if (c == 2) {
            System.out.println("Gold: " + hero.gold);
            System.out.println("1) Health potion (15g)  2) Iron upgrade, +3 ATK (40g)  3) Leave");
            int s = ask("> ", 1, 3);
            if (s == 1 && hero.gold >= 15) { hero.gold -= 15; hero.potions++; System.out.println("Bought a potion."); }
            else if (s == 2 && hero.gold >= 40) { hero.gold -= 40; hero.atk += 3; System.out.println("Your weapon is stronger! ATK " + hero.atk); }
            else if (s != 3) System.out.println("Not enough gold.");
        } else if (c == 3) {
            elder(hero);
        }
    }

    static void elder(Hero hero) {
        if (hero.quest == 0) {
            System.out.println("Elder: \"Embers are corrupting our land. Slay 5 monsters and I will reward you.\"");
            hero.quest = 1;
            hero.questKills = 0;
            System.out.println("[Quest accepted: Slay 5 monsters]");
        } else if (hero.quest == 1) {
            if (hero.questKills >= 5) {
                System.out.println("Elder: \"You did it! Take this.\"  [+50 XP, +2 potions]");
                hero.potions += 2;
                hero.quest = 2;
                if (hero.gainXp(50)) System.out.println("*** LEVEL UP! Level " + hero.level + " ***");
            } else {
                System.out.println("Elder: \"Keep going. Monsters slain: " + hero.questKills + "/5.\"");
            }
        } else {
            System.out.println("Elder: \"Defeat the Ember Lord in his lair, hero. Our hope is with you.\"");
        }
    }

    // ---------- Save / Load ----------

    static void save(Hero h) {
        Properties p = new Properties();
        p.setProperty("name", h.name);
        p.setProperty("hp", "" + h.hp);
        p.setProperty("maxHp", "" + h.maxHp);
        p.setProperty("atk", "" + h.atk);
        p.setProperty("def", "" + h.def);
        p.setProperty("level", "" + h.level);
        p.setProperty("xp", "" + h.xp);
        p.setProperty("potions", "" + h.potions);
        p.setProperty("gold", "" + h.gold);
        p.setProperty("kills", "" + h.kills);
        p.setProperty("quest", "" + h.quest);
        p.setProperty("questKills", "" + h.questKills);
        p.setProperty("area", "" + h.area);
        p.setProperty("heroClass", "" + h.heroClass);
        p.setProperty("weaponId", h.weaponId);
        p.setProperty("armorId", h.armorId);
        p.setProperty("owned", String.join(",", h.owned));
        p.setProperty("books", String.join(",", h.books));
        p.setProperty("bigPotions", "" + h.bigPotions);
        p.setProperty("ethers", "" + h.ethers);
        p.setProperty("amulets", "" + h.amulets);
        p.setProperty("charms", "" + h.charms);
        try (FileWriter w = new FileWriter(SAVE_FILE)) {
            p.store(w, "Kingdom of Embers save");
            System.out.println("Game saved.");
        } catch (IOException e) {
            System.out.println("Could not save: " + e.getMessage());
        }
    }

    /** Splits "a,b,c" into a list, ignoring blanks. */
    static List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) if (!part.trim().isEmpty()) out.add(part.trim());
        return out;
    }

    /** True if id is a real item that this hero's class may own (a book if wantBook, else weapon/armor). */
    static boolean ownable(Hero h, String id, boolean wantBook) {
        ShopItems.Item it = ShopItems.byId(id);
        if (it == null || it.heroClass != h.heroClass) return false;
        return wantBook ? it.kind == ShopItems.Kind.BOOK : ShopItems.isEquipment(it);
    }

    static Hero load() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) return null;
        Properties p = new Properties();
        try (FileReader r = new FileReader(f)) {
            p.load(r);
            Hero h = new Hero(p.getProperty("name"), Integer.parseInt(p.getProperty("heroClass", "0")));
            h.hp = Integer.parseInt(p.getProperty("hp"));
            h.maxHp = Integer.parseInt(p.getProperty("maxHp"));
            h.atk = Integer.parseInt(p.getProperty("atk"));
            h.def = Integer.parseInt(p.getProperty("def"));
            h.level = Integer.parseInt(p.getProperty("level"));
            h.xp = Integer.parseInt(p.getProperty("xp"));
            h.potions = Integer.parseInt(p.getProperty("potions"));
            h.gold = Integer.parseInt(p.getProperty("gold"));
            h.kills = Integer.parseInt(p.getProperty("kills"));
            h.quest = Integer.parseInt(p.getProperty("quest"));
            h.questKills = Integer.parseInt(p.getProperty("questKills"));
            h.area = Integer.parseInt(p.getProperty("area"));
            h.bigPotions = Integer.parseInt(p.getProperty("bigPotions", "0"));
            h.ethers = Integer.parseInt(p.getProperty("ethers", "0"));
            h.amulets = Integer.parseInt(p.getProperty("amulets", "0"));
            h.charms = Integer.parseInt(p.getProperty("charms", "0"));

            // Equipment and learned skills. ATK/DEF in the file already include the equipped gear's bonus.
            for (String id : csv(p.getProperty("owned", "")))
                if (ownable(h, id, false)) h.owned.add(id);
            for (String id : csv(p.getProperty("books", "")))
                if (ownable(h, id, true)) h.books.add(id);
            String w = p.getProperty("weaponId", ""), a = p.getProperty("armorId", "");
            if (ownable(h, w, false) && h.owned.contains(w) && ShopItems.byId(w).kind == ShopItems.Kind.WEAPON) h.weaponId = w;
            if (ownable(h, a, false) && h.owned.contains(a) && ShopItems.byId(a).kind == ShopItems.Kind.ARMOR) h.armorId = a;

            // Old save (from before class-specific gear): it has weaponTier/armorTier and no weaponId.
            if (!p.containsKey("weaponId")) {
                ShopItems.migrateOldGear(h, Integer.parseInt(p.getProperty("weaponTier", "0")),
                                            Integer.parseInt(p.getProperty("armorTier", "0")));
            }
            return h;
        } catch (Exception e) {
            System.out.println("Save file unreadable, starting new game.");
            return null;
        }
    }

    // ---------- Main loop ----------

    public static void main(String[] args) {
        System.out.println("=====================================");
        System.out.println("        KINGDOM OF EMBERS");
        System.out.println("=====================================");

        Hero hero = null;
        if (new File(SAVE_FILE).exists()) {
            System.out.println("Save found. 1) Continue  2) New game");
            if (ask("> ", 1, 2) == 1) hero = load();
        }
        if (hero == null) {
            System.out.print("Name your hero: ");
            String n = in.nextLine().trim();
            hero = new Hero(n.isEmpty() ? "Hero" : n);
            System.out.println("\nThe kingdom burns with a strange corruption. Find its source and end it.");
        }

        boolean running = true;
        while (running) {
            System.out.println("\n[" + AREA_NAMES[hero.area] + "]  " + hero.name + " Lv" + hero.level
                    + "  HP " + hero.hp + "/" + hero.maxHp + "  ATK " + hero.atk + "  DEF " + hero.def
                    + "  XP " + hero.xp + "/" + hero.xpToNext() + "  Gold " + hero.gold + "  Potions " + hero.potions);
            System.out.println("1) " + (hero.area == 0 ? "Village options" : "Explore")
                    + "  2) Travel  3) Save  4) Quit");
            int choice = ask("> ", 1, 4);

            if (choice == 1) {
                if (hero.area == 0) {
                    villageMenu(hero);
                } else {
                    Enemy e = spawn(hero.area);
                    int result = fight(hero, e);
                    if (result == -1) {
                        System.out.println("\nYou have fallen... You wake up in the village, minus half your gold.");
                        hero.gold /= 2;
                        hero.hp = hero.maxHp;
                        hero.area = 0;
                    } else if (result == 1 && e instanceof Boss) {
                        System.out.println("\n=====================================");
                        System.out.println(" The Ember Lord falls. The fires die.");
                        System.out.println(" The kingdom is saved. YOU WIN!");
                        System.out.println("=====================================");
                        running = false;
                    }
                }
            } else if (choice == 2) {
                System.out.println("Travel to:");
                for (int i = 0; i < AREA_NAMES.length; i++) {
                    System.out.println((i + 1) + ") " + AREA_NAMES[i] + " (recommended level " + AREA_MIN_LEVEL[i] + ")");
                }
                int dest = ask("> ", 1, AREA_NAMES.length) - 1;
                if (hero.level < AREA_MIN_LEVEL[dest]) {
                    System.out.println("The path is too dangerous. You need level " + AREA_MIN_LEVEL[dest] + ".");
                } else {
                    hero.area = dest;
                    System.out.println("You travel to " + AREA_NAMES[dest] + ".");
                }
            } else if (choice == 3) {
                save(hero);
            } else {
                running = false;
                System.out.println("Farewell, hero.");
            }
        }
    }
}
