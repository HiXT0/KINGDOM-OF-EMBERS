import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * Graphical front-end (Java Swing) for Kingdom of Embers.
 * Reuses Hero, Enemy, Wolf, Boss, spawn(), save() and load() from KingdomOfEmbers.java.
 * Both files must be in the same folder.
 */
public class KingdomOfEmbersGUI extends JPanel implements KeyListener {

    static final int T = 40, COLS = 20, ROWS = 14, W = COLS * T, MAP_H = ROWS * T, H = MAP_H + 90;
    static final Color[] FLOOR = {
        new Color(104, 168, 84), new Color(58, 116, 64), new Color(128, 128, 128),
        new Color(66, 56, 90), new Color(96, 36, 36) };
    static final Color[] WALL = {
        new Color(40, 110, 50), new Color(24, 70, 34), new Color(80, 80, 84),
        new Color(36, 28, 56), new Color(50, 16, 16) };

    enum Mode { SELECT, EXPLORE, BATTLE, WIN }

    KingdomOfEmbers.Hero hero = new KingdomOfEmbers.Hero("Hero");
    KingdomOfEmbers.Enemy enemy;
    Mode mode = Mode.SELECT;
    int selected = 0;          // class highlighted on the select screen
    String selectMsg = "";
    int[][] map;
    int px = 1, py = 1;
    boolean over, victory;

    // ---- Classes: Knight (0), Mage (1), Rogue (2). Each has 4 skills that unlock by level and cost MP ----
    static final int[] SKILL_LV = { 2, 3, 4, 6 };
    static final String[][] SKILL_NAME = {
        { "Shield Bash", "Iron Guard", "Second Wind", "Smite" },
        { "Fireball", "Heal", "Mana Shield", "Ember Storm" },
        { "Backstab", "Poison Blade", "Evade", "Shadow Flurry" } };
    static final String[][] SKILL_TAG = {
        { "hit + stun", "cuts next hit 2/3", "heals 35% HP", "heavy hit" },
        { "ignores defence", "heals 40% HP", "halves next hit", "huge hit" },
        { "big hit", "poison 3 turns", "dodge next hit", "3 strikes" } };
    static final int[][] SKILL_MP = { { 7, 6, 10, 16 }, { 6, 10, 7, 20 }, { 6, 8, 6, 16 } };
    static final int[] BASE_MP = { 15, 40, 25 };        // starting MP per class
    static final int[] MP_PER_LEVEL = { 4, 8, 5 };      // extra MP per level
    static final int[] HEAL_SLOT = { 2, 1, -1 };        // which skill heals (H key outside battle); -1 = none
    static final int[] HEAL_PCT = { 35, 40, 0 };        // % of max HP restored by that skill
    static final String[] ROLE = {
        "Tough fighter: high HP and defence", "Glass cannon: big MP, strong spells", "Quick striker: crits and dodges" };
    static final String[] PASSIVE = {
        "Power Strike recharges faster", "Arcane Flow: +2 extra MP per turn", "Nimble: 30% crits, 15% dodge" };

    int mp = 20;
    int guardMult = 100;       // % of the next hit that gets through (100 = no guard, 0 = full dodge)
    boolean stunned;           // enemy loses its next turn
    int poisonTurns, poisonDmg;

    int cls() { return hero.heroClass; }
    String skillName(int i) { return SKILL_NAME[cls()][i]; }
    int skillMp(int i) { return SKILL_MP[cls()][i]; }
    int maxMp() { return BASE_MP[cls()] + MP_PER_LEVEL[cls()] * (hero.level - 1); }
    int critChance() { return cls() == KingdomOfEmbers.Hero.ROGUE ? 30 : 15; }
    int mpRegen() { return cls() == KingdomOfEmbers.Hero.MAGE ? 4 : 2; }
    int dodgeChance() { return cls() == KingdomOfEmbers.Hero.ROGUE ? 15 : 0; }
    int specialRecharge() { return cls() == KingdomOfEmbers.Hero.KNIGHT ? 2 : 3; }
    int healAmount() { return hero.maxHp * HEAL_PCT[cls()] / 100; }
    int hitFlash, heroFlash, toastTimer;
    String toast = "";
    final List<String> log = new ArrayList<>();
    final Random rnd = new Random();

    /** A visible monster standing on the map. */
    static class Mob {
        int x, y;
        KingdomOfEmbers.Enemy e;
        Mob(int x, int y, KingdomOfEmbers.Enemy e) { this.x = x; this.y = y; this.e = e; }
    }
    final List<Mob> mobs = new ArrayList<>();
    Mob fighting;
    int lastX = 1, lastY = 1;
    static final int ELDER_X = 7, ELDER_Y = 4;

