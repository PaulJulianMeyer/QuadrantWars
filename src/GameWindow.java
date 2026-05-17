import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Dimension;

public class GameWindow extends JFrame {
    private Game game = new Game();
    private JButton[][] fieldButtons;
    private JPanel boardPanel;
    private final JLabel statusLabel = new JLabel();
    private final JTextArea logArea = new JTextArea(8, 44);
    private final JButton startPauseButton = new JButton("Start");
    private final JButton stepButton = new JButton("Ein Schritt");
    private final JButton skipButton = new JButton("Skip andere");
    private final JButton resetButton = new JButton("Neu starten");
    private final JButton attackButton = new JButton("Angreifen");
    private final JButton fortifyButton = new JButton("Verschieben");
    private final JButton endTurnButton = new JButton("Zug beenden");
    private final JComboBox<String> speedBox = new JComboBox<>(new String[]{"Normal", "Schnell", "Sehr schnell"});
    private final JComboBox<Integer> sizeBox = new JComboBox<>(new Integer[]{4, 8, 12, 16});
    private final JComboBox<String> playerBox = new JComboBox<>(new String[]{"Nur Simulation", "Blau", "Rot", "Gruen", "Gelb"});
    private final JSpinner soldierSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final Timer simulationTimer;

    private Position highlightedFrom;
    private Position highlightedTo;
    private Position selectedFrom;
    private Position selectedTo;

    public GameWindow() {
        super("Quadrant Wars Simulation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        simulationTimer = new Timer(60, event -> runOneStep());

        add(statusLabel, BorderLayout.NORTH);
        boardPanel = createBoardPanel();
        add(boardPanel, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        startPauseButton.addActionListener(event -> toggleSimulation());
        stepButton.addActionListener(event -> runOneStep());
        skipButton.addActionListener(event -> skipOtherPlayers());
        resetButton.addActionListener(event -> resetSimulation());
        attackButton.addActionListener(event -> attackSelectedFields());
        fortifyButton.addActionListener(event -> fortifySelectedFields());
        endTurnButton.addActionListener(event -> finishHumanTurn());
        playerBox.addActionListener(event -> handlePlayerModeChange());
        speedBox.addActionListener(event -> updateSpeed());
        speedBox.setSelectedItem("Sehr schnell");
        sizeBox.setSelectedItem(Game.DEFAULT_SIZE);

        log("Willkommen bei Quadrant Wars Simulation.");
        log("Waehle Nur Simulation oder uebernimm einen Spieler.");

        refresh();
        setSize(1150, 880);
        setLocationRelativeTo(null);
    }

    private JPanel createBoardPanel() {
        int size = game.getSize();
        JPanel boardPanel = new JPanel(new GridLayout(size, size, 2, 2));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        boardPanel.setPreferredSize(new Dimension(760, 760));
        fieldButtons = new JButton[size][size];

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                JButton button = new JButton();
                button.setFont(new Font("Arial", Font.BOLD, fontSizeForBoard(size)));
                button.setFocusPainted(false);
                Position position = new Position(row, col);
                button.addActionListener(event -> handleFieldClick(position));
                fieldButtons[row][col] = button;
                boardPanel.add(button);
            }
        }

        return boardPanel;
    }

