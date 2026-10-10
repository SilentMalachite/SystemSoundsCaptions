package captions.ui;

import captions.caption.CaptionEvent;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * A window above all other windows that shows the captions.
 * Final lines are white, the line in progress is gray. Final lines go after 5 seconds.
 */
public final class CaptionOverlay extends JWindow {

    static final int MAX_LINES = 2;
    static final Duration KEEP = Duration.ofSeconds(5);
    private static final int PAD = 18;
    private static final Color BOX = new Color(0, 0, 0, 184);
    private static final Color FINAL = Color.WHITE;
    private static final Color PARTIAL = new Color(0xB8B8B8);
    private static final Color NOTICE = new Color(0xFFB4A8);

    private record Line(String text, Instant at) {
    }

    private final Preferences prefs;
    private final Deque<Line> lines = new ArrayDeque<>();
    private final boolean translucent;
    private String partial = "";
    private String notice = "";
    private String placeholder = "";
    private int fontSize;

    public CaptionOverlay(Preferences prefs) {
        this.prefs = prefs;
        this.fontSize = prefs.getInt("fontSize", 28);
        GraphicsDevice screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        translucent = screen.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);

        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        if (translucent) {
            setBackground(new Color(0, 0, 0, 0));
        }
        var view = new View();
        setContentPane(view);
        var drag = new Drag();
        view.addMouseListener(drag);
        view.addMouseMotionListener(drag);

        resize();
        setLocation(savedLocation());

        new Timer(500, e -> dropOldLines()).start();
    }

    public void onEvent(CaptionEvent event) {
        SwingUtilities.invokeLater(() -> {
            switch (event) {
                case CaptionEvent.Partial p -> partial = p.text();
                case CaptionEvent.Final f -> {
                    lines.addLast(new Line(f.text(), f.at()));
                    partial = "";
                    while (lines.size() > MAX_LINES) {
                        lines.removeFirst();
                    }
                }
                case CaptionEvent.Error err -> notice = err.message();
            }
            repaint();
        });
    }

    /** Shown when there is no caption yet, so the user can see and move the window. */
    public void setPlaceholder(String text) {
        placeholder = text;
        repaint();
    }

    public void clear() {
        lines.clear();
        partial = "";
        notice = "";
        repaint();
    }

    public int fontSize() {
        return fontSize;
    }

    public void setFontSize(int size) {
        int bottom = getY() + getHeight();
        fontSize = size;
        prefs.putInt("fontSize", size);
        resize();
        setLocation(getX(), bottom - getHeight());
        repaint();
    }

    private void resize() {
        Rectangle screen = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(1200, (int) (screen.width * 0.7));
        int height = (int) (fontSize * 1.5 * 5) + PAD * 2; // room for about 5 wrapped lines
        setSize(width, height);
    }

    private Point savedLocation() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        var fallback = new Point(screen.x + (screen.width - getWidth()) / 2,
                screen.y + screen.height - getHeight() - 60);
        int x = prefs.getInt("x", Integer.MIN_VALUE);
        int y = prefs.getInt("y", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || !onSomeScreen(new Rectangle(x, y, getWidth(), getHeight()))) {
            return fallback;
        }
        return new Point(x, y);
    }

    private static boolean onSomeScreen(Rectangle r) {
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (d.getDefaultConfiguration().getBounds().intersects(r)) {
                return true;
            }
        }
        return false;
    }

    private void dropOldLines() {
        Instant limit = Instant.now().minus(KEEP);
        boolean changed = false;
        while (!lines.isEmpty() && lines.peekFirst().at().isBefore(limit)) {
            lines.removeFirst();
            changed = true;
        }
        if (changed) {
            repaint();
        }
    }

    private final class View extends JComponent {
        View() {
            setOpaque(!translucent);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (!translucent) {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                paintCaptions(g);
            } finally {
                g.dispose();
            }
        }

        private void paintCaptions(Graphics2D g) {
            var font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            var small = font.deriveFont(Font.PLAIN, fontSize * 0.6f);
            int wrap = getWidth() - PAD * 2;

            var rows = new ArrayList<Row>();
            for (Line line : lines) {
                rows.addAll(wrapRows(g, line.text(), font, FINAL, wrap));
            }
            if (!partial.isBlank()) {
                rows.addAll(wrapRows(g, partial, font, PARTIAL, wrap));
            }
            if (rows.isEmpty() && !placeholder.isBlank()) {
                rows.addAll(wrapRows(g, placeholder, small, PARTIAL, wrap));
            }
            if (!notice.isBlank()) {
                rows.addAll(wrapRows(g, notice, small, NOTICE, wrap));
            }
            if (rows.isEmpty()) {
                return;
            }

            // keep the newest rows that fit; the box grows up from the bottom
            float room = getHeight() - PAD * 2;
            float used = 0;
            int first = rows.size();
            while (first > 0 && used + rows.get(first - 1).height() <= room) {
                used += rows.get(--first).height();
            }
            var shown = rows.subList(first, rows.size());

            float textWidth = 0;
            for (Row row : shown) {
                textWidth = Math.max(textWidth, row.layout().getAdvance());
            }
            int boxWidth = (int) Math.ceil(textWidth) + PAD * 2;
            int boxHeight = (int) Math.ceil(used) + PAD;
            int boxX = (getWidth() - boxWidth) / 2;
            int boxY = getHeight() - boxHeight;
            g.setColor(BOX);
            g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 16, 16);

            float y = boxY + PAD / 2f;
            for (Row row : shown) {
                y += row.layout().getAscent();
                g.setColor(row.color());
                float x = (getWidth() - row.layout().getAdvance()) / 2f;
                row.layout().draw(g, x, y);
                y += row.layout().getDescent() + row.layout().getLeading();
            }
        }
    }

    private record Row(TextLayout layout, Color color) {
        float height() {
            return layout.getAscent() + layout.getDescent() + layout.getLeading();
        }
    }

    /** Breaks text into rows that fit. LineBreakMeasurer also breaks between Japanese characters. */
    private static List<Row> wrapRows(Graphics2D g, String text, Font font, Color color, int width) {
        var rows = new ArrayList<Row>();
        var attributed = new AttributedString(text);
        attributed.addAttribute(TextAttribute.FONT, font);
        var measurer = new LineBreakMeasurer(attributed.getIterator(), g.getFontRenderContext());
        while (measurer.getPosition() < text.length()) {
            rows.add(new Row(measurer.nextLayout(width), color));
        }
        return rows;
    }

    private final class Drag extends MouseAdapter {
        private Point grab;

        @Override
        public void mousePressed(MouseEvent e) {
            grab = e.getPoint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (grab != null) {
                Point screen = e.getLocationOnScreen();
                setLocation(screen.x - grab.x, screen.y - grab.y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            grab = null;
            prefs.putInt("x", getX());
            prefs.putInt("y", getY());
        }
    }
}