    KingdomOfEmbersGUI() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        buildMap();
        new javax.swing.Timer(33, e -> {
            if (toastTimer > 0) toastTimer--;
            if (hitFlash > 0) hitFlash--;
            if (heroFlash > 0) heroFlash--;
            repaint();
        }).start();
    }

    // ---------- Game logic ----------

    void toast(String s) { toast = s; toastTimer = 120; }

    /** Runs an action after a short delay (so sounds do not play on top of each other). */
    void later(int ms, Runnable r) {
        javax.swing.Timer t = new javax.swing.Timer(ms, ev -> r.run());
        t.setRepeats(false);
        t.start();
    }

    void log(String s) {
        log.add(s);
        while (log.size() > 5) log.remove(0);
    }

    void buildMap() {
        Random r = new Random(hero.area * 31L + 7);
        map = new int[ROWS][COLS];
        for (int y = 0; y < ROWS; y++)
            for (int x = 0; x < COLS; x++)
                map[y][x] = (x == 0 || y == 0 || x == COLS - 1 || y == ROWS - 1 || r.nextInt(100) < 10) ? 1 : 0;
        for (int dy = 0; dy < 3; dy++)
            for (int dx = 0; dx < 3; dx++) map[1 + dy][1 + dx] = 0;
        px = 1; py = 1; lastX = 1; lastY = 1;
        mobs.clear();
        if (hero.area == 0) map[ELDER_Y][ELDER_X] = 0;
        else if (hero.area == 4) spawnMob();
        else for (int i = 0; i < 6; i++) spawnMob();
    }

    Mob mobAt(int x, int y) {
        for (Mob m : mobs) if (m.x == x && m.y == y) return m;
        return null;
    }

    void spawnMob() {
        for (int tries = 0; tries < 200; tries++) {
            int x = 1 + rnd.nextInt(COLS - 2), y = 1 + rnd.nextInt(ROWS - 2);
            if (map[y][x] == 0 && Math.abs(x - px) + Math.abs(y - py) > 6 && mobAt(x, y) == null) {
                mobs.add(new Mob(x, y, KingdomOfEmbers.spawn(hero.area)));
                return;
            }
        }
    }

    /** Monsters wander, and chase the hero when close. Touching the hero starts a battle. */
    void moveMobs() {
        for (Mob m : mobs) {
            int dx = 0, dy = 0;
            int dist = Math.abs(m.x - px) + Math.abs(m.y - py);
            if (dist <= 5 && rnd.nextInt(100) < 60) {
                if (Math.abs(px - m.x) >= Math.abs(py - m.y)) dx = Integer.signum(px - m.x);
                else dy = Integer.signum(py - m.y);
            } else if (rnd.nextInt(100) < 40) {
                int d = rnd.nextInt(4);
                dx = d == 0 ? -1 : d == 1 ? 1 : 0;
                dy = d == 2 ? -1 : d == 3 ? 1 : 0;
            }
            int nx = m.x + dx, ny = m.y + dy;
            if ((dx != 0 || dy != 0) && map[ny][nx] == 0 && mobAt(nx, ny) == null) {
                m.x = nx; m.y = ny;
                if (m.x == px && m.y == py) { startBattle(m); return; }
            }
        }
    }

    void exploreKey(int k) {
        int dx = 0, dy = 0;
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) dx = -1;
        else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) dx = 1;
        else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) dy = -1;
        else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) dy = 1;

        if (dx != 0 || dy != 0) {
            int nx = px + dx, ny = py + dy;
            if (hero.area == 0 && nx == ELDER_X && ny == ELDER_Y) { elder(); return; }
            if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS && map[ny][nx] == 0) {
                lastX = px; lastY = py;
                px = nx; py = ny;
                Mob hit = mobAt(px, py);
                if (hit != null) { startBattle(hit); return; }
                moveMobs();
            }
            return;
        }

        if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_5) {
            int dest = k - KeyEvent.VK_1;
            if (hero.level < KingdomOfEmbers.AREA_MIN_LEVEL[dest]) {
                SoundFx.deny(); toast("Too dangerous! Need level " + KingdomOfEmbers.AREA_MIN_LEVEL[dest] + ".");
            } else {
                hero.area = dest;
                buildMap();
                SoundFx.click(); toast("Travelled to " + KingdomOfEmbers.AREA_NAMES[dest] + ".");
            }
        } else if (k == KeyEvent.VK_H) {
            int hs = HEAL_SLOT[cls()];
            if (hs < 0) { SoundFx.deny(); toast("The " + hero.className() + " has no healing spell. Press P for a potion."); }
            else if (hero.level < SKILL_LV[hs]) { SoundFx.deny(); toast(skillName(hs) + " unlocks at level " + SKILL_LV[hs] + "."); }
            else if (mp < skillMp(hs)) { SoundFx.deny(); toast("Not enough MP."); }
            else if (hero.hp >= hero.maxHp) { SoundFx.deny(); toast("You are already at full health."); }
            else { mp -= skillMp(hs); hero.heal(healAmount()); SoundFx.heal(); toast(skillName(hs) + " cast!"); }
        } else if (k == KeyEvent.VK_P) {
            if (hero.potions > 0 && hero.hp < hero.maxHp) {
                hero.potions--; hero.heal(30); SoundFx.heal(); toast("You drink a potion.");
            } else { SoundFx.deny(); toast("Can't use a potion now."); }
        } else if (k == KeyEvent.VK_F5 || k == KeyEvent.VK_K) {
            KingdomOfEmbers.save(hero); SoundFx.save(); toast("Game saved.");
        } else if (k == KeyEvent.VK_L) {
            KingdomOfEmbers.Hero h = KingdomOfEmbers.load();
            if (h != null) { hero = h; mp = maxMp(); buildMap(); SoundFx.save(); toast("Game loaded."); } else { SoundFx.deny(); toast("No save found."); }
        } else if (hero.area == 0) {
            if (k == KeyEvent.VK_R) { hero.hp = hero.maxHp; mp = maxMp(); SoundFx.heal(); toast("You rest at the inn. HP and MP restored!"); }
            else if (k == KeyEvent.VK_B) {
                if (hero.gold >= 15) { hero.gold -= 15; hero.potions++; SoundFx.coin(); toast("Bought a potion."); }
                else { SoundFx.deny(); toast("Not enough gold (15)."); }
            } else if (k == KeyEvent.VK_U) {
                if (hero.gold >= 40) { hero.gold -= 40; hero.atk += 3; SoundFx.coin(); toast("Weapon upgraded! ATK " + hero.atk); }
                else { SoundFx.deny(); toast("Not enough gold (40)."); }
            } else if (k == KeyEvent.VK_E) {
                if (Math.abs(px - ELDER_X) <= 1 && Math.abs(py - ELDER_Y) <= 1) elder();
                else { SoundFx.deny(); toast("Walk next to the Elder first."); }
            }
        }
    }

    void elder() {
        if (hero.quest == 0) {
            hero.quest = 1; hero.questKills = 0;
            toast("Elder: Slay 5 monsters and I will reward you. [Quest accepted]");
        } else if (hero.quest == 1) {
            if (hero.questKills >= 5) {
                hero.potions += 2; hero.quest = 2;
                boolean up = hero.gainXp(50);
                if (up) mp = maxMp();
                if (up) SoundFx.levelUp(); else SoundFx.victory(); toast("Quest complete! +50 XP, +2 potions" + (up ? " LEVEL UP!" : ""));
            } else toast("Elder: Keep going. Slain " + hero.questKills + "/5.");
        } else toast("Elder: Defeat the Ember Lord in his lair, hero!");
    }

    void startBattle(Mob m) {
        fighting = m;
        enemy = m.e;
        log.clear();
        log("A wild " + enemy.name + " appears!");
        hero.specialCooldown = 0;
        guardMult = 100; stunned = false; poisonTurns = 0;
        over = false; victory = false;
        SoundFx.encounter(); mode = Mode.BATTLE;
    }

    void battleKey(int k) {
        if (over) { endBattle(); return; }
        boolean used = false;
        if (k == KeyEvent.VK_1) {
            strike(1.0); used = true;
        } else if (k == KeyEvent.VK_2) {
            if (hero.specialCooldown > 0) { SoundFx.deny(); log("Special is recharging."); }
            else { strike(2.0); hero.specialCooldown = specialRecharge(); used = true; }
        } else if (k == KeyEvent.VK_3) {
            if (hero.potions <= 0) { SoundFx.deny(); log("No potions left!"); }
            else {
                hero.potions--;
                int before = hero.hp;
                hero.heal(30);
                SoundFx.heal(); log("You drink a potion: +" + (hero.hp - before) + " HP.");
                used = true;
            }
        } else if (k == KeyEvent.VK_4) {
            if (enemy instanceof KingdomOfEmbers.Boss) { SoundFx.deny(); log("You cannot flee from the Ember Lord!"); used = true; }
            else if (rnd.nextInt(100) < 50) { SoundFx.flee(); toast("You escaped!"); px = lastX; py = lastY; mode = Mode.EXPLORE; return; }
            else { SoundFx.miss(); log("Couldn't get away!"); used = true; }
        } else if (k >= KeyEvent.VK_5 && k <= KeyEvent.VK_8) {
            used = useSkill(k - KeyEvent.VK_5);
        }
        if (!used) return;
        mp = Math.min(maxMp(), mp + mpRegen());
        if (hero.specialCooldown > 0 && k != KeyEvent.VK_2) hero.specialCooldown--;
        if (!over && enemy.alive() && poisonTurns > 0) {
            enemy.takeDamage(poisonDmg);
            poisonTurns--;
            hitFlash = 6;
            log("Poison burns the " + enemy.name + " for " + poisonDmg + ".");
            if (!enemy.alive()) win();
        }
        if (!over && enemy.alive()) enemyTurn();
    }

    /** Returns true if the skill was cast (and so uses the turn). */
    boolean useSkill(int i) {
        if (hero.level < SKILL_LV[i]) { SoundFx.deny(); log(skillName(i) + " unlocks at level " + SKILL_LV[i] + "."); return false; }
        if (mp < skillMp(i)) { SoundFx.deny(); log("Not enough MP for " + skillName(i) + "."); return false; }
        mp -= skillMp(i);
        switch (cls()) {
            case KingdomOfEmbers.Hero.KNIGHT: knightSkill(i); break;
            case KingdomOfEmbers.Hero.MAGE:   mageSkill(i);   break;
            default:                          rogueSkill(i);  break;
        }
        if (!enemy.alive()) win();
        return true;
    }

    void knightSkill(int i) {
        switch (i) {
            case 0: { // Shield Bash: hits (half defence counts) and stuns
                int dmg = Math.max(1, hero.attackRoll() + 4 - enemy.def / 2);
                enemy.takeDamage(dmg); hitFlash = 12; SoundFx.hit();
                if (enemy instanceof KingdomOfEmbers.Boss)
                    log("SHIELD BASH hits for " + dmg + ". The Ember Lord shrugs off the stun!");
                else { stunned = true; log("SHIELD BASH hits the " + enemy.name + " for " + dmg + " and stuns it!"); }
                break;
            }
            case 1: // Iron Guard
                guardMult = 34; SoundFx.guard();
                log("IRON GUARD: the next hit is cut by two thirds.");
                break;
            case 2: { // Second Wind
                int before = hero.hp;
                hero.heal(healAmount());
                SoundFx.heal(); log("SECOND WIND restores " + (hero.hp - before) + " HP.");
                break;
            }
            default: { // Smite
                int dmg = Math.max(1, hero.atk * 2 + 8 + rnd.nextInt(5) - enemy.def / 2);
                enemy.takeDamage(dmg); hitFlash = 12;
                SoundFx.cast(); log("SMITE strikes the " + enemy.name + " for " + dmg + "!");
            }
        }
    }

    void mageSkill(int i) {
        switch (i) {
            case 0: { // Fireball: ignores defence
                int dmg = hero.atk + 10 + rnd.nextInt(4);
                enemy.takeDamage(dmg); hitFlash = 12;
                SoundFx.cast(); log("FIREBALL burns the " + enemy.name + " for " + dmg + " (ignores defence).");
                break;
            }
            case 1: { // Heal
                int before = hero.hp;
                hero.heal(healAmount());
                SoundFx.heal(); log("HEAL restores " + (hero.hp - before) + " HP.");
                break;
            }
            case 2: // Mana Shield
                guardMult = 50; SoundFx.guard();
                log("MANA SHIELD: the next hit is halved.");
                break;
            default: { // Ember Storm: ignores defence
                int dmg = hero.atk * 2 + 14 + rnd.nextInt(6);
                enemy.takeDamage(dmg); hitFlash = 12;
                SoundFx.cast(); log("EMBER STORM engulfs the " + enemy.name + " for " + dmg + "!");
            }
        }
    }

    void rogueSkill(int i) {
        switch (i) {
            case 0: { // Backstab
                int dmg = Math.max(1, hero.attackRoll() * 2 + 3 - enemy.def);
                enemy.takeDamage(dmg); hitFlash = 12; SoundFx.crit();
                log("BACKSTAB pierces the " + enemy.name + " for " + dmg + "!");
                break;
            }
            case 1: { // Poison Blade: a stab plus poison for 3 turns (ignores defence)
                int dmg = Math.max(1, hero.attackRoll() - enemy.def);
                enemy.takeDamage(dmg); hitFlash = 12; SoundFx.hit();
                poisonTurns = 3; poisonDmg = 4 + hero.level;
                log("POISON BLADE hits for " + dmg + " and poisons the " + enemy.name + " (" + poisonDmg + " a turn).");
                break;
            }
            case 2: // Evade: the next attack misses completely
                guardMult = 0; SoundFx.guard();
                log("EVADE: you slip into the shadows. The next attack will miss.");
                break;
            default: { // Shadow Flurry: three quick strikes, each can crit
                int total = 0;
                StringBuilder sb = new StringBuilder();
                for (int n = 0; n < 3; n++) {
                    int raw = hero.attackRoll();
                    if (rnd.nextInt(100) < critChance()) raw *= 2;
                    int d = Math.max(1, raw - enemy.def);
                    total += d;
                    sb.append(n > 0 ? " + " : "").append(d);
                    enemy.takeDamage(d);
                }
                hitFlash = 12; SoundFx.crit();
                log("SHADOW FLURRY: " + sb + " = " + total + "!");
            }
        }
    }

    void strike(double mult) {
        int raw = hero.attackRoll();
        boolean crit = rnd.nextInt(100) < critChance();
        if (crit) raw *= 2;
        int dmg = Math.max(1, (int) (raw * mult) - enemy.def);
        enemy.takeDamage(dmg);
        hitFlash = 12;
        if (crit) SoundFx.crit(); else SoundFx.hit();
        log((mult > 1 ? "POWER STRIKE" : "You slash") + " for " + dmg + (crit ? " - CRITICAL HIT!" : "."));
        if (!enemy.alive()) win();
    }

    void enemyTurn() {
        if (stunned) { stunned = false; log("The " + enemy.name + " is stunned and loses its turn!"); return; }
        int raw = enemy.attackRoll();
        boolean fury = enemy instanceof KingdomOfEmbers.Boss && raw > enemy.atk + 2;
        if (guardMult == 0) {   // Rogue's Evade
            guardMult = 100; SoundFx.miss();
            log("The " + enemy.name + " swings at empty air!");
            return;
        }
        if (rnd.nextInt(100) < dodgeChance()) {   // Rogue passive (and dodge from gear)
            SoundFx.miss(); log("You nimbly dodge the " + enemy.name + "'s attack!");
            return;
        }
        int dmg = Math.max(1, raw - hero.def);
        if (guardMult < 100) { dmg = Math.max(1, dmg * guardMult / 100); guardMult = 100; log("Your guard softens the blow!"); }
        hero.takeDamage(dmg);
        heroFlash = 12; later(220, SoundFx::hurt);
        log((fury ? "EMBER FURY! " : "") + enemy.name + " hits you for " + dmg + ".");
        if (!hero.alive()) { later(500, SoundFx::defeat); log("You have fallen... (press any key)"); over = true; victory = false; }
    }

    void win() {
        hero.gold += enemy.goldReward;
        hero.kills++;
        if (hero.quest == 1) hero.questKills++;
        final boolean willLevel = hero.xp + enemy.xpReward >= hero.xpToNext();
        later(250, () -> { if (willLevel) SoundFx.levelUp(); else SoundFx.victory(); });
        log("Victory! +" + enemy.xpReward + " XP, +" + enemy.goldReward + " gold.");
        mp = Math.min(maxMp(), mp + 3);
        int oldLevel = hero.level;
        if (hero.gainXp(enemy.xpReward)) {
            mp = maxMp();
            log("LEVEL UP! You are now level " + hero.level + "!");
            for (int i = 0; i < SKILL_LV.length; i++)
                if (SKILL_LV[i] > oldLevel && SKILL_LV[i] <= hero.level) log("New skill unlocked: " + skillName(i) + "!");
        }
        if (rnd.nextInt(100) < 25) { hero.potions++; log("The enemy dropped a potion!"); }
        over = true; victory = true;
    }

    void endBattle() {
        if (!victory) {
            hero.gold /= 2; hero.hp = hero.maxHp; mp = maxMp(); hero.area = 0;
            buildMap(); mode = Mode.EXPLORE;
            toast("You wake in the village, minus half your gold.");
        } else if (enemy instanceof KingdomOfEmbers.Boss) mode = Mode.WIN;
        else {
            mobs.remove(fighting);
            spawnMob();
            mode = Mode.EXPLORE;
        }
    }

    // ---------- Input ----------

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_M) { SoundFx.toggleMuted(); toast(SoundFx.isMuted() ? "Sound off." : "Sound on."); repaint(); return; }
        if (mode == Mode.SELECT) { selectKey(k); repaint(); return; }
        if (mode == Mode.WIN) { if (k == KeyEvent.VK_ESCAPE) System.exit(0); return; }
        if (mode == Mode.BATTLE) battleKey(k); else exploreKey(k);
        repaint();
    }

    /** Class selection screen: pick Knight, Mage or Rogue, or load a saved game. */
    void selectKey(int k) {
        int n = KingdomOfEmbers.Hero.CLASS_NAMES.length;
        selectMsg = "";
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) { selected = (selected + n - 1) % n; SoundFx.click(); }
        else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) { selected = (selected + 1) % n; SoundFx.click(); }
        else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_3) { selected = k - KeyEvent.VK_1; SoundFx.click(); }
        else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) startGame(selected);
        else if (k == KeyEvent.VK_L) {
            KingdomOfEmbers.Hero h = KingdomOfEmbers.load();
            if (h != null) {
                hero = h; selected = h.heroClass; mp = maxMp(); buildMap();
                mode = Mode.EXPLORE; SoundFx.save(); toast("Game loaded.");
            } else { SoundFx.deny(); selectMsg = "No saved game found."; }
        }
    }

    void startGame(int heroClass) {
        hero = new KingdomOfEmbers.Hero("Hero", heroClass);
        mp = maxMp();
        buildMap();
        mode = Mode.EXPLORE;
        SoundFx.victory();
        toast("You are a " + hero.className() + ". Press 1-5 to travel. Touch a monster to fight; bump the Elder to talk.");
    }
    @Override public void keyReleased(KeyEvent e) { }
    @Override public void keyTyped(KeyEvent e) { }

    // ---------- Drawing ----------

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (mode == Mode.SELECT) drawSelect(g);
        else if (mode == Mode.EXPLORE) { drawMap(g); drawHud(g); }
        else if (mode == Mode.BATTLE) drawBattle(g);
        else drawWin(g);
    }

    void drawMap(Graphics2D g) {
        int a = hero.area;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                g.setColor(FLOOR[a]);
                g.fillRect(x * T, y * T, T, T);
                if ((x + y) % 2 == 0) { g.setColor(new Color(0, 0, 0, 18)); g.fillRect(x * T, y * T, T, T); }
                if (map[y][x] == 1) drawObstacle(g, x * T, y * T, a);
            }
        }
        if (a == 0) drawElder(g);
        for (Mob m : mobs) {
            boolean boss = m.e instanceof KingdomOfEmbers.Boss;
            drawEnemy(g, m.e.name, m.x * T - (boss ? 6 : 0), m.y * T - (boss ? 10 : 0), boss ? 1.3 : 1.0);
        }
        drawHero(g, cls(), px * T, py * T, 1.0);
        if (toastTimer > 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            int w = g.getFontMetrics().stringWidth(toast) + 24;
            g.setColor(new Color(0, 0, 0, 190));
            g.fillRoundRect((W - w) / 2, 10, w, 30, 14, 14);
            g.setColor(Color.WHITE);
            g.drawString(toast, (W - w) / 2 + 12, 31);
        }
    }

    void drawElder(Graphics2D g0) {
        Graphics2D k = (Graphics2D) g0.create();
        k.translate(ELDER_X * T, ELDER_Y * T);
        k.setColor(new Color(110, 60, 150)); k.fillRoundRect(9, 15, 22, 24, 8, 8);
        k.setColor(new Color(240, 200, 160)); k.fillOval(12, 3, 16, 16);
        k.setColor(Color.WHITE); k.fillOval(10, 14, 20, 10); k.fillArc(12, 0, 16, 12, 0, 180);
        k.setColor(new Color(140, 90, 40)); k.setStroke(new BasicStroke(3)); k.drawLine(34, 38, 34, 8);
        k.setColor(new Color(255, 220, 60)); k.fillOval(31, 4, 7, 7);
        boolean alert = hero.quest == 0 || (hero.quest == 1 && hero.questKills >= 5);
        if (alert) { k.setFont(new Font("SansSerif", Font.BOLD, 22)); k.drawString("!", 16, -2); }
        k.setFont(new Font("SansSerif", Font.BOLD, 11));
        k.setColor(Color.WHITE); k.drawString("Elder", 6, 52);
        k.dispose();
    }

    void drawObstacle(Graphics2D g, int x, int y, int a) {
        if (a <= 1) {
            g.setColor(new Color(90, 60, 30)); g.fillRect(x + 16, y + 24, 8, 14);
            g.setColor(WALL[a]); g.fillOval(x + 4, y + 2, 32, 30);
        } else {
            g.setColor(WALL[a]); g.fillRoundRect(x + 3, y + 6, 34, 30, 12, 12);
            g.setColor(new Color(255, 255, 255, 30)); g.fillRoundRect(x + 8, y + 10, 12, 8, 6, 6);
        }
    }

    void drawHud(Graphics2D g) {
        int y0 = MAP_H;
        g.setColor(new Color(24, 20, 30)); g.fillRect(0, y0, W, 90);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(255, 200, 90));
        g.drawString(KingdomOfEmbers.AREA_NAMES[hero.area] + "   |   " + hero.className() + " Lv " + hero.level, 14, y0 + 22);
        g.setColor(Color.WHITE);
        g.drawString("HP " + hero.hp + "/" + hero.maxHp, 14, y0 + 44);
        bar(g, 90, y0 + 32, 160, 12, (double) hero.hp / hero.maxHp, new Color(200, 50, 50));
        g.drawString("XP " + hero.xp + "/" + hero.xpToNext(), 270, y0 + 44);
        bar(g, 350, y0 + 32, 120, 12, (double) hero.xp / hero.xpToNext(), new Color(80, 140, 255));
        g.drawString("ATK " + hero.atk + "  DEF " + hero.def + "  Gold " + hero.gold + "  Potions " + hero.potions, 490, y0 + 44);
        String q = hero.quest == 0 ? "Quest: talk to the Elder" : hero.quest == 1 ? "Quest: slay " + hero.questKills + "/5" : "Quest complete";
        g.drawString(q, 14, y0 + 66);
        g.drawString("MP " + mp + "/" + maxMp(), 300, y0 + 66);
        bar(g, 380, y0 + 54, 120, 12, (double) mp / maxMp(), new Color(70, 120, 230));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(190, 190, 200));
        String help = "Move: arrows/WASD   1-5 Travel   P Potion"
            + (HEAL_SLOT[cls()] >= 0 ? "   H " + skillName(HEAL_SLOT[cls()]) : "") + "   K Save   L Load";
        if (hero.area == 0) help += "   |   R Rest   B Potion(15g)   U Weapon(40g)   E Elder";
        g.drawString(help, 14, y0 + 84);
    }

    void bar(Graphics2D g, int x, int y, int w, int h, double frac, Color c) {
        frac = Math.max(0, Math.min(1, frac));
        g.setColor(new Color(60, 60, 60)); g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(c); g.fillRoundRect(x, y, (int) (w * frac), h, 6, 6);
        g.setColor(Color.WHITE); g.drawRoundRect(x, y, w, h, 6, 6);
    }

    void drawBattle(Graphics2D g) {
        int a = hero.area, ground = 360;
        g.setPaint(new GradientPaint(0, 0, WALL[a].darker(), 0, ground, FLOOR[a].brighter()));
        g.fillRect(0, 0, W, ground);
        g.setColor(FLOOR[a].darker()); g.fillRect(0, ground, W, MAP_H + 90 - ground);

        boolean boss = enemy instanceof KingdomOfEmbers.Boss;
        double es = boss ? 5.5 : 4.0;
        int ox = hitFlash > 0 ? (hitFlash % 2 == 0 ? 8 : -8) : 0;
        int hx = heroFlash > 0 ? (heroFlash % 2 == 0 ? -8 : 8) : 0;
        if (!enemy.alive() && over) es = 0;
        drawHero(g, cls(), 110 + hx, ground - 160, 4.0);
        if (es > 0) drawEnemy(g, enemy.name, 520 + ox, ground - (int) (40 * es), es);

        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(Color.WHITE);
        g.drawString(hero.className() + "  Lv " + hero.level + (guardMult < 100 ? "   [guarded]" : ""), 40, 36);
        bar(g, 40, 44, 240, 16, (double) hero.hp / hero.maxHp, new Color(60, 190, 80));
        g.drawString(hero.hp + "/" + hero.maxHp, 290, 58);
        bar(g, 40, 66, 240, 10, (double) mp / maxMp(), new Color(70, 120, 230));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("MP " + mp + "/" + maxMp(), 290, 76);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(enemy.name + (stunned ? "  [stunned]" : "") + (poisonTurns > 0 ? "  [poisoned]" : ""), W - 320, 36);
        bar(g, W - 320, 44, 240, 16, (double) enemy.hp / enemy.maxHp, new Color(210, 60, 60));
        g.drawString(enemy.hp + "/" + enemy.maxHp, W - 70, 58);

        g.setColor(new Color(10, 10, 16, 230)); g.fillRect(0, 410, W, H - 410);
        g.setFont(new Font("Monospaced", Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        for (int i = 0; i < log.size(); i++) g.drawString(log.get(i), 20, 436 + i * 22);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(150, 200, 255));
        g.drawString("SKILLS", 540, 430);
        for (int i = 0; i < SKILL_LV.length; i++) {   // 4 skills (SKILL_NAME.length would be the number of classes)
            boolean locked = hero.level < SKILL_LV[i];
            boolean cant = !locked && mp < skillMp(i);
            g.setColor(locked || cant ? new Color(110, 110, 120) : Color.WHITE);
            String label = (5 + i) + " " + skillName(i) + "  " + skillMp(i) + " MP" + (locked ? "  (Lv " + SKILL_LV[i] + ")" : "");
            g.drawString(label, 540, 454 + i * 22);
        }
        g.setFont(new Font("Monospaced", Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(255, 200, 90));
        String menu = over ? "Press any key to continue"
            : "1 Attack    2 Special" + (hero.specialCooldown > 0 ? " (cd " + hero.specialCooldown + ")" : "")
              + "    3 Potion (" + hero.potions + ")    4 Flee";
        g.drawString(menu, 20, H - 14);
    }

    void drawWin(Graphics2D g) {
        g.setColor(Color.BLACK); g.fillRect(0, 0, W, H);
        g.setFont(new Font("Serif", Font.BOLD, 48));
        g.setColor(new Color(255, 200, 60));
        center(g, "THE EMBER LORD FALLS", 240);
        g.setFont(new Font("Serif", Font.PLAIN, 28));
        g.setColor(Color.WHITE);
        center(g, "The fires die. The kingdom is saved.", 300);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(Color.LIGHT_GRAY);
        center(g, hero.className() + "  |  Final level " + hero.level + "  |  Monsters defeated " + hero.kills + "     (Esc to quit)", 360);
    }

    void center(Graphics2D g, String s, int y) {
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    // ---------- Sprites (drawn with shapes in a 40x40 box, then scaled) ----------

    void drawKnight(Graphics2D g0, int x, int y, double s) {
        Graphics2D k = (Graphics2D) g0.create();
        k.translate(x, y); k.scale(s, s);
        k.setColor(new Color(60, 40, 30)); k.fillRect(13, 33, 5, 6); k.fillRect(22, 33, 5, 6);
        k.setColor(new Color(50, 90, 200)); k.fillRoundRect(10, 16, 20, 18, 6, 6);
        k.setColor(new Color(240, 200, 160)); k.fillOval(12, 3, 16, 16);
        k.setColor(Color.LIGHT_GRAY); k.fillArc(11, 1, 18, 14, 0, 180);
        k.setColor(Color.WHITE); k.setStroke(new BasicStroke(3));
        k.drawLine(32, 32, 38, 10);
        k.dispose();
    }

    void poly(Graphics2D k, int... p) {
        int n = p.length / 2;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) { xs[i] = p[2 * i]; ys[i] = p[2 * i + 1]; }
        k.fillPolygon(xs, ys, n);
    }

    void drawEnemy(Graphics2D g0, String name, int x, int y, double s) {
        Graphics2D k = (Graphics2D) g0.create();
        k.translate(x, y); k.scale(s, s);
        switch (name) {
            case "Goblin":
                k.setColor(new Color(90, 170, 60));
                k.fillRoundRect(8, 16, 24, 20, 8, 8);
                k.fillOval(8, 2, 24, 20);
                poly(k, 8, 10, 0, 4, 10, 14);
                poly(k, 32, 10, 40, 4, 30, 14);
                k.setColor(Color.RED); k.fillOval(14, 9, 4, 4); k.fillOval(23, 9, 4, 4);
                k.setColor(Color.BLACK); k.drawLine(16, 17, 24, 17);
                break;
            case "Wolf":
                k.setColor(new Color(130, 130, 140));
                k.fillOval(2, 14, 30, 16);
                k.fillOval(24, 8, 15, 14);
                poly(k, 27, 10, 29, 2, 32, 9);
                poly(k, 34, 9, 37, 3, 38, 10);
                k.fillRect(6, 26, 4, 11); k.fillRect(24, 26, 4, 11);
                k.setStroke(new BasicStroke(3)); k.drawLine(3, 18, -3, 10);
                k.setColor(Color.YELLOW); k.fillOval(31, 12, 3, 3);
                k.setColor(Color.BLACK); k.fillOval(37, 15, 3, 3);
                break;
            case "Skeleton Warrior":
                k.setColor(new Color(235, 235, 225));
                k.fillOval(10, 2, 20, 18);
                k.fillRect(14, 18, 12, 4);
                k.setStroke(new BasicStroke(2));
                k.drawLine(20, 22, 20, 36);
                for (int i = 0; i < 3; i++) k.drawLine(12, 25 + i * 4, 28, 25 + i * 4);
                k.drawLine(8, 24, 4, 34); k.drawLine(32, 24, 36, 34);
                k.drawLine(16, 36, 14, 40); k.drawLine(24, 36, 26, 40);
                k.setColor(Color.BLACK); k.fillOval(14, 8, 5, 6); k.fillOval(22, 8, 5, 6);
                break;
            case "Dark Knight":
                k.setColor(new Color(50, 50, 62)); k.fillRoundRect(8, 14, 24, 22, 4, 4);
                k.setColor(new Color(36, 36, 44)); k.fillRect(11, 34, 7, 6); k.fillRect(22, 34, 7, 6);
                k.setColor(new Color(70, 70, 84)); k.fillRoundRect(10, 1, 20, 16, 6, 6);
                k.fillOval(3, 12, 10, 8); k.fillOval(27, 12, 10, 8);
                k.setColor(new Color(220, 40, 40)); k.fillRect(14, 8, 12, 3);
                k.setColor(new Color(180, 180, 200)); k.setStroke(new BasicStroke(3)); k.drawLine(36, 36, 38, 6);
                break;
            default: // Ember Lord
                k.setColor(new Color(255, 140, 0));
                for (int i = 0; i < 5; i++) k.fillOval(2 + i * 7, 30, 10, 12);
                k.setColor(new Color(255, 220, 60));
                for (int i = 0; i < 4; i++) k.fillOval(6 + i * 8, 33, 6, 8);
                k.setColor(new Color(190, 40, 30)); k.fillRoundRect(6, 12, 28, 26, 10, 10);
                k.fillOval(10, 0, 20, 18);
                k.setColor(new Color(255, 160, 40));
                poly(k, 11, 6, 5, -4, 15, 3);
                poly(k, 29, 6, 35, -4, 25, 3);
                k.setColor(Color.YELLOW); k.fillOval(14, 7, 4, 4); k.fillOval(22, 7, 4, 4);
                break;
        }
        k.dispose();
    }
/** Draws the hero sprite for the given class (0 Knight, 1 Mage, 2 Rogue). */
    void drawHero(Graphics2D g0, int heroClass, int x, int y, double s) {
        if (heroClass == KingdomOfEmbers.Hero.KNIGHT) { drawKnight(g0, x, y, s); return; }
        Graphics2D k = (Graphics2D) g0.create();
        k.translate(x, y); k.scale(s, s);
        k.setColor(new Color(60, 40, 30)); k.fillRect(13, 33, 5, 6); k.fillRect(22, 33, 5, 6);
        if (heroClass == KingdomOfEmbers.Hero.MAGE) {
            k.setColor(new Color(110, 60, 190)); k.fillRoundRect(9, 16, 22, 20, 6, 6);
            k.setColor(new Color(240, 200, 160)); k.fillOval(12, 5, 16, 15);
            k.setColor(new Color(80, 40, 150)); poly(k, 8, 10, 20, -4, 32, 10); k.fillRect(8, 8, 24, 4);
            k.setColor(new Color(150, 100, 50)); k.setStroke(new BasicStroke(3)); k.drawLine(34, 36, 34, 6);
            k.setColor(new Color(255, 120, 40)); k.fillOval(30, 1, 8, 8);
        } else {
            k.setColor(new Color(40, 100, 70)); k.fillRoundRect(10, 16, 20, 18, 6, 6);
            k.setColor(new Color(30, 70, 50)); k.fillOval(10, 2, 20, 18);
            k.setColor(new Color(240, 200, 160)); k.fillOval(14, 7, 12, 10);
            k.setColor(new Color(200, 200, 215)); k.setStroke(new BasicStroke(2));
            k.drawLine(6, 30, 12, 22); k.drawLine(34, 30, 28, 22);
        }
        k.dispose();
    }
 
    /** Class selection screen shown at the start. */
    void drawSelect(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(20, 10, 30), 0, H, new Color(90, 30, 20)));
        g.fillRect(0, 0, W, H);
        g.setFont(new Font("Serif", Font.BOLD, 46));
        g.setColor(new Color(255, 200, 60));
        center(g, "KINGDOM OF EMBERS", 70);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(Color.LIGHT_GRAY);
        center(g, "Choose your hero", 100);
        int n = KingdomOfEmbers.Hero.CLASS_NAMES.length;
        for (int i = 0; i < n; i++) {
            int cx = 60 + i * 240, cy = 120;
            boolean sel = i == selected;
            g.setColor(sel ? new Color(255, 255, 255, 40) : new Color(0, 0, 0, 90));
            g.fillRoundRect(cx, cy, 220, 390, 18, 18);
            g.setColor(sel ? new Color(255, 200, 60) : new Color(120, 120, 130));
            g.setStroke(new BasicStroke(sel ? 3 : 1));
            g.drawRoundRect(cx, cy, 220, 390, 18, 18);
            g.setStroke(new BasicStroke(1));
            drawHero(g, i, cx + 30, cy + 20, 4.0);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(Color.WHITE);
            g.drawString((i + 1) + ". " + KingdomOfEmbers.Hero.CLASS_NAMES[i], cx + 14, cy + 210);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(210, 210, 220));
            g.drawString(ROLE[i], cx + 10, cy + 232);
            g.setColor(new Color(150, 200, 255));
            g.drawString("Passive: " + PASSIVE[i], cx + 10, cy + 252);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(255, 200, 90));
            g.drawString("Skills", cx + 10, cy + 280);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(Color.WHITE);
            for (int j = 0; j < 4; j++)
                g.drawString("Lv " + SKILL_LV[j] + "  " + SKILL_NAME[i][j] + " - " + SKILL_TAG[i][j], cx + 10, cy + 302 + j * 20);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(255, 200, 90));
        center(g, "Left/Right or 1-3 to choose     Enter to start     L to load a saved game     M sound on/off", 560);
        if (!selectMsg.isEmpty()) { g.setColor(new Color(255, 120, 120)); center(g, selectMsg, 590); }
    }
    // ---------- Main ----------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Kingdom of Embers");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            KingdomOfEmbersGUI panel = new KingdomOfEmbersGUI();
            f.add(panel);
            f.pack();
            f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}