    private JPanel createSidePanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));

        panel.add(startPauseButton);
        panel.add(stepButton);
        panel.add(skipButton);
        panel.add(resetButton);
        panel.add(new JLabel("Mitspielen"));
        panel.add(playerBox);
        panel.add(new JLabel("Feldgroesse"));
        panel.add(sizeBox);
        panel.add(new JLabel("Geschwindigkeit"));
        panel.add(speedBox);
        panel.add(new JLabel("Soldaten"));
        panel.add(soldierSpinner);
        panel.add(attackButton);
        panel.add(fortifyButton);
        panel.add(endTurnButton);

        JButton helpButton = new JButton("Hilfe");
        helpButton.addActionListener(event -> showHelp());
        panel.add(helpButton);

        return panel;
    }

    private void toggleSimulation() {
        if (game.hasWinner()) {
            return;
        }

        if (simulationTimer.isRunning()) {
            simulationTimer.stop();
            startPauseButton.setText("Start");
            stepButton.setEnabled(true);
        } else {
            if (isHumanControlledTurn()) {
                log("Du bist am Zug. Fuehre deinen Zug manuell aus.");
                refresh();
                return;
            }
            simulationTimer.start();
            startPauseButton.setText("Pause");
            stepButton.setEnabled(false);
        }
    }

    private void runOneStep() {
        if (game.hasWinner()) {
            simulationTimer.stop();
            startPauseButton.setText("Start");
            showWinner();
            refresh();
            return;
        }

        if (isHumanControlledTurn()) {
            simulationTimer.stop();
            startPauseButton.setText("Start");
            stepButton.setEnabled(true);
            log(Game.playerName(controlledPlayer()) + " ist dein Spieler. Du bist am Zug.");
            refresh();
            return;
        }

        SimulationStep step = game.stepSimulation();
        highlightedFrom = step.from();
        highlightedTo = step.to();
        log(step.message());
        refreshAfterStep(step);

        if (game.hasWinner()) {
            simulationTimer.stop();
            startPauseButton.setText("Start");
            showWinner();
            refresh();
        } else if (isHumanControlledTurn()) {
            simulationTimer.stop();
            startPauseButton.setText("Start");
            stepButton.setEnabled(true);
            log(Game.playerName(controlledPlayer()) + " ist jetzt dein Zug.");
            refresh();
        }
    }

    private void skipOtherPlayers() {
        char player = controlledPlayer();
        if (player == '-') {
            log("Waehle zuerst eine Farbe, damit Skip sinnvoll ist.");
            return;
        }
        if (game.hasWinner()) {
            return;
        }
        if (isHumanControlledTurn()) {
            log("Du bist schon am Zug.");
            return;
        }

        simulationTimer.stop();
        startPauseButton.setText("Start");
        int skippedSteps = 0;
        while (!game.hasWinner() && !isHumanControlledTurn()) {
            game.stepSimulation();
            skippedSteps++;
        }

        highlightedFrom = null;
        highlightedTo = null;
        log(skippedSteps + " Schritte der anderen Spieler uebersprungen.");
        if (game.hasWinner()) {
            showWinner();
        } else {
            log(Game.playerName(player) + " ist jetzt am Zug.");
        }
        refresh();
    }

    private void resetSimulation() {
        simulationTimer.stop();
        game = new Game((Integer) sizeBox.getSelectedItem());
        highlightedFrom = null;
        highlightedTo = null;
        selectedFrom = null;
        selectedTo = null;
        logArea.setText("");
        startPauseButton.setText("Start");
        stepButton.setEnabled(true);
        remove(boardPanel);
        boardPanel = createBoardPanel();
        add(boardPanel, BorderLayout.CENTER);
        log("Neue Simulation gestartet.");
        revalidate();
        repaint();
        refresh();
    }

    private void handleFieldClick(Position position) {
        if (!isHumanControlledTurn() || game.hasWinner()) {
            return;
        }

        char player = controlledPlayer();
        if (game.getPendingReinforcements() > 0) {
            if (!game.canPlaceReinforcement(position, player)) {
                log("Verstaerkungen duerfen nur auf eigene Felder.");
                return;
            }
            applyStep(game.placeReinforcement(position, player));
            return;
        }

        Field field = game.getField(position);
        if (selectedFrom == null) {
            if (field.getOwner() != player) {
                log("Waehle zuerst ein eigenes Feld.");
                return;
            }
            if (field.getSoldiers() < 2) {
                log("Dieses Feld braucht mindestens 2 Soldaten.");
                return;
            }
            selectedFrom = position;
            selectedTo = null;
            highlightedFrom = position;
            highlightedTo = null;
            updateSpinnerForSelection();
            log("Startfeld " + position.label() + " gewaehlt.");
            refresh();
            return;
        }

        if (position.equals(selectedFrom)) {
            clearSelection();
            log("Auswahl aufgehoben.");
            refresh();
            return;
        }

        selectedTo = position;
        highlightedTo = position;
        updateSpinnerForSelection();
        log("Zielfeld " + position.label() + " gewaehlt.");
        refresh();
    }

    private void attackSelectedFields() {
        if (!isHumanControlledTurn() || selectedFrom == null || selectedTo == null) {
            log("Waehle zuerst Startfeld und Ziel.");
            return;
        }

        char player = controlledPlayer();
        if (!game.canAttack(selectedFrom, selectedTo, player)) {
            log("Angriff geht nur auf ein fremdes direktes Nachbarfeld.");
            return;
        }

        applyStep(game.playerAttack(selectedFrom, selectedTo, (Integer) soldierSpinner.getValue(), player));
        clearSelection();
        if (game.hasWinner()) {
            showWinner();
        }
    }

    private void fortifySelectedFields() {
        if (!isHumanControlledTurn() || selectedFrom == null || selectedTo == null) {
            log("Waehle zuerst Startfeld und Ziel.");
            return;
        }

        char player = controlledPlayer();
        if (!game.canFortify(selectedFrom, selectedTo, player)) {
            log("Verschieben geht nur zwischen verbundenen eigenen Feldern und nur einmal pro Zug.");
            return;
        }

        applyStep(game.playerFortify(selectedFrom, selectedTo, (Integer) soldierSpinner.getValue(), player));
        clearSelection();
    }

    private void finishHumanTurn() {
        if (!isHumanControlledTurn()) {
            return;
        }
        if (game.getPendingReinforcements() > 0) {
            log("Setze zuerst alle Verstaerkungen.");
            return;
        }

        clearSelection();
        applyStep(game.finishCurrentTurn());
    }

    private void applyStep(SimulationStep step) {
        highlightedFrom = step.from();
        highlightedTo = step.to();
        log(step.message());
        refresh();
    }

    private void clearSelection() {
        selectedFrom = null;
        selectedTo = null;
        highlightedFrom = null;
        highlightedTo = null;
        soldierSpinner.setModel(new SpinnerNumberModel(1, 1, 1, 1));
    }

    private void updateSpinnerForSelection() {
        int max = 1;
        if (selectedFrom != null && selectedTo != null) {
            char player = controlledPlayer();
            if (game.canAttack(selectedFrom, selectedTo, player)) {
                max = game.maxAttackers(selectedFrom);
            } else if (game.canFortify(selectedFrom, selectedTo, player)) {
                max = game.maxMovableSoldiers(selectedFrom);
            }
        } else if (selectedFrom != null) {
            max = Math.max(1, game.maxMovableSoldiers(selectedFrom));
        }
        soldierSpinner.setModel(new SpinnerNumberModel(max, 1, max, 1));
    }

    private void refreshAfterStep(SimulationStep step) {
        if (step.boardChanged()) {
            refreshBoard();
        }
        refreshStatus();
    }

    private void updateSpeed() {
        String selectedSpeed = (String) speedBox.getSelectedItem();
        if ("Sehr schnell".equals(selectedSpeed)) {
            simulationTimer.setDelay(60);
        } else if ("Schnell".equals(selectedSpeed)) {
            simulationTimer.setDelay(120);
        } else {
            simulationTimer.setDelay(250);
        }
    }

    private void handlePlayerModeChange() {
        clearSelection();
        if (isHumanControlledTurn()) {
            simulationTimer.stop();
            startPauseButton.setText("Start");
            log("Du uebernimmst " + Game.playerName(controlledPlayer()) + ". Diese Farbe ist gerade am Zug.");
        }
        refresh();
    }

    private void refresh() {
        refreshBoard();
        refreshStatus();
    }

    private void refreshBoard() {
        int size = game.getSize();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Position position = new Position(row, col);
                Field field = game.getField(position);
                JButton button = fieldButtons[row][col];

                button.setText("<html><center>" + field.getOwner() + "<br>" + field.getSoldiers() + "</center></html>");
                button.setBackground(playerColor(field.getOwner()));
                button.setForeground(Color.WHITE);
                button.setOpaque(true);
                button.setBorder(BorderFactory.createLineBorder(borderColor(position), borderWidth(position)));
            }
        }
    }

    private void refreshStatus() {
        startPauseButton.setEnabled(!game.hasWinner());
        stepButton.setEnabled(!game.hasWinner() && !simulationTimer.isRunning() && !isHumanControlledTurn());
        skipButton.setEnabled(!game.hasWinner() && controlledPlayer() != '-' && !isHumanControlledTurn());
        attackButton.setEnabled(isHumanControlledTurn()
                && selectedFrom != null
                && selectedTo != null
                && game.canAttack(selectedFrom, selectedTo, controlledPlayer()));
        fortifyButton.setEnabled(isHumanControlledTurn()
                && selectedFrom != null
                && selectedTo != null
                && game.canFortify(selectedFrom, selectedTo, controlledPlayer()));
        endTurnButton.setEnabled(isHumanControlledTurn() && game.getPendingReinforcements() == 0);

        if (game.hasWinner()) {
            statusLabel.setText(Game.playerName(game.winner()) + " hat gewonnen!");
        } else {
            String mode = controlledPlayer() == '-' ? "Nur Simulation" : "Du bist " + Game.playerName(controlledPlayer());
            statusLabel.setText("<html>" + mode + " | " + game.getSize() + "x" + game.getSize() + " | Runde " + game.getRound()
                    + " | Am Zug: " + Game.playerName(game.getCurrentPlayer())
                    + " | Verstaerkungen uebrig: " + game.getPendingReinforcements()
                    + " | Angriffe: " + game.getAttacksThisTurn() + "/" + game.maxAttacksPerTurn()
                    + "<br>" + playerSummary() + "</html>");
        }
    }

    private boolean isHumanControlledTurn() {
        char player = controlledPlayer();
        return player != '-' && !game.hasWinner() && game.getCurrentPlayer() == player;
    }

    private char controlledPlayer() {
        String selected = (String) playerBox.getSelectedItem();
        if ("Blau".equals(selected)) {
            return 'A';
        }
        if ("Rot".equals(selected)) {
            return 'B';
        }
        if ("Gruen".equals(selected)) {
            return 'C';
        }
        if ("Gelb".equals(selected)) {
            return 'D';
        }
        return '-';
    }

    private String playerSummary() {
        StringBuilder summary = new StringBuilder("Gesamt: ");
        for (char player : Game.PLAYERS) {
            if (summary.length() > "Gesamt: ".length()) {
                summary.append(" | ");
            }
            summary.append(Game.playerName(player))
                    .append(": ")
                    .append(game.totalSoldiers(player))
                    .append(" Soldaten, ")
                    .append(game.controlledFields(player))
                    .append(" Felder");
        }
        return summary.toString();
    }

    private int fontSizeForBoard(int size) {
        if (size >= 16) {
            return 10;
        }
        if (size >= 12) {
            return 11;
        }
        if (size >= 8) {
            return 13;
        }
        return 20;
    }

    private Color playerColor(char player) {
        return switch (player) {
            case 'A' -> new Color(35, 105, 190);
            case 'B' -> new Color(190, 65, 65);
            case 'C' -> new Color(55, 145, 85);
            case 'D' -> new Color(210, 150, 45);
            default -> Color.GRAY;
        };
    }

    private Color borderColor(Position position) {
        if (position.equals(highlightedFrom)) {
            return Color.WHITE;
        }
        if (position.equals(highlightedTo)) {
            return Color.BLACK;
        }
        if (position.row() == game.getSize() / 2 - 1 || position.col() == game.getSize() / 2 - 1) {
            return Color.DARK_GRAY;
        }
        return Color.LIGHT_GRAY;
    }

    private int borderWidth(Position position) {
        if (position.equals(highlightedFrom) || position.equals(highlightedTo)) {
            return 4;
        }
        if (position.row() == game.getSize() / 2 - 1 || position.col() == game.getSize() / 2 - 1) {
            return 3;
        }
        return 1;
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this,
                "Das Spiel laeuft als Simulation.\n"
                        + "Mitspielen: Waehle eine Farbe oder Nur Simulation.\n"
                        + "Skip andere: Ueberspringt die Zuege der anderen Farben bis du wieder dran bist.\n"
                        + "Im eigenen Zug: eigene Felder anklicken, dann Angreifen, Verschieben oder Zug beenden.\n"
                        + "Start: Automatisch Schritt fuer Schritt laufen lassen.\n"
                        + "Pause: Simulation anhalten.\n"
                        + "Ein Schritt: Genau ein Ereignis ausfuehren.\n"
                        + "Feldgroesse: Groesse waehlen und mit Neu starten anwenden.\n"
                        + "Das Spielfeld wird nur nach Angriffen neu gezeichnet.\n"
                        + "Weisser Rand: Angreifendes Feld.\n"
                        + "Schwarzer Rand: Ziel oder betroffenes Feld.",
                "Simulation",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWinner() {
        JOptionPane.showMessageDialog(this,
                Game.playerName(game.winner()) + " hat gewonnen!",
                "Simulation beendet",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void log(String message) {
        logArea.append(message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
