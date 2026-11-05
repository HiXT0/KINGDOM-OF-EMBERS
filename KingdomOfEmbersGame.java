import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Kingdom of Embers with a Merchant, a separate shop window and book skills.
 * Extends KingdomOfEmbersGUI (inheritance), so almost nothing in that file needs to change.
 * Run THIS class:  java KingdomOfEmbersGame
 *
 * The shop itself is ShopWindow.java (a modal JDialog). What it sells is in ShopItems.java.
 * This class holds the buying rules (shopAction), the effects of gear, and the book skills in battle.
 */
public class KingdomOfEmbersGame extends KingdomOfEmbersGUI {

    static final int MERCH_X = 11, MERCH_Y = 4;

    /** Last message from the shop, and whether the last action worked (read by ShopWindow). */
    String shopMsg = "";
    boolean shopOk = false;

    // Battle effects from book skills (reset at the start of every battle)
    boolean counterReady;     // Taunt: counter-attack after the enemy's next hit
    int shieldCharges;        // Arcane Shield: enemy attacks still to be reduced
    int blindTurns;           // Smoke Bomb: enemy attacks that may miss

    // ---------- Gear bonuses (on top of the base class values in KingdomOfEmbersGUI) ----------

    @Override int maxMp() { return super.maxMp() + 10 * hero.charms + ShopItems.mpBonus(hero); }
    @Override int mpRegen() { return super.mpRegen() + ShopItems.regenBonus(hero); }
    @Override int critChance() { return super.critChance() + ShopItems.critBonus(hero); }
    @Override int dodgeChance() { return super.dodgeChance() + ShopItems.dodgeBonus(hero); }

    // ---------- Shop rules ----------

    /**
     * Buys, learns or equips an item. Returns true if something happened. Sets shopMsg and shopOk,
     * and plays a coin sound on success or a deny sound on a refused purchase.
     */
    boolean shopAction(ShopItems.Item it) {
        shopOk = false;
        if (ShopItems.isEquipped(hero, it)) {
            SoundFx.deny(); shopMsg = it.name + " is already equipped."; return false;
        }
        if (ShopItems.isOwned(hero, it)) {
            if (it.kind == ShopItems.Kind.BOOK) { SoundFx.deny(); shopMsg = "You already know " + it.name + "."; return false; }
            equipGear(it);                       // owned gear is free to re-equip
            SoundFx.click(); shopMsg = "Equipped " + it.name + "."; shopOk = true;
            return true;
        }
        String why = ShopItems.reason(hero, it);
        if (why != null) { SoundFx.deny(); shopMsg = why + "."; return false; }

        hero.gold -= it.price;
        SoundFx.coin();
        switch (it.kind) {
            case WEAPON:
            case ARMOR:
                hero.owned.add(it.id);
                equipGear(it);
                shopMsg = "Bought and equipped " + it.name + " for " + it.price + " gold!";
                break;
            case BOOK:
                hero.books.add(it.id);
                shopMsg = "You learned " + it.name + "! Use it in battle with " + "QWE".charAt(it.slot) + ".";
                break;
            default:
                buyConsumable(it);
                shopMsg = "Bought " + it.name + " for " + it.price + " gold!";
        }
        shopOk = true;
        return true;
    }

    /** Equips gear; the old item's ATK/DEF is removed first. Current MP follows the max MP change. */
    void equipGear(ShopItems.Item it) {
        int before = maxMp();
        ShopItems.equip(hero, it);
        int after = maxMp();
        mp = after > before ? Math.min(after, mp + (after - before)) : Math.min(mp, after);
    }

    void buyConsumable(ShopItems.Item it) {
        switch (it.id) {
            case "potion":    hero.potions++; break;
            case "bigpotion": hero.bigPotions++; break;
            case "ether":     hero.ethers++; break;
            case "amulet":    hero.maxHp += 15; hero.hp += 15; hero.amulets++; break;
            default:          hero.charms++; mp = Math.min(maxMp(), mp + 10);
        }
    }

    // ---------- Map: the Merchant ----------

    @Override void buildMap() {
        super.buildMap();
        if (hero.area == 0) {   // make sure the Merchant and the tiles around him are free
            map[MERCH_Y][MERCH_X] = 0;
            map[MERCH_Y][MERCH_X - 1] = 0; map[MERCH_Y][MERCH_X + 1] = 0;
            map[MERCH_Y - 1][MERCH_X] = 0; map[MERCH_Y + 1][MERCH_X] = 0;
        }
    }

