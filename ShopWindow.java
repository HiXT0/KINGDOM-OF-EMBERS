import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * The Merchant's shop: a separate modal window (JDialog) made of normal Swing components.
 * The game is paused while it is open (the dialog blocks input to the game window) and the game
 * panel gets keyboard focus back when it closes.
 *
 * Buying logic lives in KingdomOfEmbersGame.shopAction(); this class only shows items and
 * forwards clicks. The content is in ShopPanel so it can also be created without opening a window.
 */
public class ShopWindow extends JDialog {

    private static boolean lookSet = false;

    /** Opens the shop, waits until it is closed, then gives keyboard focus back to the game. */
    static void open(KingdomOfEmbersGame game) {
        if (!lookSet) {
            lookSet = true;
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        }
        Window owner = SwingUtilities.getWindowAncestor(game);
        ShopWindow w = new ShopWindow(owner, game);
        w.setVisible(true);                       // modal: returns when the window is closed
        game.requestFocusInWindow();
        SwingUtilities.invokeLater(game::requestFocusInWindow);
    }

    private ShopWindow(Window owner, KingdomOfEmbersGame game) {
        super(owner, "Merchant's Wares", Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(new ShopPanel(game, this::dispose));
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { game.requestFocusInWindow(); }
        });
    }
}

/** The shop's contents: hero summary on top, four tabs, a message line and a Close button. */
class ShopPanel extends JPanel {

    private static final Color GOOD = new Color(30, 130, 50), BAD = new Color(190, 40, 40),
            MUTED = new Color(120, 120, 130), OWNED = new Color(40, 90, 180), GOLD = new Color(170, 120, 0);

    private final KingdomOfEmbersGame game;
    private final JLabel goldL = statLabel(), classL = statLabel(), atkL = statLabel(),
            defL = statLabel(), hpL = statLabel(), mpL = statLabel();
    private final JLabel msgL = new JLabel(" ");
    private final List<ItemTab> tabs = new ArrayList<>();

    ShopPanel(KingdomOfEmbersGame game, Runnable onClose) {
        this.game = game;
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel header = new JPanel(new GridLayout(1, 6, 10, 0));
        header.setBorder(new MatteBorder(0, 0, 1, 0, MUTED));
        for (JLabel l : new JLabel[] { goldL, classL, atkL, defL, hpL, mpL }) header.add(l);
        goldL.setForeground(GOLD);
        add(header, BorderLayout.NORTH);

        JTabbedPane pane = new JTabbedPane();
        addTab(pane, "Weapons", ShopItems.Kind.WEAPON);
        addTab(pane, "Armor", ShopItems.Kind.ARMOR);
        addTab(pane, "Skill Books", ShopItems.Kind.BOOK);
        addTab(pane, "Items", ShopItems.Kind.ITEM);
        add(pane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        msgL.setFont(msgL.getFont().deriveFont(Font.BOLD, 13f));
        JButton close = new JButton("Close (Esc)");
        close.addActionListener(e -> onClose.run());
        bottom.add(msgL, BorderLayout.CENTER);
        bottom.add(close, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(800, 540));
        msgL.setText("Welcome, traveller! What can I get you?");
        msgL.setForeground(MUTED);
        refresh();
    }

    private void addTab(JTabbedPane pane, String title, ShopItems.Kind kind) {
        ItemTab t = new ItemTab(kind);
        tabs.add(t);
        pane.addTab(title, t);
    }

    private static JLabel statLabel() {
        JLabel l = new JLabel(" ", SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 14f));
        return l;
    }

    /** Updates the hero summary and every tab. */
    void refresh() {
        KingdomOfEmbers.Hero h = game.hero;
        goldL.setText("Gold " + h.gold);
        classL.setText(h.className() + " Lv " + h.level);
        atkL.setText("ATK " + h.atk);
        defL.setText("DEF " + h.def);
        hpL.setText("HP " + h.hp + "/" + h.maxHp);
        mpL.setText("MP " + game.mp + "/" + game.maxMp());
        for (ItemTab t : tabs) t.refresh();
    }

    /** One tab: a list of items on the left, details and a Buy button on the right. */
    private class ItemTab extends JPanel {
        final ShopItems.Kind kind;
        final DefaultListModel<ShopItems.Item> model = new DefaultListModel<>();
        final JList<ShopItems.Item> list = new JList<>(model);
        final JLabel nameL = new JLabel(" "), metaL = new JLabel(" "), priceL = new JLabel(" "), statusL = new JLabel(" ");
        final JTextArea descA = new JTextArea();
        final JButton buyB = new JButton("Buy");

