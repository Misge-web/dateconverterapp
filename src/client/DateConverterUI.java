package client;

import rmi.DateConverterException;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 🌍 Distributed Ethiopian–Gregorian Calendar System
 * ⚡ Powered by Java RMI (Multi-client Architecture)
 *
 * Features: tabs, auto-convert, live validation, Amharic toggle,
 * history, dark mode, RMI status with response time, export.
 */
public class DateConverterUI extends JFrame {

    // ── Color palette ─────────────────────────────────────────────────────────
    private static final Color C_PRIMARY   = new Color(0x25, 0x63, 0xEB);
    private static final Color C_SECONDARY = new Color(0x7C, 0x3A, 0xED);
    private static final Color C_SUCCESS   = new Color(0x10, 0xB9, 0x81);
    private static final Color C_DANGER    = new Color(0xEF, 0x44, 0x44);
    private static final Color C_TEAL      = new Color(0x06, 0xB6, 0xD4);
    private static final Color C_BG_LIGHT  = new Color(0xF8, 0xFA, 0xFC);
    private static final Color C_BG_DARK   = new Color(0x1E, 0x29, 0x3B);
    private static final Color C_CARD_LIGHT = Color.WHITE;
    private static final Color C_CARD_DARK  = new Color(0x2D, 0x3B, 0x55);
    private static final Color C_TEXT_LIGHT = new Color(0x1E, 0x29, 0x3B);
    private static final Color C_TEXT_DARK  = new Color(0xF1, 0xF5, 0xF9);
    private static final Color C_BORDER    = new Color(0xE2, 0xE8, 0xF0);
    private static final Color C_ERR_BG    = new Color(0xFF, 0xE4, 0xE6);
    private static final Color C_SP_ERR    = new Color(0xFF, 0xC5, 0xC5);

    // ── Ethiopic-capable font (detected at startup) ───────────────────────────
    private static final Font ETHIOPIC_FONT = findEthiopicFont();

    private static Font findEthiopicFont() {
        // Prefer Ebrima, fall back to Nyala, then any font that can display ሀ
        for (String name : new String[]{"Ebrima", "Nyala"}) {
            Font f = new Font(name, Font.PLAIN, 13);
            if (f.canDisplay('\u1200')) return f;
        }
        // Last resort: scan all system fonts
        for (String name : GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            Font f = new Font(name, Font.PLAIN, 13);
            if (f.canDisplay('\u1200')) return f;
        }
        return new Font("Segoe UI", Font.PLAIN, 13); // give up gracefully
    }
    private static final String[] ETH_EN = {
        "","Meskerem","Tikimt","Hidar","Tahsas","Tir","Yekatit",
        "Megabit","Miazia","Ginbot","Sene","Hamle","Nehase","Pagume"};
    private static final String[] ETH_AM = {
        "","መስከረም","ጥቅምት","ህዳር","ታህሳስ","ጥር","የካቲት",
        "መጋቢት","ሚያዚያ","ግንቦት","ሰኔ","ሐምሌ","ነሐሴ","ጳጉሜ"};
    private static final String[] GREG_MONTHS = {
        "","January","February","March","April","May","June",
        "July","August","September","October","November","December"};

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean darkMode   = false;
    private boolean amharic    = false;
    private boolean stepsOpen  = false;
    private static int windowCount = 0;

    // ── Ethiopian tab widgets ─────────────────────────────────────────────────
    private JSpinner ethDay, ethMonth, ethYear;
    private JLabel   ethErrorLabel;
    private Timer    ethDebounce;

    // ── Gregorian tab widgets ─────────────────────────────────────────────────
    private JSpinner gregDay, gregMonth, gregYear;
    private JLabel   gregErrorLabel;
    private Timer    gregDebounce;

    // ── Shared output ─────────────────────────────────────────────────────────
    private JLabel    resultBig;
    private JTextArea stepsArea;
    private JPanel    stepsPanel;

