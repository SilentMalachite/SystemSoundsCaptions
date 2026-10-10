package captions.ui;

import captions.Pipeline;
import captions.Session;
import captions.audio.LineSource;
import captions.audio.LoopbackFinder;

import javax.sound.sampled.Mixer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.prefs.Preferences;

/** The small window where the user picks the source and starts captions. */
public final class ControlWindow extends JFrame {

    private enum Mode { MICROPHONE, SYSTEM, FILE }

    private record Device(Mixer.Info mixer, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Color OK = new Color(0x1F7A52);
    private static final Color BAD = new Color(0xB42318);
    private static final Color DIM = new Color(0x8A8578);

    private final Pipeline pipeline;
    private final CaptionOverlay overlay;

    private final JToggleButton micButton = new JToggleButton("マイク");
    private final JToggleButton systemButton = new JToggleButton("システム音声");
    private final JToggleButton fileButton = new JToggleButton("ファイル");
    private final JComboBox<Device> devices = new JComboBox<>();
    private final JLabel deviceLabel = new JLabel("デバイス");
    private final JLabel loopbackHint = new JLabel();
    private final JLabel fileLabel = new JLabel("ファイル");
    private final JLabel dropArea = new JLabel("ここにファイルをドロップ、またはクリックして選ぶ", SwingConstants.CENTER);
    private final JSlider fontSize;
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JLabel progressText = new JLabel(" ");
    private final JPanel progressPanel = new JPanel(new BorderLayout(8, 0));
    private final JButton startButton = new JButton("▶ 開始");
    private final JLabel status = new JLabel("待機中");

    private Mode mode = Mode.MICROPHONE;
    private Path file;
    private Session.State state = Session.State.IDLE;

    public ControlWindow(Session session, Pipeline pipeline, CaptionOverlay overlay, Preferences prefs) {
        super("System Sounds Captions");
        this.pipeline = pipeline;
        this.overlay = overlay;
        this.fontSize = new JSlider(16, 64, overlay.fontSize());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pipeline.stop();
            }
        });

        buildLayout();
        wireActions(prefs);
        session.addListener((from, to, detail) -> SwingUtilities.invokeLater(() -> show(to, detail)));
        pipeline.setProgressListener(new Pipeline.ProgressListener() {
            @Override
            public void download(long done, long total) {
                SwingUtilities.invokeLater(() -> showDownload(done, total));
            }

            @Override
            public void file(Duration played, Duration total) {
                SwingUtilities.invokeLater(() -> showFile(played, total));
            }
        });

        Mode saved = Mode.valueOf(prefs.get("mode", Mode.MICROPHONE.name()));
        selectMode(saved);
        show(Session.State.IDLE, "");
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        var form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        var c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.anchor = GridBagConstraints.WEST;

        var group = new ButtonGroup();
        var sources = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (var b : List.of(micButton, systemButton, fileButton)) {
            group.add(b);
            sources.add(b);
        }
        row(form, c, 0, new JLabel("音源"), sources);
        row(form, c, 1, deviceLabel, devices);

        loopbackHint.setForeground(BAD);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(loopbackHint, c);

        dropArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(DIM, 4, 3),
                BorderFactory.createEmptyBorder(14, 10, 14, 10)));
        dropArea.setForeground(DIM);
        dropArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row(form, c, 3, fileLabel, dropArea);

        row(form, c, 4, new JLabel("言語"), new JLabel("日本語"));
        row(form, c, 5, new JLabel("文字サイズ"), fontSize);

        progressPanel.add(progress, BorderLayout.CENTER);
        progressText.setForeground(DIM);
        progressPanel.add(progressText, BorderLayout.EAST);
        row(form, c, 6, new JLabel(""), progressPanel);

        var footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xEEE9DD)));
        footer.add(startButton);
        footer.add(status);

        var content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private static void row(JPanel form, GridBagConstraints c, int y, JComponent label, JComponent field) {
        c.gridy = y;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        label.setForeground(DIM);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    private void wireActions(Preferences prefs) {
        micButton.addActionListener(e -> selectMode(Mode.MICROPHONE));
        systemButton.addActionListener(e -> selectMode(Mode.SYSTEM));
        fileButton.addActionListener(e -> selectMode(Mode.FILE));
        micButton.addActionListener(e -> prefs.put("mode", Mode.MICROPHONE.name()));
        systemButton.addActionListener(e -> prefs.put("mode", Mode.SYSTEM.name()));
        fileButton.addActionListener(e -> prefs.put("mode", Mode.FILE.name()));

        fontSize.addChangeListener(e -> overlay.setFontSize(fontSize.getValue()));

        dropArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (dropArea.isEnabled()) {
                    chooseFile();
                }
            }
        });
        // a file can be dropped anywhere on the window
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return state != Session.State.LOADING && state != Session.State.LISTENING
                        && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    var files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) {
                        return false;
                    }
                    setFile(files.getFirst().toPath());
                    selectMode(Mode.FILE);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        startButton.addActionListener(e -> {
            if (state == Session.State.LOADING || state == Session.State.LISTENING) {
                pipeline.stop();
            } else {
                start();
            }
        });
    }

    private void selectMode(Mode next) {
        mode = next;
        switch (next) {
            case MICROPHONE -> micButton.setSelected(true);
            case SYSTEM -> systemButton.setSelected(true);
            case FILE -> fileButton.setSelected(true);
        }
        boolean line = next != Mode.FILE;
        deviceLabel.setVisible(line);
        devices.setVisible(line);
        fileLabel.setVisible(!line);
        dropArea.setVisible(!line);
        loopbackHint.setVisible(false);
        if (line) {
            fillDevices(next);
        }
        progress.setValue(0);
        progressText.setText(" ");
        progressPanel.setVisible(!line || state == Session.State.LOADING);
        pack();
    }

    private void fillDevices(Mode next) {
        devices.removeAllItems();
        List<Mixer.Info> inputs = LineSource.inputs();
        if (next == Mode.MICROPHONE) {
            devices.addItem(new Device(null, "既定の入力デバイス"));
            inputs.stream()
                    .filter(i -> !LoopbackFinder.looksLikeLoopback(i.getName()))
                    .forEach(i -> devices.addItem(new Device(i, i.getName())));
            return;
        }
        var loopback = LoopbackFinder.find(inputs);
        loopback.ifPresent(i -> devices.addItem(new Device(i, i.getName())));
        inputs.stream()
                .filter(i -> loopback.map(l -> !l.equals(i)).orElse(true))
                .forEach(i -> devices.addItem(new Device(i, i.getName())));
        if (loopback.isEmpty()) {
            boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
            loopbackHint.setText(mac
                    ? "<html>ループバック用のデバイスがありません。<br>BlackHole を入れて「複数出力装置」を作ってください (README 参照)。</html>"
                    : "<html>ループバック用のデバイスがありません。<br>サウンド設定で「ステレオ ミキサー」を有効にしてください (README 参照)。</html>");
            loopbackHint.setVisible(true);
        }
    }

    private void chooseFile() {
        var chooser = new JFileChooser(file == null ? null : file.getParent().toFile());
        chooser.setDialogTitle("音声・動画ファイルを選ぶ");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setFile(chooser.getSelectedFile().toPath());
        }
    }

    private void setFile(Path path) {
        file = path;
        dropArea.setText(path.getFileName().toString());
        dropArea.setForeground(Color.DARK_GRAY);
    }

    private void start() {
        overlay.clear();
        Pipeline.Choice choice;
        switch (mode) {
            case FILE -> {
                if (file == null) {
                    chooseFile();
                    if (file == null) {
                        return;
                    }
                }
                choice = new Pipeline.Choice.AudioFile(file);
            }
            case MICROPHONE -> {
                var d = (Device) devices.getSelectedItem();
                choice = new Pipeline.Choice.Microphone(d == null ? null : d.mixer(), d == null ? "マイク" : d.name());
            }
            case SYSTEM -> {
                var d = (Device) devices.getSelectedItem();
                if (d == null) {
                    return;
                }
                choice = new Pipeline.Choice.SystemSound(d.mixer(), d.name());
            }
            default -> throw new IllegalStateException();
        }
        pipeline.start(choice);
    }

    private void show(Session.State next, String detail) {
        state = next;
        if (next != Session.State.LOADING) {
            progress.setIndeterminate(false);
        }
        // the bar shows the download, or how far a file played
        progressPanel.setVisible(next == Session.State.LOADING || mode == Mode.FILE);
        boolean busy = next == Session.State.LOADING || next == Session.State.LISTENING;
        for (var control : List.of(micButton, systemButton, fileButton, devices, dropArea)) {
            control.setEnabled(!busy);
        }
        switch (next) {
            case IDLE -> {
                startButton.setText("▶ 開始");
                status.setText("待機中");
                status.setForeground(DIM);
                overlay.setVisible(false);
            }
            case LOADING -> {
                startButton.setText("キャンセル");
                status.setText("モデルを読み込み中…");
                status.setForeground(DIM);
                overlay.setPlaceholder("モデルを読み込み中…");
                overlay.setVisible(true);
            }
            case LISTENING -> {
                startButton.setText("■ 停止");
                status.setText("● 認識中 · " + detail);
                status.setForeground(OK);
                overlay.setPlaceholder("音声を待っています…");
                progress.setValue(0);
                progressText.setText(" ");
            }
            case ERROR -> {
                startButton.setText("▶ 開始");
                status.setText("<html>⚠ " + escape(detail) + "</html>");
                status.setForeground(BAD);
                overlay.setVisible(false);
            }
        }
        pack();
    }

    private void showDownload(long done, long total) {
        status.setText("モデルをダウンロード中… (初回だけ)");
        if (total > 0) {
            progress.setValue((int) (done * 1000 / total));
            progressText.setText("%d / %d MB".formatted(done >> 20, total >> 20));
        } else {
            progress.setIndeterminate(true);
            progressText.setText("%d MB".formatted(done >> 20));
        }
    }

    private void showFile(Duration played, Duration total) {
        progress.setIndeterminate(false);
        if (total != null && !total.isZero()) {
            progress.setValue((int) Math.min(1000, played.toMillis() * 1000 / total.toMillis()));
            progressText.setText(clock(played) + " / " + clock(total));
        } else {
            progressText.setText(clock(played));
        }
    }

    static String clock(Duration d) {
        long s = d.toSeconds();
        return s >= 3600
                ? "%d:%02d:%02d".formatted(s / 3600, s / 60 % 60, s % 60)
                : "%02d:%02d".formatted(s / 60, s % 60);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