        ItemTab(ShopItems.Kind kind) {
            this.kind = kind;
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(10, 4, 4, 4));

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setFixedCellHeight(30);
            list.setCellRenderer((l, it, index, selected, focus) -> row(l, it, selected));
            list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDetails(); });
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) act(); }
            });
            JScrollPane sp = new JScrollPane(list);
            sp.setPreferredSize(new Dimension(320, 100));
            add(sp, BorderLayout.WEST);

            JPanel d = new JPanel();
            d.setLayout(new BoxLayout(d, BoxLayout.Y_AXIS));
            nameL.setFont(nameL.getFont().deriveFont(Font.BOLD, 20f));
            metaL.setForeground(MUTED);
            descA.setEditable(false);
            descA.setLineWrap(true);
            descA.setWrapStyleWord(true);
            descA.setOpaque(false);
            descA.setFont(descA.getFont().deriveFont(14f));
            descA.setFocusable(false);
            priceL.setFont(priceL.getFont().deriveFont(Font.BOLD, 15f));
            statusL.setFont(statusL.getFont().deriveFont(Font.BOLD, 15f));
            buyB.setFont(buyB.getFont().deriveFont(Font.BOLD, 15f));
            buyB.addActionListener(e -> act());
            for (JComponent c : new JComponent[] { nameL, metaL, descA, priceL, statusL, buyB }) c.setAlignmentX(LEFT_ALIGNMENT);
            d.add(nameL);
            d.add(Box.createVerticalStrut(2));
            d.add(metaL);
            d.add(Box.createVerticalStrut(12));
            d.add(descA);
            d.add(Box.createVerticalStrut(12));
            d.add(priceL);
            d.add(Box.createVerticalStrut(4));
            d.add(statusL);
            d.add(Box.createVerticalStrut(14));
            d.add(buyB);
            d.add(Box.createVerticalGlue());
            add(d, BorderLayout.CENTER);
        }

        /** The items this tab shows for the current hero. */
        List<ShopItems.Item> stock() {
            int c = game.hero.heroClass;
            List<ShopItems.Item> out = new ArrayList<>(ShopItems.of(kind, c));
            if (kind == ShopItems.Kind.BOOK) out.addAll(ShopItems.booksOfOtherClasses(c));   // shown greyed out
            return out;
        }

        void refresh() {
            ShopItems.Item sel = list.getSelectedValue();
            int idx = list.getSelectedIndex();
            model.clear();
            for (ShopItems.Item it : stock()) model.addElement(it);
            if (sel != null && model.contains(sel)) list.setSelectedValue(sel, true);
            else if (!model.isEmpty()) list.setSelectedIndex(Math.max(0, Math.min(idx, model.size() - 1)));
            showDetails();
        }

        private Component row(JList<? extends ShopItems.Item> l, ShopItems.Item it, boolean selected) {
            KingdomOfEmbers.Hero h = game.hero;
            JPanel p = new JPanel(new BorderLayout(8, 0));
            p.setBorder(new EmptyBorder(0, 8, 0, 8));
            p.setBackground(selected ? l.getSelectionBackground() : l.getBackground());
            String left = it.name;
            if (it.kind == ShopItems.Kind.ITEM) left += "   x" + ShopItems.count(h, it);
            if (it.kind == ShopItems.Kind.BOOK && it.heroClass != h.heroClass)
                left = "[" + KingdomOfEmbers.Hero.CLASS_NAMES[it.heroClass] + "] " + it.name;
            JLabel a = new JLabel(left);
            JLabel b = new JLabel();
            String st = ShopItems.status(h, it);
            Color col;
            String right;
            if (st.equals("Equipped")) { right = "Equipped"; col = GOOD; }
            else if (st.equals("Owned")) { right = "Owned"; col = OWNED; }
            else if (st.startsWith("Class:")) { right = "Locked"; col = MUTED; }
            else if (st.startsWith("Requires")) { right = "Lv " + it.minLevel; col = MUTED; }
            else if (st.startsWith("Maxed")) { right = "Maxed"; col = MUTED; }
            else if (st.equals("Not enough gold")) { right = it.price + " g"; col = BAD; }
            else { right = it.price + " g"; col = GOLD; }
            b.setText(right);
            b.setForeground(col);
            b.setFont(b.getFont().deriveFont(Font.BOLD));
            a.setForeground(st.startsWith("Class:") || st.startsWith("Requires") ? MUTED
                    : selected ? l.getSelectionForeground() : l.getForeground());
            p.add(a, BorderLayout.CENTER);
            p.add(b, BorderLayout.EAST);
            return p;
        }

        void showDetails() {
            ShopItems.Item it = list.getSelectedValue();
            if (it == null) {
                nameL.setText(" "); metaL.setText(" "); descA.setText(""); priceL.setText(" "); statusL.setText(" ");
                buyB.setText("Buy"); buyB.setEnabled(false);
                return;
            }
            KingdomOfEmbers.Hero h = game.hero;
            String kindText = it.kind == ShopItems.Kind.WEAPON ? "Weapon" : it.kind == ShopItems.Kind.ARMOR ? "Armor"
                    : it.kind == ShopItems.Kind.BOOK ? "Skill Book" : "Item";
            String who = it.heroClass < 0 ? "all classes" : KingdomOfEmbers.Hero.CLASS_NAMES[it.heroClass];
            nameL.setText(it.name);
            metaL.setText(kindText + "  |  " + who + (it.minLevel > 1 ? "  |  level " + it.minLevel + "+" : ""));
            descA.setText(it.description());
            priceL.setText("Price: " + it.price + " gold");

            String st = ShopItems.status(h, it);
            boolean equipped = st.equals("Equipped"), owned = st.equals("Owned");
            if (equipped) { statusL.setText("Equipped"); statusL.setForeground(GOOD); }
            else if (owned) { statusL.setText("Owned"); statusL.setForeground(OWNED); }
            else if (st.isEmpty()) { statusL.setText("Available"); statusL.setForeground(GOOD); }
            else { statusL.setText(st); statusL.setForeground(BAD); }

            if (equipped) { buyB.setText("Equipped"); buyB.setEnabled(false); }
            else if (owned && it.kind == ShopItems.Kind.BOOK) { buyB.setText("Owned"); buyB.setEnabled(false); }
            else if (owned) { buyB.setText("Equip"); buyB.setEnabled(true); }          // owned gear: free to re-equip
            else if (st.startsWith("Class:")) { buyB.setText("Not for your class"); buyB.setEnabled(false); }
            else { buyB.setText("Buy for " + it.price + " g"); buyB.setEnabled(true); }  // errors are shown on click
        }

        void act() {
            ShopItems.Item it = list.getSelectedValue();
            if (it == null || !buyB.isEnabled()) return;
            boolean ok = game.shopAction(it);
            msgL.setText(game.shopMsg);
            msgL.setForeground(ok ? GOOD : BAD);
            ShopPanel.this.refresh();
        }
    }
}