    /** Opens the shop window. The game waits here until the window is closed. */
    void openShop() {
        SoundFx.click();
        ShopWindow.open(this);
        repaint();
    }

    // ---------- Input ----------

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (mode == Mode.EXPLORE) {
            if (hero.area == 0) {
                int dx = 0, dy = 0;
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) dx = -1;
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) dx = 1;
                else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) dy = -1;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) dy = 1;
                boolean bump = (dx != 0 || dy != 0) && px + dx == MERCH_X && py + dy == MERCH_Y;
                boolean near = Math.abs(px - MERCH_X) <= 1 && Math.abs(py - MERCH_Y) <= 1;
                if (bump || (near && k == KeyEvent.VK_E)) { openShop(); return; }
                if (k == KeyEvent.VK_B || k == KeyEvent.VK_U) {
                    if (near) openShop(); else { SoundFx.deny(); toast("Walk next to the Merchant to shop."); repaint(); }
                    return;
                }
            }
            if (k == KeyEvent.VK_9) { useBigPotion(); repaint(); return; }
            if (k == KeyEvent.VK_0) { useEther(); repaint(); return; }
        }

        if (mode == Mode.BATTLE && !over) {
            if (k == KeyEvent.VK_9 || k == KeyEvent.VK_0) {
                if (battleItem(k)) endOfPlayerTurn();
                repaint();
                return;
            }
            int slot = k == KeyEvent.VK_Q ? 0 : k == KeyEvent.VK_W ? 1 : k == KeyEvent.VK_E ? 2 : -1;
            if (slot >= 0) {                       // book skills: Q, W, E
                if (bookSkill(slot)) endOfPlayerTurn();
                repaint();
                return;
            }
        }

        super.keyPressed(e);
    }

    void useBigPotion() {
        if (hero.bigPotions <= 0) { SoundFx.deny(); toast("No big potions."); }
        else if (hero.hp >= hero.maxHp) { SoundFx.deny(); toast("You are already at full health."); }
        else { hero.bigPotions--; hero.heal(60); SoundFx.heal(); toast("You drink a big potion."); }
    }

    void useEther() {
        if (hero.ethers <= 0) { SoundFx.deny(); toast("No ethers."); }
        else if (mp >= maxMp()) { SoundFx.deny(); toast("Your MP is already full."); }
        else { hero.ethers--; mp = Math.min(maxMp(), mp + 25); SoundFx.heal(); toast("You drink an ether."); }
    }

    /** Uses an item in battle. Returns true if a turn was used. */
    boolean battleItem(int k) {
        if (k == KeyEvent.VK_9) {
            if (hero.bigPotions <= 0) { SoundFx.deny(); log("No big potions left!"); return false; }
            hero.bigPotions--;
            int before = hero.hp;
            hero.heal(60);
            SoundFx.heal(); log("Big potion: +" + (hero.hp - before) + " HP.");
            return true;
        }
        if (hero.ethers <= 0) { SoundFx.deny(); log("No ethers left!"); return false; }
        hero.ethers--;
        int before = mp;
        mp = Math.min(maxMp(), mp + 25);
        SoundFx.heal(); log("Ether: +" + (mp - before) + " MP.");
        return true;
    }

    /** Same end-of-turn steps as the normal battle: MP regen, cooldown, poison, enemy attack. */
    void endOfPlayerTurn() {
        mp = Math.min(maxMp(), mp + mpRegen());
        if (hero.specialCooldown > 0) hero.specialCooldown--;
        if (!over && enemy.alive() && poisonTurns > 0) {
            enemy.takeDamage(poisonDmg);
            poisonTurns--;
            hitFlash = 6;
            log("Poison burns the " + enemy.name + " for " + poisonDmg + ".");
            if (!enemy.alive()) win();
        }
        if (!over && enemy.alive()) enemyTurn();
    }

    // ---------- Book skills (learned in the shop, used with Q, W, E) ----------

    /** Casts the learned book skill in a slot. Returns true if it was cast (and so uses the turn). */
    boolean bookSkill(int slot) {
        ShopItems.Item b = ShopItems.book(hero.heroClass, slot);
        if (b == null || !hero.books.contains(b.id)) {
            SoundFx.deny(); log("No skill on key " + "QWE".charAt(slot) + " yet. Buy Skill Books from the Merchant.");
            return false;
        }
        if (mp < b.mpCost) { SoundFx.deny(); log("Not enough MP for " + b.name + "."); return false; }
        mp -= b.mpCost;
        switch (cls()) {
            case KingdomOfEmbers.Hero.KNIGHT: knightBook(slot); break;
            case KingdomOfEmbers.Hero.MAGE:   mageBook(slot);   break;
            default:                          rogueBook(slot);  break;
        }
        if (!enemy.alive()) win();
        return true;
    }

    void knightBook(int slot) {
        switch (slot) {
            case 0: { // Whirlwind: two cuts, each can crit, half the enemy's defence counts
                int total = 0;
                StringBuilder sb = new StringBuilder();
                for (int n = 0; n < 2; n++) {
                    int raw = (int) (hero.attackRoll() * 1.1);
                    if (rnd.nextInt(100) < critChance()) raw *= 2;
                    int d = Math.max(1, raw - enemy.def / 2);
                    total += d;
                    sb.append(n > 0 ? " + " : "").append(d);
                    enemy.takeDamage(d);
                }
                hitFlash = 12; SoundFx.hit();
                log("WHIRLWIND: " + sb + " = " + total + "!");
                break;
            }
            case 1: // Taunt: the next hit is halved and you counter-attack (see enemyTurn)
                guardMult = Math.min(guardMult, 50);
                counterReady = true;
                SoundFx.guard();
                log("TAUNT: the " + enemy.name + " lunges at you! Its next hit is halved and you will counter.");
                break;
            default: { // Holy Strike: ignores defence, heals you for a quarter of the damage
                int dmg = hero.atk * 2 + 12 + rnd.nextInt(5);
                enemy.takeDamage(dmg); hitFlash = 12;
                int before = hero.hp;
                hero.heal(dmg / 4);
                SoundFx.cast();
                log("HOLY STRIKE smites the " + enemy.name + " for " + dmg + " and heals you for " + (hero.hp - before) + ".");
            }
        }
    }

    void mageBook(int slot) {
        switch (slot) {
            case 0: { // Frost Nova: damage that ignores defence, and freezes the enemy for a turn
                int dmg = hero.atk + 6 + rnd.nextInt(4);
                enemy.takeDamage(dmg); hitFlash = 12; SoundFx.cast();
                if (enemy instanceof KingdomOfEmbers.Boss)
                    log("FROST NOVA hits for " + dmg + ". The Ember Lord resists the freeze!");
                else {
                    if (enemy.alive()) stunned = true;
                    log("FROST NOVA hits the " + enemy.name + " for " + dmg + " and freezes it!");
                }
                break;
            }
            case 1: { // Lightning: strong, variable damage that ignores defence
                int dmg = hero.atk * 3 / 2 + 8 + rnd.nextInt(9);
                enemy.takeDamage(dmg); hitFlash = 12; SoundFx.cast();
                log("LIGHTNING strikes the " + enemy.name + " for " + dmg + "!");
                break;
            }
            default: // Arcane Shield: the next two enemy attacks are cut by 60% (see enemyTurn)
                shieldCharges = 2;
                SoundFx.guard();
                log("ARCANE SHIELD: a barrier will weaken the next two attacks.");
        }
    }

    void rogueBook(int slot) {
        switch (slot) {
            case 0: // Smoke Bomb: the enemy's next 3 attacks each have a 50% chance to miss (see enemyTurn)
                blindTurns = 3;
                SoundFx.guard();
                log("SMOKE BOMB: the " + enemy.name + " is blinded for 3 turns.");
                break;
            case 1: { // Assassinate: huge hit, double damage on an enemy below 35% health
                int dmg = Math.max(1, hero.attackRoll() * 3 + 6 - enemy.def);
                boolean execute = enemy.hp * 100 <= enemy.maxHp * 35;
                if (execute) dmg *= 2;
                enemy.takeDamage(dmg); hitFlash = 12; SoundFx.crit();
                log("ASSASSINATE " + (execute ? "executes" : "stabs") + " the " + enemy.name + " for " + dmg + "!");
                break;
            }
            default: { // Vampiric Strike: heals you for half the damage dealt
                int dmg = Math.max(1, hero.attackRoll() * 2 - enemy.def);
                enemy.takeDamage(dmg); hitFlash = 12;
                int before = hero.hp;
                hero.heal(dmg / 2);
                SoundFx.hit();
                log("VAMPIRIC STRIKE drains the " + enemy.name + " for " + dmg + " and heals you for " + (hero.hp - before) + ".");
            }
        }
    }

    @Override void startBattle(Mob m) {
        super.startBattle(m);
        counterReady = false; shieldCharges = 0; blindTurns = 0;
    }

    /** Adds the effects of Smoke Bomb, Arcane Shield and Taunt around the normal enemy attack. */
    @Override void enemyTurn() {
        if (stunned) { super.enemyTurn(); return; }          // a stunned enemy just loses its turn
        if (blindTurns > 0) {
            blindTurns--;
            if (rnd.nextInt(100) < 50) {
                SoundFx.miss(); log("The " + enemy.name + " flails blindly and misses!");
                return;
            }
        }
        if (shieldCharges > 0 && guardMult > 40) { shieldCharges--; guardMult = 40; }
        boolean counter = counterReady;
        counterReady = false;
        super.enemyTurn();
        if (counter && !over && hero.alive() && enemy.alive()) {
            int d = Math.max(1, hero.atk + 4 - enemy.def);
            enemy.takeDamage(d); hitFlash = 12; SoundFx.hit();
            log("You counter for " + d + "!");
            if (!enemy.alive()) win();
        }
    }

    // ---------- Drawing ----------

    @Override void drawMap(Graphics2D g) {
        super.drawMap(g);
        if (hero.area == 0) drawMerchant(g);
    }

    @Override void drawHud(Graphics2D g) {
        super.drawHud(g);
        int y0 = MAP_H;
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.WHITE);
        g.drawString("Big " + hero.bigPotions + "  Ether " + hero.ethers, 520, y0 + 66);
        g.setColor(new Color(24, 20, 30)); g.fillRect(0, y0 + 72, W, 18);   // cover the old help line
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(190, 190, 200));
        String help = "Move: arrows/WASD   1-5 Travel   P/9/0 Potion/Big/Ether"
            + (HEAL_SLOT[cls()] >= 0 ? "   H " + skillName(HEAL_SLOT[cls()]) : "") + "   K Save   L Load";
        if (hero.area == 0) help += "   |   R Rest   E Elder/Shop";
        g.drawString(help, 14, y0 + 84);
    }

    @Override void drawBattle(Graphics2D g) {
        super.drawBattle(g);
        if (over) return;
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(255, 200, 90));
        g.drawString("9 Big Potion (" + hero.bigPotions + ")      0 Ether (" + hero.ethers + ")", 20, H - 38);

        // learned book skills under the normal skill list
        boolean header = false;
        int row = 0;
        for (int slot = 0; slot < 3; slot++) {
            ShopItems.Item b = ShopItems.book(hero.heroClass, slot);
            if (b == null || !hero.books.contains(b.id)) continue;
            if (!header) {
                g.setColor(new Color(150, 200, 255));
                g.drawString("BOOK SKILLS", 540, 548);
                header = true;
            }
            g.setColor(mp < b.mpCost ? new Color(110, 110, 120) : Color.WHITE);
            g.drawString("QWE".charAt(slot) + " " + b.name + "  " + b.mpCost + " MP", 540, 570 + row * 22);
            row++;
        }

        // effects from book skills
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(new Color(200, 230, 255));
        if (shieldCharges > 0) g.drawString("[Arcane Shield x" + shieldCharges + "]", 40, 98);
        g.setColor(new Color(255, 230, 150));
        if (blindTurns > 0) g.drawString("[blinded x" + blindTurns + "]", W - 320, 80);
    }

    void drawMerchant(Graphics2D g0) {
        Graphics2D k = (Graphics2D) g0.create();
        k.translate(MERCH_X * T, MERCH_Y * T);
        k.setColor(new Color(60, 120, 70)); k.fillRoundRect(9, 15, 22, 24, 8, 8);
        k.setColor(new Color(240, 200, 160)); k.fillOval(12, 4, 16, 15);
        k.setColor(new Color(120, 70, 30)); k.fillRoundRect(8, 1, 24, 7, 6, 6); k.fillRect(13, -2, 14, 5);
        k.setColor(new Color(170, 120, 60)); k.fillOval(26, 20, 13, 16);
        k.setFont(new Font("SansSerif", Font.BOLD, 11));
        k.setColor(new Color(255, 220, 60)); k.drawString("$", 29, 32);
        k.setColor(Color.WHITE); k.drawString("Shop", 8, 52);
        k.dispose();
    }

    // ---------- Main ----------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Kingdom of Embers");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            KingdomOfEmbersGame panel = new KingdomOfEmbersGame();
            f.add(panel);
            f.pack();
            f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
