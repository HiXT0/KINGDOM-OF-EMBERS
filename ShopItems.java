import java.util.*;

/**
 * Shop catalog and equipment rules for Kingdom of Embers. No GUI code in here.
 *
 * - Weapons and armor are class-specific (4 of each per class).
 * - Skill books are class-specific (3 per class) and teach a skill permanently.
 * - Consumables and permanent upgrades (potions, ether, amulet, charm) are for everyone.
 *
 * ATK and DEF bonuses of equipment are applied directly to hero.atk / hero.def, and removed again
 * when the item is swapped, so bonuses never stack. Max MP, MP regen, crit and dodge bonuses are
 * read from the equipped items on demand (see mpBonus, regenBonus, critBonus, dodgeBonus).
 */
final class ShopItems {

    enum Kind { WEAPON, ARMOR, BOOK, ITEM }

    static final class Item {
        final String id;
        final Kind kind;
        final int heroClass;                 // 0 Knight, 1 Mage, 2 Rogue, -1 = any class
        final String name, flavor;
        final int price, minLevel;
        final int atk, def, mp, regen, crit, dodge;   // equipment bonuses
        final int slot;                      // BOOK: 0..2, used in battle with Q, W, E
        final int mpCost;                    // BOOK: MP cost per cast
        final int max;                       // ITEM: most you can own (-1 = unlimited)

        Item(String id, Kind kind, int heroClass, String name, String flavor, int price, int minLevel,
             int atk, int def, int mp, int regen, int crit, int dodge, int slot, int mpCost, int max) {
            this.id = id; this.kind = kind; this.heroClass = heroClass; this.name = name; this.flavor = flavor;
            this.price = price; this.minLevel = minLevel;
            this.atk = atk; this.def = def; this.mp = mp; this.regen = regen; this.crit = crit; this.dodge = dodge;
            this.slot = slot; this.mpCost = mpCost; this.max = max;
        }

        /** "+3 ATK   +8 max MP ..." for equipment. */
        String bonusText() {
            List<String> b = new ArrayList<>();
            if (atk != 0)   b.add("+" + atk + " ATK");
            if (def != 0)   b.add("+" + def + " DEF");
            if (mp != 0)    b.add("+" + mp + " max MP");
            if (regen != 0) b.add("+" + regen + " MP regen per turn");
            if (crit != 0)  b.add("+" + crit + "% crit chance");
            if (dodge != 0) b.add("+" + dodge + "% dodge chance");
            return String.join("   ", b);
        }

        /** Full description shown in the shop window. */
        String description() {
            switch (kind) {
                case WEAPON:
                case ARMOR: return flavor + "\n\n" + bonusText();
                case BOOK:  return flavor + "\n\nMP cost: " + mpCost + "\nUse in battle with key " + "QWE".charAt(slot) + ".";
                default:    return flavor;
            }
        }
    }

    static final List<Item> ALL = new ArrayList<>();
    private static final Map<String, Item> BY_ID = new HashMap<>();

    private static void add(Item it) {
        if (BY_ID.put(it.id, it) != null) throw new IllegalStateException("duplicate item id " + it.id);
        ALL.add(it);
    }

    // equipment: id, class, name, flavor, price, minLevel, atk, def, mp, regen, crit, dodge
    private static void weapon(String id, int c, String name, String flavor, int price, int lv,
                               int atk, int mp, int regen, int crit) {
        add(new Item(id, Kind.WEAPON, c, name, flavor, price, lv, atk, 0, mp, regen, crit, 0, -1, 0, -1));
    }

    private static void armor(String id, int c, String name, String flavor, int price, int lv,
                              int def, int mp, int regen, int dodge) {
        add(new Item(id, Kind.ARMOR, c, name, flavor, price, lv, 0, def, mp, regen, 0, dodge, -1, 0, -1));
    }

    private static void book(String id, int c, int slot, String name, String flavor, int price, int lv, int mpCost) {
        add(new Item(id, Kind.BOOK, c, name, flavor, price, lv, 0, 0, 0, 0, 0, 0, slot, mpCost, -1));
    }

