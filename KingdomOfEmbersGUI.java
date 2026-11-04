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

    enum Mode { EXPLORE, BATTLE, WIN }

    KingdomOfEmbers.Hero hero = new KingdomOfEmbers.Hero("Hero");
    KingdomOfEmbers.Enemy enemy;
    Mode mode = Mode.EXPLORE;
    int[][] map;
    int px = 1, py = 1;
    boolean over, victory;
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
        toast("Press 1-5 to travel. Touch a monster to fight; bump the Elder to talk.");
        new javax.swing.Timer(33, e -> {
            if (toastTimer > 0) toastTimer--;
            if (hitFlash > 0) hitFlash--;
            if (heroFlash > 0) heroFlash--;
            repaint();
        }).start();
    }

    // ---------- Game logic ----------

    void toast(String s) { toast = s; toastTimer = 120; }

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
                toast("Too dangerous! Need level " + KingdomOfEmbers.AREA_MIN_LEVEL[dest] + ".");
            } else {
                hero.area = dest;
                buildMap();
                toast("Travelled to " + KingdomOfEmbers.AREA_NAMES[dest] + ".");
            }
        } else if (k == KeyEvent.VK_P) {
            if (hero.potions > 0 && hero.hp < hero.maxHp) {
                hero.potions--; hero.heal(30); toast("You drink a potion.");
            } else toast("Can't use a potion now.");
        } else if (k == KeyEvent.VK_F5 || k == KeyEvent.VK_K) {
            KingdomOfEmbers.save(hero); toast("Game saved.");
        } else if (k == KeyEvent.VK_L) {
            KingdomOfEmbers.Hero h = KingdomOfEmbers.load();
            if (h != null) { hero = h; buildMap(); toast("Game loaded."); } else toast("No save found.");
        } else if (hero.area == 0) {
            if (k == KeyEvent.VK_R) { hero.hp = hero.maxHp; toast("You rest at the inn. Fully healed!"); }
            else if (k == KeyEvent.VK_B) {
                if (hero.gold >= 15) { hero.gold -= 15; hero.potions++; toast("Bought a potion."); }
                else toast("Not enough gold (15).");
            } else if (k == KeyEvent.VK_U) {
                if (hero.gold >= 40) { hero.gold -= 40; hero.atk += 3; toast("Weapon upgraded! ATK " + hero.atk); }
                else toast("Not enough gold (40).");
            } else if (k == KeyEvent.VK_E) {
                if (Math.abs(px - ELDER_X) <= 1 && Math.abs(py - ELDER_Y) <= 1) elder();
                else toast("Walk next to the Elder first.");
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
                toast("Quest complete! +50 XP, +2 potions" + (up ? " LEVEL UP!" : ""));
            } else toast("Elder: Keep going. Slain " + hero.questKills + "/5.");
        } else toast("Elder: Defeat the Ember Lord in his lair, hero!");
    }

    void startBattle(Mob m) {
        fighting = m;
        enemy = m.e;
        log.clear();
        log("A wild " + enemy.name + " appears!");
        hero.specialCooldown = 0;
        over = false; victory = false;
        mode = Mode.BATTLE;
    }

    void battleKey(int k) {
        if (over) { endBattle(); return; }
        boolean used = false;
        if (k == KeyEvent.VK_1) {
            strike(1.0); used = true;
        } else if (k == KeyEvent.VK_2) {
            if (hero.specialCooldown > 0) log("Special is recharging.");
            else { strike(2.0); hero.specialCooldown = 3; used = true; }
        } else if (k == KeyEvent.VK_3) {
            if (hero.potions <= 0) log("No potions left!");
            else {
                hero.potions--;
                int before = hero.hp;
                hero.heal(30);
                log("You drink a potion: +" + (hero.hp - before) + " HP.");
                used = true;
            }
        } else if (k == KeyEvent.VK_4) {
            if (enemy instanceof KingdomOfEmbers.Boss) { log("You cannot flee from the Ember Lord!"); used = true; }
            else if (rnd.nextInt(100) < 50) { toast("You escaped!"); px = lastX; py = lastY; mode = Mode.EXPLORE; return; }
            else { log("Couldn't get away!"); used = true; }
        }
        if (!used) return;
        if (hero.specialCooldown > 0 && k != KeyEvent.VK_2) hero.specialCooldown--;
        if (!over && enemy.alive()) enemyTurn();
    }

    void strike(double mult) {
        int raw = hero.attackRoll();
        boolean crit = rnd.nextInt(100) < 15;
        if (crit) raw *= 2;
        int dmg = Math.max(1, (int) (raw * mult) - enemy.def);
        enemy.takeDamage(dmg);
        hitFlash = 12;
        log((mult > 1 ? "POWER STRIKE" : "You slash") + " for " + dmg + (crit ? " - CRITICAL HIT!" : "."));
        if (!enemy.alive()) win();
    }

    void enemyTurn() {
        int raw = enemy.attackRoll();
        boolean fury = enemy instanceof KingdomOfEmbers.Boss && raw > enemy.atk + 2;
        int dmg = Math.max(1, raw - hero.def);
        hero.takeDamage(dmg);
        heroFlash = 12;
        log((fury ? "EMBER FURY! " : "") + enemy.name + " hits you for " + dmg + ".");
        if (!hero.alive()) { log("You have fallen... (press any key)"); over = true; victory = false; }
    }

    void win() {
        hero.gold += enemy.goldReward;
        hero.kills++;
        if (hero.quest == 1) hero.questKills++;
        log("Victory! +" + enemy.xpReward + " XP, +" + enemy.goldReward + " gold.");
        if (hero.gainXp(enemy.xpReward)) log("LEVEL UP! You are now level " + hero.level + "!");
        if (rnd.nextInt(100) < 25) { hero.potions++; log("The enemy dropped a potion!"); }
        over = true; victory = true;
    }

    void endBattle() {
        if (!victory) {
            hero.gold /= 2; hero.hp = hero.maxHp; hero.area = 0;
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
        if (mode == Mode.WIN) { if (k == KeyEvent.VK_ESCAPE) System.exit(0); return; }
        if (mode == Mode.BATTLE) battleKey(k); else exploreKey(k);
        repaint();
    }
    @Override public void keyReleased(KeyEvent e) { }
    @Override public void keyTyped(KeyEvent e) { }

    // ---------- Drawing ----------

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (mode == Mode.EXPLORE) { drawMap(g); drawHud(g); }
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
        drawKnight(g, px * T, py * T, 1.0);
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
        g.drawString(KingdomOfEmbers.AREA_NAMES[hero.area] + "   |   Lv " + hero.level, 14, y0 + 22);
        g.setColor(Color.WHITE);
        g.drawString("HP " + hero.hp + "/" + hero.maxHp, 14, y0 + 44);
        bar(g, 90, y0 + 32, 160, 12, (double) hero.hp / hero.maxHp, new Color(200, 50, 50));
        g.drawString("XP " + hero.xp + "/" + hero.xpToNext(), 270, y0 + 44);
        bar(g, 350, y0 + 32, 120, 12, (double) hero.xp / hero.xpToNext(), new Color(80, 140, 255));
        g.drawString("ATK " + hero.atk + "  DEF " + hero.def + "  Gold " + hero.gold + "  Potions " + hero.potions, 490, y0 + 44);
        String q = hero.quest == 0 ? "Quest: talk to the Elder" : hero.quest == 1 ? "Quest: slay " + hero.questKills + "/5" : "Quest complete";
        g.drawString(q, 14, y0 + 66);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(190, 190, 200));
        String help = "Move: arrows/WASD   1-5 Travel   P Potion   K Save   L Load";
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
        drawKnight(g, 110 + hx, ground - 160, 4.0);
        if (es > 0) drawEnemy(g, enemy.name, 520 + ox, ground - (int) (40 * es), es);

        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(Color.WHITE);
        g.drawString(hero.name + "  Lv " + hero.level, 40, 36);
        bar(g, 40, 44, 240, 16, (double) hero.hp / hero.maxHp, new Color(60, 190, 80));
        g.drawString(hero.hp + "/" + hero.maxHp, 290, 58);
        g.drawString(enemy.name, W - 320, 36);
        bar(g, W - 320, 44, 240, 16, (double) enemy.hp / enemy.maxHp, new Color(210, 60, 60));
        g.drawString(enemy.hp + "/" + enemy.maxHp, W - 70, 58);

        g.setColor(new Color(10, 10, 16, 230)); g.fillRect(0, 410, W, H - 410);
        g.setFont(new Font("Monospaced", Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        for (int i = 0; i < log.size(); i++) g.drawString(log.get(i), 20, 436 + i * 22);
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
        center(g, "Final level " + hero.level + "  |  Monsters defeated " + hero.kills + "     (Esc to quit)", 360);
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