    // ── History ───────────────────────────────────────────────────────────────
    private final List<String>      historyData  = new ArrayList<>();
    private       DefaultListModel<String> historyModel = new DefaultListModel<>();
    private       JList<String>     historyList;

    // ── Status bar ────────────────────────────────────────────────────────────
    private JLabel connDot;
    private JLabel connText;
    private JLabel pingLabel;

    // ── Theme-aware panels (collected for dark-mode repaint) ──────────────────
    private final List<JComponent> cards = new ArrayList<>();

    // ── RMI ───────────────────────────────────────────────────────────────────
    private final DateConverterClient client;

    // ── Buttons that need theme updates ──────────────────────────────────────
    private JButton btnConvertEth, btnConvertGreg;
    private JButton btnClearEth,   btnClearGreg;
    private JButton btnTodayGreg,  btnTodayEth;
    private JButton btnDark, btnLang, btnExport;
    private JButton btnShowStepsEth, btnShowStepsGreg;

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) {
        startEmbeddedServer();
        SwingUtilities.invokeLater(DateConverterUI::openNewWindow);
    }

    public static void openNewWindow() {
        windowCount++;
        new DateConverterUI(new DateConverterClient(), windowCount);
    }

    private static void startEmbeddedServer() {
        new Thread(() -> {
            try {
                rmi.DateConverterServiceImpl svc = new rmi.DateConverterServiceImpl();
                try {
                    java.rmi.registry.LocateRegistry
                        .createRegistry(rmi.DateConverterServer.DEFAULT_PORT)
                        .rebind(rmi.DateConverterServer.SERVICE_NAME, svc);
                } catch (Exception ex) {
                    java.rmi.registry.LocateRegistry
                        .getRegistry(rmi.DateConverterServer.DEFAULT_PORT)
                        .rebind(rmi.DateConverterServer.SERVICE_NAME, svc);
                }
                System.out.println("[Server] Embedded RMI started on port "
                    + rmi.DateConverterServer.DEFAULT_PORT);
            } catch (Exception e) {
                System.err.println("[Server] " + e.getMessage());
            }
        }, "embedded-server").start();
        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    public DateConverterUI(DateConverterClient client, int idx) {
        super("Distributed Ethiopian-Gregorian Calendar System"
            + (idx > 1 ? "  [Window " + idx + "]" : ""));
        this.client = client;
        setDefaultCloseOperation(idx == 1 ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(680, 560));

        setJMenuBar(buildMenu());
        setLayout(new BorderLayout());
        add(buildTitleBar(),  BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        applyTheme();
        pack();
        setLocationRelativeTo(null);
        if (idx > 1) setLocation(getX() + 30*(idx-1), getY() + 30*(idx-1));
        setVisible(true);
        connectAsync();
    }

    // =========================================================================
    // Menu
    // =========================================================================

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem newWin = new JMenuItem("New Window  (Ctrl+N)");
        newWin.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newWin.addActionListener(e -> openNewWindow());
        file.add(newWin);
        file.addSeparator();
        JMenuItem quit = new JMenuItem("Quit");
        quit.addActionListener(e -> System.exit(0));
        file.add(quit);
        bar.add(file);
        return bar;
    }

    // =========================================================================
    // Title bar
    // =========================================================================

    private JPanel buildTitleBar() {
        JLabel title = new JLabel("Distributed Ethiopian-Gregorian Calendar System",
            SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JLabel sub = new JLabel(
            "Powered by Java RMI  |  Multi-client Architecture  |  Real-time Conversion",
            SwingConstants.CENTER);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(new Color(0x64, 0x74, 0x8B));

        // Toolbar buttons — moved to status bar, nothing here now
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        toolbar.setOpaque(false);

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 2));
        center.setOpaque(false);
        center.add(title);
        center.add(sub);

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(14, 20, 10, 20));
        p.setOpaque(false);
        p.add(center,  BorderLayout.CENTER);
        p.add(toolbar, BorderLayout.EAST);
        cards.add(p);
        return p;
    }

    // =========================================================================
    // Main panel — tabs + result + steps + history
    // =========================================================================

    private JPanel buildMainPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBorder(new EmptyBorder(0, 16, 12, 16));
        p.setOpaque(false);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("Ethiopian  ->  Gregorian", buildEthTab());
        tabs.addTab("Gregorian  ->  Ethiopian",  buildGregTab());
        tabs.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Result card
        JPanel resultCard = buildResultCard();

        // Steps panel (hidden by default)
        stepsPanel = buildStepsPanel();
        stepsPanel.setVisible(false);

        // History card
        JPanel historyCard = buildHistoryCard();

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);
        bottom.add(resultCard);
        bottom.add(Box.createVerticalStrut(10));
        bottom.add(stepsPanel);
        bottom.add(Box.createVerticalStrut(10));
        bottom.add(historyCard);

        p.add(tabs,   BorderLayout.NORTH);
        p.add(bottom, BorderLayout.CENTER);
        return p;
    }

    // =========================================================================
    // Ethiopian tab
    // =========================================================================

    private JPanel buildEthTab() {
        JPanel card = card();

        JLabel lDay   = fieldLabel("Day (1–30)");
        JLabel lMonth = fieldLabel("Month (1–13)");
        JLabel lYear  = fieldLabel("Year");

        ethDay   = spinner(1, 1, 30,   2);
        ethMonth = spinner(1, 1, 13,   2);
        ethYear  = spinner(2016, 1, 9999, 4);

        ethErrorLabel = errorLabel();

        // Debounce timer — fires 600 ms after last spinner change
        ethDebounce = new Timer(600, e -> runEthToGreg());
        ethDebounce.setRepeats(false);

        ChangeListener ethChange = e -> {
            validateEth();
            ethDebounce.restart();
        };
        ethDay  .addChangeListener(ethChange);
        ethMonth.addChangeListener(ethChange);
        ethYear .addChangeListener(ethChange);

        btnConvertEth = actionButton("Convert  →", C_PRIMARY);
        btnConvertEth.addActionListener(e -> runEthToGreg());

        btnClearEth = actionButton("Clear", C_DANGER);
        btnClearEth.addActionListener(e -> clearEth());

        btnTodayEth = actionButton("Today (Eth)", C_SUCCESS);
        btnTodayEth.setToolTipText("Fill today's date in Ethiopian calendar");
        btnTodayEth.addActionListener(e -> fillTodayEth());

        btnShowStepsEth = actionButton("Show Steps ▼", C_TEAL);
        btnShowStepsEth.addActionListener(e -> toggleSteps());

        // Layout
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        row1.setOpaque(false);
        row1.add(labeledSpinner(lDay,   ethDay));
        row1.add(labeledSpinner(lMonth, ethMonth));
        row1.add(labeledSpinner(lYear,  ethYear));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.add(btnConvertEth);
        btnRow.add(btnClearEth);
        btnRow.add(btnTodayEth);
        btnRow.add(btnShowStepsEth);

        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(sectionLabel("Ethiopian Date  (Day / Month / Year)"));
        card.add(Box.createVerticalStrut(10));
        card.add(row1);
        card.add(Box.createVerticalStrut(6));
        card.add(ethErrorLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(btnRow);
        return card;
    }

    // =========================================================================
    // Gregorian tab
    // =========================================================================

    private JPanel buildGregTab() {
        JPanel card = card();

        JLabel lDay   = fieldLabel("Day (1–31)");
        JLabel lMonth = fieldLabel("Month (1–12)");
        JLabel lYear  = fieldLabel("Year");

        gregDay   = spinner(1,    1, 31,   2);
        gregMonth = spinner(1,    1, 12,   2);
        gregYear  = spinner(2024, 1, 9999, 4);

        gregErrorLabel = errorLabel();

        gregDebounce = new Timer(600, e -> runGregToEth());
        gregDebounce.setRepeats(false);

        ChangeListener gregChange = e -> {
            validateGreg();
            gregDebounce.restart();
        };
        gregDay  .addChangeListener(gregChange);
        gregMonth.addChangeListener(gregChange);
        gregYear .addChangeListener(gregChange);

        btnConvertGreg = actionButton("Convert  →", C_PRIMARY);
        btnConvertGreg.addActionListener(e -> runGregToEth());

        btnClearGreg = actionButton("Clear", C_DANGER);
        btnClearGreg.addActionListener(e -> clearGreg());

        btnTodayGreg = actionButton("Today", C_SUCCESS);
        btnTodayGreg.addActionListener(e -> fillToday());

        btnShowStepsGreg = actionButton("Show Steps ▼", C_TEAL);
        btnShowStepsGreg.addActionListener(e -> toggleSteps());

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        row1.setOpaque(false);
        row1.add(labeledSpinner(lDay,   gregDay));
        row1.add(labeledSpinner(lMonth, gregMonth));
        row1.add(labeledSpinner(lYear,  gregYear));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.add(btnConvertGreg);
        btnRow.add(btnClearGreg);
        btnRow.add(btnTodayGreg);
        btnRow.add(btnShowStepsGreg);

        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(sectionLabel("Gregorian Date  (Day / Month / Year)"));
        card.add(Box.createVerticalStrut(10));
        card.add(row1);
        card.add(Box.createVerticalStrut(6));
        card.add(gregErrorLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(btnRow);
        return card;
    }

    // =========================================================================
    // Result card
    // =========================================================================

    private JPanel buildResultCard() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            new EmptyBorder(16, 20, 16, 20)));

        resultBig = new JLabel(
            "<html><center><span style='color:#94A3B8;font-size:13px'>"
            + "Result will appear here after conversion</span></center></html>",
            SwingConstants.CENTER);
        resultBig.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        card.add(resultBig, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // Steps panel
    // =========================================================================

    private JPanel buildStepsPanel() {
        stepsArea = new JTextArea(10, 60);
        stepsArea.setEditable(false);
        stepsArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        stepsArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        stepsArea.setText("Steps will appear here after conversion.");

        JScrollPane scroll = new JScrollPane(stepsArea);
        scroll.setBorder(new LineBorder(C_BORDER, 1, true));

        JLabel heading = new JLabel("Conversion Steps  (computed on RMI server)");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 12));
        heading.setForeground(C_TEAL);

        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 0, 0, 0));
        p.add(heading, BorderLayout.NORTH);
        p.add(scroll,  BorderLayout.CENTER);
        return p;
    }

    private void toggleSteps() {
        stepsOpen = !stepsOpen;
        stepsPanel.setVisible(stepsOpen);
        String label = stepsOpen ? "Hide Steps ▲" : "Show Steps ▼";
        btnShowStepsEth.setText(label);
        btnShowStepsGreg.setText(label);
        pack();
    }

    // =========================================================================
    // History card
    // =========================================================================

    private JPanel buildHistoryCard() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            new EmptyBorder(12, 16, 12, 16)));

        JLabel heading = new JLabel("Conversion History  (last 10)");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 12));
        heading.setForeground(C_SECONDARY);

        historyList = new JList<>(historyModel);
        historyList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        historyList.setFixedCellHeight(22);
        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setPreferredSize(new Dimension(600, 90));
        scroll.setBorder(new LineBorder(C_BORDER, 1, true));

        btnExport = actionButton("Export History", new Color(0x47, 0x55, 0x69));
        btnExport.addActionListener(e -> exportHistory());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(heading,   BorderLayout.WEST);
        top.add(btnExport, BorderLayout.EAST);

        card.add(top,    BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // Status bar
    // =========================================================================

    private JPanel buildStatusBar() {
        connDot  = new JLabel("*");
        connDot.setFont(new Font("Segoe UI", Font.BOLD, 16));
        connDot.setForeground(Color.ORANGE);

        connText = new JLabel("Connecting to RMI server...");
        connText.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        pingLabel = new JLabel("");
        pingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        pingLabel.setForeground(new Color(0x64, 0x74, 0x8B));

        JButton reconnect = new JButton("Reconnect");
        reconnect.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        reconnect.addActionListener(e -> connectAsync());

        // Dark mode and language buttons live here — clean, no emoji
        btnDark = new JButton("Dark Mode");
        btnDark.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnDark.setBackground(new Color(0x47, 0x55, 0x69));
        btnDark.setForeground(Color.WHITE);
        btnDark.setFocusPainted(false);
        btnDark.setBorderPainted(false);
        btnDark.setOpaque(true);
        btnDark.addActionListener(e -> toggleDark());

        btnLang = new JButton("Amharic");
        btnLang.setFont(ETHIOPIC_FONT.deriveFont(Font.PLAIN, 11f));
        btnLang.setBackground(new Color(0x06, 0x95, 0x6E));
        btnLang.setForeground(Color.WHITE);
        btnLang.setFocusPainted(false);
        btnLang.setBorderPainted(false);
        btnLang.setOpaque(true);
        btnLang.addActionListener(e -> toggleLanguage());

        JButton newWin = new JButton("+ New Window");
        newWin.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        newWin.addActionListener(e -> openNewWindow());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        left.setOpaque(false);
        left.add(connDot);
        left.add(connText);
        left.add(pingLabel);
        left.add(reconnect);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        right.setOpaque(false);
        right.add(btnLang);
        right.add(btnDark);
        right.add(newWin);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new MatteBorder(1, 0, 0, 0, C_BORDER));
        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        cards.add(bar);
        return bar;
    }

    // =========================================================================
    // RMI connection
    // =========================================================================

    private void connectAsync() {
        connDot.setForeground(Color.ORANGE);
        connText.setText("Connecting…");
        pingLabel.setText("");
        new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                client.reconnect();
                long ms = System.currentTimeMillis() - t0;
                SwingUtilities.invokeLater(() -> {
                    connDot.setForeground(C_SUCCESS);
                    connText.setText("Connected to Server (Port 1099)");
                    pingLabel.setText("  " + ms + " ms");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    connDot.setForeground(C_DANGER);
                    connText.setText("Disconnected — " + ex.getMessage());
                });
            }
        }, "rmi-connect").start();
    }

    // =========================================================================
    // Conversion logic
    // =========================================================================

    private void runEthToGreg() {
        if (!validateEth()) return;
        int d = (int) ethDay.getValue();
        int m = (int) ethMonth.getValue();
        int y = (int) ethYear.getValue();
        setConverting(true);
        new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                String result = client.ethiopianToGregorian(d, m, y);
                long ms = System.currentTimeMillis() - t0;
                String[] steps = stepsOpen
                    ? client.getConversionSteps(d, m, y, "ETH_TO_GREG") : null;
                String[] parts = result.split("-");
                int gd = Integer.parseInt(parts[0]);
                int gm = Integer.parseInt(parts[1]);
                int gy = Integer.parseInt(parts[2]);
                String ethLabel = d + " " + monthName(m, true) + " " + y;
                String gregLabel = gd + " " + GREG_MONTHS[gm] + " " + gy;
                SwingUtilities.invokeLater(() -> {
                    gregDay.setValue(gd); gregMonth.setValue(gm); gregYear.setValue(gy);
                    showResult(ethLabel, gregLabel, true);
                    pingLabel.setText("  " + ms + " ms");
                    addHistory(ethLabel + "  →  " + gregLabel);
                    if (steps != null) showSteps(steps);
                    setConverting(false);
                });
            } catch (DateConverterException ex) {
                SwingUtilities.invokeLater(() -> {
                    ethErrorLabel.setText("⚠  " + ex.getMessage());
                    ethErrorLabel.setVisible(true);
                    setConverting(false);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    connDot.setForeground(C_DANGER);
                    connText.setText("RMI error: " + ex.getMessage());
                    setConverting(false);
                });
            }
        }, "eth-to-greg").start();
    }

    private void runGregToEth() {
        if (!validateGreg()) return;
        int d = (int) gregDay.getValue();
        int m = (int) gregMonth.getValue();
        int y = (int) gregYear.getValue();
        setConverting(true);
        new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                String result = client.gregorianToEthiopian(d, m, y);
                long ms = System.currentTimeMillis() - t0;
                String[] steps = stepsOpen
                    ? client.getConversionSteps(d, m, y, "GREG_TO_ETH") : null;
                String[] parts = result.split("-");
                int ed = Integer.parseInt(parts[0]);
                int em = Integer.parseInt(parts[1]);
                int ey = Integer.parseInt(parts[2]);
                String gregLabel = d + " " + GREG_MONTHS[m] + " " + y;
                String ethLabel  = ed + " " + monthName(em, true) + " " + ey;
                SwingUtilities.invokeLater(() -> {
                    ethDay.setValue(ed); ethMonth.setValue(em); ethYear.setValue(ey);
                    showResult(ethLabel, gregLabel, false);
                    pingLabel.setText("  " + ms + " ms");
                    addHistory(gregLabel + "  →  " + ethLabel);
                    if (steps != null) showSteps(steps);
                    setConverting(false);
                });
            } catch (DateConverterException ex) {
                SwingUtilities.invokeLater(() -> {
                    gregErrorLabel.setText("⚠  " + ex.getMessage());
                    gregErrorLabel.setVisible(true);
                    setConverting(false);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    connDot.setForeground(C_DANGER);
                    connText.setText("RMI error: " + ex.getMessage());
                    setConverting(false);
                });
            }
        }, "greg-to-eth").start();
    }

    private void setConverting(boolean on) {
        btnConvertEth.setText(on ? "Converting…" : "Convert  →");
        btnConvertGreg.setText(on ? "Converting…" : "Convert  →");
        btnConvertEth.setEnabled(!on);
        btnConvertGreg.setEnabled(!on);
    }

    private void showResult(String ethDate, String gregDate, boolean ethToGreg) {
        String from = ethToGreg ? "ETH: " + ethDate : "GRE: " + gregDate;
        String to   = ethToGreg ? "GRE: " + gregDate : "ETH: " + ethDate;
        resultBig.setFont(amharic
            ? ETHIOPIC_FONT.deriveFont(Font.PLAIN, 15f)
            : new Font("Segoe UI", Font.PLAIN, 15));
        resultBig.setText(
            "<html><center>"
            + "<span style='font-size:14px;color:#64748B'>" + from + "</span>"
            + "<br>"
            + "<span style='font-size:20px;color:#2563EB'>&rarr;</span>"
            + "<br>"
            + "<b style='font-size:18px;color:#10B981'>" + to + "</b>"
            + "</center></html>");
    }

    private void showSteps(String[] steps) {
        StringBuilder sb = new StringBuilder();
        for (String s : steps) sb.append(s).append("\n");
        stepsArea.setText(sb.toString().trim());
        stepsArea.setCaretPosition(0);
        if (!stepsOpen) {
            stepsOpen = true;
            stepsPanel.setVisible(true);
            btnShowStepsEth.setText("Hide Steps ▲");
            btnShowStepsGreg.setText("Hide Steps ▲");
            pack();
        }
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /** @return true if valid */
    private boolean validateEth() {
        if (ethDay == null) return true;
        int d = (int) ethDay.getValue();
        int m = (int) ethMonth.getValue();
        int y = (int) ethYear.getValue();
        spinnerOk(ethDay); spinnerOk(ethMonth); spinnerOk(ethYear);
        ethErrorLabel.setVisible(false);

        if (m == 13) {
            int max = (y % 4 == 3) ? 6 : 5;
            if (d > max) {
                spinnerErr(ethDay);
                ethErrorLabel.setText("⚠  Pagume has " + max + " days in year " + y
                    + " (year " + y + " is " + (y%4==3?"a leap year":"not a leap year") + ")");
                ethErrorLabel.setVisible(true);
                return false;
            }
        }
        return true;
    }

    /** @return true if valid */
    private boolean validateGreg() {
        if (gregDay == null) return true;
        int d = (int) gregDay.getValue();
        int m = (int) gregMonth.getValue();
        int y = (int) gregYear.getValue();
        spinnerOk(gregDay); spinnerOk(gregMonth); spinnerOk(gregYear);
        gregErrorLabel.setVisible(false);

        int max = gregMaxDay(m, y);
        if (d > max) {
            spinnerErr(gregDay);
            gregErrorLabel.setText("⚠  " + GREG_MONTHS[m] + " " + y + " only has " + max
                + " days" + (m==2 ? " (year " + y + " is "
                + (isGregLeap(y)?"a leap year":"not a leap year") + ")" : ""));
            gregErrorLabel.setVisible(true);
            return false;
        }
        return true;
    }

    private void spinnerOk(JSpinner sp) {
        JComponent ed = sp.getEditor();
        ed.setBackground(Color.WHITE);
        if (ed instanceof JSpinner.NumberEditor)
            ((JSpinner.NumberEditor)ed).getTextField().setBackground(Color.WHITE);
    }

    private void spinnerErr(JSpinner sp) {
        JComponent ed = sp.getEditor();
        ed.setBackground(C_SP_ERR);
        if (ed instanceof JSpinner.NumberEditor)
            ((JSpinner.NumberEditor)ed).getTextField().setBackground(C_SP_ERR);
    }

    // =========================================================================
    // Clear / Today
    // =========================================================================

    private void clearEth() {
        ethDay.setValue(1); ethMonth.setValue(1); ethYear.setValue(2016);
        spinnerOk(ethDay); spinnerOk(ethMonth); spinnerOk(ethYear);
        ethErrorLabel.setVisible(false);
    }

    private void clearGreg() {
        gregDay.setValue(1); gregMonth.setValue(1); gregYear.setValue(2024);
        spinnerOk(gregDay); spinnerOk(gregMonth); spinnerOk(gregYear);
        gregErrorLabel.setVisible(false);
    }

    private void fillToday() {
        Calendar c = Calendar.getInstance();
        gregDay.setValue(c.get(Calendar.DAY_OF_MONTH));
        gregMonth.setValue(c.get(Calendar.MONTH) + 1);
        gregYear.setValue(c.get(Calendar.YEAR));
    }

    /** Converts today's Gregorian date to Ethiopian and fills the Ethiopian spinners. */
    private void fillTodayEth() {
        Calendar c = Calendar.getInstance();
        int d = c.get(Calendar.DAY_OF_MONTH);
        int m = c.get(Calendar.MONTH) + 1;
        int y = c.get(Calendar.YEAR);
        new Thread(() -> {
            try {
                String result = client.gregorianToEthiopian(d, m, y);
                String[] parts = result.split("-");
                int ed = Integer.parseInt(parts[0]);
                int em = Integer.parseInt(parts[1]);
                int ey = Integer.parseInt(parts[2]);
                SwingUtilities.invokeLater(() -> {
                    ethDay.setValue(ed);
                    ethMonth.setValue(em);
                    ethYear.setValue(ey);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    connText.setText("Today (Eth) failed: " + ex.getMessage()));
            }
        }, "today-eth").start();
    }

    // =========================================================================
    // History
    // =========================================================================

    private void addHistory(String entry) {
        historyData.add(0, entry);
        if (historyData.size() > 10) historyData.remove(historyData.size() - 1);
        historyModel.clear();
        for (String s : historyData) historyModel.addElement(s);
    }

    private void exportHistory() {
        try (FileWriter fw = new FileWriter("conversion_history.txt")) {
            fw.write("Ethiopian–Gregorian Conversion History\n");
            fw.write("======================================\n");
            for (String s : historyData) fw.write(s + "\n");
            JOptionPane.showMessageDialog(this,
                "History exported to conversion_history.txt",
                "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Export failed: " + ex.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // Dark mode
    // =========================================================================

    private void toggleDark() {
        darkMode = !darkMode;
        btnDark.setText(darkMode ? "Light Mode" : "Dark Mode");
        applyTheme();
    }

    private void applyTheme() {
        Color bg   = darkMode ? C_BG_DARK   : C_BG_LIGHT;
        Color card = darkMode ? C_CARD_DARK  : C_CARD_LIGHT;
        Color text = darkMode ? C_TEXT_DARK  : C_TEXT_LIGHT;

        getContentPane().setBackground(bg);
        for (JComponent c : cards) {
            c.setBackground(bg);
            c.setForeground(text);
        }
        if (stepsArea != null) {
            stepsArea.setBackground(darkMode ? new Color(0x1E,0x29,0x3B) : new Color(0xF8,0xFA,0xFC));
            stepsArea.setForeground(text);
        }
        if (historyList != null) {
            historyList.setBackground(card);
            historyList.setForeground(text);
        }
        repaint();
    }

    // =========================================================================
    // Language toggle
    // =========================================================================

    private void toggleLanguage() {
        amharic = !amharic;
        // Switch button label — use Ethiopic font when showing Amharic label
        btnLang.setText(amharic ? "English" : "Amharic");
        btnLang.setFont(amharic
            ? new Font("Segoe UI", Font.PLAIN, 11)
            : ETHIOPIC_FONT.deriveFont(Font.PLAIN, 11f));
    }

    private String monthName(int m, boolean ethiopian) {
        if (!ethiopian) return GREG_MONTHS[m];
        return amharic ? ETH_AM[m] : ETH_EN[m];
    }

    // =========================================================================
    // Calendar helpers
    // =========================================================================

    private boolean isGregLeap(int y) {
        return (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0);
    }

    private int gregMaxDay(int m, int y) {
        int[] days = {0,31,28,31,30,31,30,31,31,30,31,30,31};
        if (m == 2 && isGregLeap(y)) return 29;
        return days[m];
    }

    // =========================================================================
    // Widget factories
    // =========================================================================

    private JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(C_CARD_LIGHT);
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            new EmptyBorder(14, 18, 14, 18)));
        cards.add(p);
        return p;
    }

    private JSpinner spinner(int val, int min, int max, int cols) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
        sp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sp.setPreferredSize(new Dimension(cols == 4 ? 72 : 56, 30));
        JSpinner.NumberEditor ed = new JSpinner.NumberEditor(sp, "#");
        sp.setEditor(ed);
        return sp;
    }

    private JPanel labeledSpinner(JLabel lbl, JSpinner sp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(lbl, BorderLayout.NORTH);
        p.add(sp,  BorderLayout.CENTER);
        return p;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(new Color(0x64, 0x74, 0x8B));
        return l;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 14));
        l.setForeground(C_TEXT_LIGHT);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel errorLabel() {
        JLabel l = new JLabel(" ");
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(C_DANGER);
        l.setBackground(C_ERR_BG);
        l.setOpaque(true);
        l.setBorder(new EmptyBorder(4, 8, 4, 8));
        l.setVisible(false);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JButton actionButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(140, 36));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bg.darker()); }
            public void mouseExited (MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    private JButton toolbarButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(100, 28));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