    private static void consumable(String id, String name, String flavor, int price, int max) {
        add(new Item(id, Kind.ITEM, -1, name, flavor, price, 1, 0, 0, 0, 0, 0, 0, -1, 0, max));
    }

    static {
        // ---------------- Knight: swords and heavy armor ----------------
        weapon("KW1", 0, "Iron Sword",        "A plain, reliable blade.",                              45, 1,  3, 0, 0, 0);
        weapon("KW2", 0, "Steel Blade",       "Well balanced and sharp.",                              95, 3,  5, 0, 0, 0);
        weapon("KW3", 0, "Knight's Claymore", "A heavy two-handed sword that cleaves through armor.", 180, 5,  8, 0, 0, 0);
        weapon("KW4", 0, "Ember Blade",       "Forged in embers. Still warm to the touch.",           320, 7, 11, 0, 0, 0);
        armor("KA1", 0, "Chain Shirt",  "Light mail rings over padded cloth.",       35, 1, 2, 0, 0, 0);
        armor("KA2", 0, "Chain Mail",   "Full chain mail, heavy but sturdy.",        85, 3, 4, 0, 0, 0);
        armor("KA3", 0, "Plate Armor",  "Steel plates that turn aside most blows.", 160, 5, 6, 0, 0, 0);
        armor("KA4", 0, "Dragon Plate", "Scaled plate with a faint magical hum.",   280, 7, 9, 4, 0, 0);

        // ---------------- Mage: staffs and robes ----------------
        weapon("MW1", 1, "Oak Staff",      "A simple staff that hums with a little mana.",       45, 1, 2,  8, 0, 0);
        weapon("MW2", 1, "Crystal Staff",  "A crystal tip focuses your spells.",                 95, 3, 3, 14, 1, 0);
        weapon("MW3", 1, "Arcane Staff",   "Etched with runes that feed your mana.",            180, 5, 5, 22, 1, 0);
        weapon("MW4", 1, "Staff of Embers","An ember burns inside its crown.",                  320, 7, 7, 32, 2, 0);
        armor("MA1", 1, "Apprentice Robe", "Thin cloth, but it holds a little mana.",           35, 1, 1,  4, 0, 0);
        armor("MA2", 1, "Silk Robe",       "Smooth silk woven with silver thread.",             85, 3, 1,  8, 0, 0);
        armor("MA3", 1, "Arcane Robe",     "Runes along the hem steady your casting.",         160, 5, 2, 12, 1, 0);
        armor("MA4", 1, "Archmage Robe",   "Worn by the greatest mages of the kingdom.",       280, 7, 3, 18, 1, 0);

        // ---------------- Rogue: daggers and light armor ----------------
        weapon("RW1", 2, "Iron Dagger",     "Quick and easy to hide.",                    45, 1, 3, 0, 0,  3);
        weapon("RW2", 2, "Fang Blade",      "A curved blade that finds weak spots.",      95, 3, 4, 0, 0,  6);
        weapon("RW3", 2, "Shadow Dagger",   "Dark steel that barely reflects light.",    180, 5, 6, 0, 0,  9);
        weapon("RW4", 2, "Nightfall Blades","A matched pair that strike like a whisper.",320, 7, 9, 0, 0, 12);
        armor("RA1", 2, "Cloth Wraps",   "Silent and light.",                            35, 1, 1, 0, 0,  3);
        armor("RA2", 2, "Leather Vest",  "Supple leather that lets you move freely.",    85, 3, 2, 0, 0,  5);
        armor("RA3", 2, "Shadow Garb",   "Dyed dark for fighting at night.",            160, 5, 2, 0, 0,  8);
        armor("RA4", 2, "Phantom Cloak", "Enemies swear they saw you somewhere else.",  280, 7, 3, 0, 0, 11);

        // ---------------- Skill books (3 per class, keys Q W E in battle) ----------------
        book("KB1", 0, 0, "Whirlwind",     "Spin and cut twice. Each cut can crit and half the enemy's defence is ignored.", 80, 3,  8);
        book("KB2", 0, 1, "Taunt",         "Goad the enemy into a reckless attack. Its next hit is halved and you counter for ATK + 4.", 140, 5, 6);
        book("KB3", 0, 2, "Holy Strike",   "A blessed blow that ignores defence and heals you for a quarter of the damage.", 230, 6, 18);
        book("MB1", 1, 0, "Frost Nova",    "Magic damage that ignores defence and freezes the enemy for a turn. The Ember Lord resists.", 80, 3, 12);
        book("MB2", 1, 1, "Lightning",     "A crackling bolt for strong, variable damage that ignores defence.", 140, 5, 12);
        book("MB3", 1, 2, "Arcane Shield", "A barrier that cuts the next two enemy attacks by 60%.", 230, 6, 14);
        book("RB1", 2, 0, "Smoke Bomb",    "Blinds the enemy for 3 turns. Each of its attacks has a 50% chance to miss.", 80, 3, 8);
        book("RB2", 2, 1, "Assassinate",   "A huge strike. Deals double damage to an enemy below 35% health.", 140, 5, 12);
        book("RB3", 2, 2, "Vampiric Strike","A fast cut that heals you for half the damage dealt.", 230, 6, 12);

        // ---------------- Consumables and permanent upgrades (same prices and effects as before) ----------------
        consumable("potion",     "Health Potion",   "Restores 30 HP.  Use with P (or 3 in battle).", 15, -1);
        consumable("bigpotion",  "Big Potion",      "Restores 60 HP.  Use with 9.",                  30, -1);
        consumable("ether",      "Ether",           "Restores 25 MP.  Use with 0.",                  25, -1);
        consumable("amulet",     "Vitality Amulet", "Permanently adds +15 max HP.  You can own up to 3.", 60, 3);
        consumable("charm",      "Mana Charm",      "Permanently adds +10 max MP.  You can own up to 3.", 70, 3);
    }

    // ---------- Lookups ----------

    static Item byId(String id) { return id == null ? null : BY_ID.get(id); }

    static List<Item> of(Kind kind, int heroClass) {
        List<Item> out = new ArrayList<>();
        for (Item it : ALL) if (it.kind == kind && (it.heroClass == heroClass || it.heroClass < 0)) out.add(it);
        return out;
    }

    /** Skill books of the other classes (shown greyed out in the shop). */
    static List<Item> booksOfOtherClasses(int heroClass) {
        List<Item> out = new ArrayList<>();
        for (Item it : ALL) if (it.kind == Kind.BOOK && it.heroClass != heroClass) out.add(it);
        return out;
    }

    static List<Item> consumables() { return of(Kind.ITEM, -1); }

    /** The skill book that belongs to a battle key slot (0 = Q, 1 = W, 2 = E) for a class. */
    static Item book(int heroClass, int slot) {
        for (Item it : ALL) if (it.kind == Kind.BOOK && it.heroClass == heroClass && it.slot == slot) return it;
        return null;
    }

    // ---------- State of an item for a hero ----------

    static boolean isEquipment(Item it) { return it.kind == Kind.WEAPON || it.kind == Kind.ARMOR; }

    static boolean isOwned(KingdomOfEmbers.Hero h, Item it) {
        if (it.kind == Kind.BOOK) return h.books.contains(it.id);
        if (isEquipment(it)) return h.owned.contains(it.id);
        return false;
    }

    static boolean isEquipped(KingdomOfEmbers.Hero h, Item it) {
        if (it.kind == Kind.WEAPON) return it.id.equals(h.weaponId);
        if (it.kind == Kind.ARMOR) return it.id.equals(h.armorId);
        return false;
    }

    /** How many of a consumable or upgrade the hero has. */
    static int count(KingdomOfEmbers.Hero h, Item it) {
        switch (it.id) {
            case "potion":    return h.potions;
            case "bigpotion": return h.bigPotions;
            case "ether":     return h.ethers;
            case "amulet":    return h.amulets;
            case "charm":     return h.charms;
            default:          return 0;
        }
    }

    /** Why the hero cannot buy this item right now, or null if they can. */
    static String reason(KingdomOfEmbers.Hero h, Item it) {
        if (it.heroClass >= 0 && it.heroClass != h.heroClass)
            return "Class: " + KingdomOfEmbers.Hero.CLASS_NAMES[it.heroClass] + " only";
        if (h.level < it.minLevel) return "Requires level " + it.minLevel;
        if (it.max > 0 && count(h, it) >= it.max) return "Maxed (" + it.max + " of " + it.max + ")";
        if (h.gold < it.price) return "Not enough gold";
        return null;
    }

    /** Short status word for the shop: Equipped, Owned, or the reason it cannot be bought, else "". */
    static String status(KingdomOfEmbers.Hero h, Item it) {
        if (isEquipped(h, it)) return "Equipped";
        if (isOwned(h, it)) return "Owned";
        String why = reason(h, it);
        return why == null ? "" : why;
    }

    // ---------- Equipment rules ----------

    /**
     * Equips an item (it must be a weapon or armor for the hero's class). The previous item in that slot
     * has its ATK/DEF bonus removed first, so bonuses never stack.
     */
    static void equip(KingdomOfEmbers.Hero h, Item it) {
        if (!isEquipment(it)) throw new IllegalArgumentException(it.id + " is not equipment");
        Item old = byId(it.kind == Kind.WEAPON ? h.weaponId : h.armorId);
        if (old != null) { h.atk -= old.atk; h.def -= old.def; }
        h.atk += it.atk;
        h.def += it.def;
        if (it.kind == Kind.WEAPON) h.weaponId = it.id; else h.armorId = it.id;
    }

    private static Item equipped(KingdomOfEmbers.Hero h, boolean weapon) {
        return byId(weapon ? h.weaponId : h.armorId);
    }

    static int mpBonus(KingdomOfEmbers.Hero h) {
        int v = 0;
        Item w = equipped(h, true), a = equipped(h, false);
        if (w != null) v += w.mp;
        if (a != null) v += a.mp;
        return v;
    }

    static int regenBonus(KingdomOfEmbers.Hero h) {
        int v = 0;
        Item w = equipped(h, true), a = equipped(h, false);
        if (w != null) v += w.regen;
        if (a != null) v += a.regen;
        return v;
    }

    static int critBonus(KingdomOfEmbers.Hero h) {
        int v = 0;
        Item w = equipped(h, true), a = equipped(h, false);
        if (w != null) v += w.crit;
        if (a != null) v += a.crit;
        return v;
    }

    static int dodgeBonus(KingdomOfEmbers.Hero h) {
        int v = 0;
        Item w = equipped(h, true), a = equipped(h, false);
        if (w != null) v += w.dodge;
        if (a != null) v += a.dodge;
        return v;
    }

    // ---------- Old saves ----------

    private static final int[] OLD_WEAPON_BONUS = { 3, 5, 8 };
    private static final int[] OLD_ARMOR_BONUS = { 2, 3, 5 };

    /**
     * Saves from before class-specific gear stored the old shop's weapon/armor tiers, and their bonuses
     * were added straight into ATK and DEF. This removes those old bonuses and gives the hero the matching
     * tier of gear for their class instead (same tier, now tracked and swappable).
     */
    static void migrateOldGear(KingdomOfEmbers.Hero h, int weaponTier, int armorTier) {
        for (int t = 0; t < Math.min(weaponTier, OLD_WEAPON_BONUS.length); t++) h.atk -= OLD_WEAPON_BONUS[t];
        for (int t = 0; t < Math.min(armorTier, OLD_ARMOR_BONUS.length); t++) h.def -= OLD_ARMOR_BONUS[t];
        grantTier(h, Kind.WEAPON, weaponTier);
        grantTier(h, Kind.ARMOR, armorTier);
    }

    private static void grantTier(KingdomOfEmbers.Hero h, Kind kind, int tier) {
        if (tier <= 0) return;
        List<Item> list = of(kind, h.heroClass);
        int top = Math.min(tier, list.size());
        for (int i = 0; i < top; i++) h.owned.add(list.get(i).id);
        equip(h, list.get(top - 1));
    }

    private ShopItems() {}
}
