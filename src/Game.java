import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Game {
    public static final int DEFAULT_SIZE = 8;
    public static final char[] PLAYERS = {'A', 'B', 'C', 'D'};

    private static final int START_SOLDIERS = 5;
    private static final double MIN_AI_ATTACK_CHANCE = 0.55;
    private static final double MIN_AI_LEADER_ATTACK_CHANCE = 0.45;
    private static final double ISOLATED_DEFENSE_FACTOR = 0.75;
    private static final double CUT_OFF_DEFENSE_FACTOR = 0.85;
    private static final double FIELD_REINFORCEMENT_CAP_RATIO = 0.50;
    private static final double LEADER_FIELD_RATIO = 0.50;
    private static final double VULNERABLE_FIELD_RATIO = 0.10;
    private static final int LEADER_ATTACK_SCORE_BONUS = 12;
    private static final int WEAK_TARGET_ATTACK_SCORE_BONUS = 10;
    private static final int WEAK_TARGET_ELIMINATION_BONUS = 2;

    private final int size;
    private final Field[][] board;
    private final Random random = new Random();
    private final int[] nextTurnBonusSoldiers = new int[PLAYERS.length];

    private int currentPlayerIndex;
    private int roundStartIndex;
    private int turnOffset;
    private int pendingReinforcements;
    private int attacksThisTurn;
    private boolean fortificationDone;
    private int round = 1;

    public Game() {
        this(DEFAULT_SIZE);
    }

    public Game(int size) {
        if (size < 4 || size % 2 != 0) {
            throw new IllegalArgumentException("Die Feldgroesse muss gerade und mindestens 4 sein.");
        }
        this.size = size;
        board = new Field[size][size];
        setupBoard();
        startTurn();
    }

    public static String playerName(char player) {
        return switch (player) {
            case 'A' -> "Blau";
            case 'B' -> "Rot";
            case 'C' -> "Gruen";
            case 'D' -> "Gelb";
            default -> "Unbekannt";
        };
    }

    private static int playerIndex(char player) {
        for (int i = 0; i < PLAYERS.length; i++) {
            if (PLAYERS[i] == player) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unbekannter Spieler: " + player);
    }

    public int getSize() {
        return size;
    }

    public Field getField(Position position) {
        return board[position.row()][position.col()];
    }

    public char getCurrentPlayer() {
        return PLAYERS[currentPlayerIndex];
    }

    public int getPendingReinforcements() {
        return pendingReinforcements;
    }

    public int getAttacksThisTurn() {
        return attacksThisTurn;
    }

    public int maxAttacksPerTurn() {
        return maxAttacksFor(getCurrentPlayer());
    }

    private int maxAttacksFor(char player) {
        int fields = countFields(player);
        int attacks = 1 + fields / Math.max(2, size / 2);
        if (size <= 4) {
            return Math.min(attacks, 3);
        }
        if (size <= 12) {
            return Math.min(attacks, 6);
        }
        return Math.min(attacks, 8);
    }

    public boolean isFortificationDone() {
        return fortificationDone;
    }

    public int getRound() {
        return round;
    }

    public boolean hasWinner() {
        return winner() != '-';
    }

    public int totalSoldiers(char player) {
        int total = 0;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col].getOwner() == player) {
                    total += board[row][col].getSoldiers();
                }
            }
        }
        return total;
    }

    public int controlledFields(char player) {
        return countFields(player);
    }

    public char winner() {
        for (char player : PLAYERS) {
            if (countFields(player) == size * size) {
                return player;
            }
        }
        return '-';
    }

    public SimulationStep stepSimulation() {
        if (hasWinner()) {
            return new SimulationStep("Spiel beendet: " + playerName(winner()) + " hat gewonnen.", null, null, false);
        }

        char player = getCurrentPlayer();
        if (pendingReinforcements > 0) {
            Position position = reinforcementTarget(player);
            getField(position).addSoldier();
            pendingReinforcements--;
            String message = "Runde " + round + ", " + playerName(player) + ": Verstaerkung auf "
                    + position.label() + ". Noch " + pendingReinforcements + " uebrig.";
            return new SimulationStep(message, position, null, false);
        }

        List<Attack> attacks = worthwhileAttacks(player);
        if (attacksThisTurn < maxAttacksPerTurn() && !attacks.isEmpty()) {
            Attack attack = chooseBestAttack(attacks);
            int attackers = maxAttackers(attack.from());
            AttackResult result = attack(attack.from(), attack.to(), attackers);
            String message = "Runde " + round + ", " + playerName(player) + ": Angriff von "
                    + attack.from().label() + " nach " + attack.to().label() + ". " + result.message();
            return new SimulationStep(message, attack.from(), attack.to(), true);
        }

        if (!fortificationDone) {
            fortificationDone = true;
            SimulationStep fortification = fortify(player);
            if (fortification != null) {
                return fortification;
            }
        }

        char finishedPlayer = player;
        advanceToNextLivingPlayer();
        String message = playerName(finishedPlayer) + " hat keine Angriffe mehr und beendet den Zug. "
                + playerName(getCurrentPlayer()) + " ist jetzt dran und bekommt " + pendingReinforcements + " Soldaten.";
        return new SimulationStep(message, null, null, false);
    }

    public boolean canPlaceReinforcement(Position position, char player) {
        return getCurrentPlayer() == player
                && pendingReinforcements > 0
                && getField(position).getOwner() == player;
    }

    public SimulationStep placeReinforcement(Position position, char player) {
        if (!canPlaceReinforcement(position, player)) {
            throw new IllegalArgumentException("Hier kann keine Verstaerkung gesetzt werden.");
        }

        getField(position).addSoldier();
        pendingReinforcements--;
        String message = "Runde " + round + ", " + playerName(player) + ": Verstaerkung auf "
                + position.label() + ". Noch " + pendingReinforcements + " uebrig.";
        return new SimulationStep(message, position, null, true);
    }

    public boolean canAttack(Position from, Position to, char player) {
        return getCurrentPlayer() == player
                && pendingReinforcements == 0
                && attacksThisTurn < maxAttacksPerTurn()
                && getField(from).getOwner() == player
                && getField(to).getOwner() != player
                && getField(from).getSoldiers() > 1
                && areNeighbors(from, to);
    }

    public int maxAttackers(Position from) {
        return Math.max(0, getField(from).getSoldiers() - 1);
    }

    public SimulationStep playerAttack(Position from, Position to, int attackers, char player) {
        if (!canAttack(from, to, player)) {
            throw new IllegalArgumentException("Dieser Angriff ist nicht moeglich.");
        }
        if (attackers < 1 || attackers > maxAttackers(from)) {
            throw new IllegalArgumentException("Diese Angreiferzahl ist nicht moeglich.");
        }

        AttackResult result = attack(from, to, attackers);
        String message = "Runde " + round + ", " + playerName(player) + ": Angriff von "
                + from.label() + " nach " + to.label() + ". " + result.message();
        return new SimulationStep(message, from, to, true);
    }

    public boolean canFortify(Position from, Position to, char player) {
        return getCurrentPlayer() == player
                && pendingReinforcements == 0
                && !fortificationDone
                && !from.equals(to)
                && getField(from).getOwner() == player
                && getField(to).getOwner() == player
                && getField(from).getSoldiers() > 1
                && connectedByOwnFields(from, to, player);
    }

    public SimulationStep playerFortify(Position from, Position to, int soldiers, char player) {
        if (!canFortify(from, to, player)) {
            throw new IllegalArgumentException("Diese Verschiebung ist nicht moeglich.");
        }

        int maxSoldiers = maxMovableSoldiers(from);
        if (soldiers < 1 || soldiers > maxSoldiers) {
            throw new IllegalArgumentException("Diese Soldatenzahl ist nicht moeglich.");
        }

        getField(from).setSoldiers(getField(from).getSoldiers() - soldiers);
        getField(to).setSoldiers(getField(to).getSoldiers() + soldiers);
        fortificationDone = true;

        String message = "Runde " + round + ", " + playerName(player) + ": Verschiebt " + soldiers
                + " Soldaten von " + from.label() + " nach " + to.label() + ".";
        return new SimulationStep(message, from, to, true);
    }

    public int maxMovableSoldiers(Position from) {
        return Math.max(0, getField(from).getSoldiers() - 1);
    }

    public SimulationStep finishCurrentTurn() {
        char finishedPlayer = getCurrentPlayer();
        advanceToNextLivingPlayer();
        String message = playerName(finishedPlayer) + " beendet den Zug. "
                + playerName(getCurrentPlayer()) + " ist jetzt dran und bekommt " + pendingReinforcements + " Soldaten.";
        return new SimulationStep(message, null, null, false);
    }

    private AttackResult attack(Position from, Position to, int attackers) {
        Field source = getField(from);
        Field target = getField(to);
        char attacker = source.getOwner();
        char defender = target.getOwner();
        int defenders = target.getSoldiers();
        boolean weakTargetAttack = isAttackAgainstWeakTarget(new Attack(from, to));
        double effectiveDefenders = effectiveDefenders(to);
        double chance = attackers / (attackers + effectiveDefenders);
        boolean success = random.nextDouble() < chance;

        source.setSoldiers(source.getSoldiers() - attackers);
        if (success) {
            target.setOwner(attacker);
            target.setSoldiers(Math.max(2, attackers - defenders / 2));
            if (weakTargetAttack && countFields(defender) == 0) {
                nextTurnBonusSoldiers[playerIndex(attacker)] += WEAK_TARGET_ELIMINATION_BONUS;
            }
        } else {
            target.setSoldiers(Math.max(1, defenders - attackers / 2));
        }

        attacksThisTurn++;
        return new AttackResult(success, (int) Math.round(chance * 100), from, to, attacker, defenseStatus(to));
    }

    private Position reinforcementTarget(char player) {
        Position best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Position position = new Position(row, col);
                if (getField(position).getOwner() == player) {
                    int score = reinforcementScore(position, player);
                    if (score > bestScore) {
                        bestScore = score;
                        best = position;
                    }
                }
            }
        }

        return best;
    }

    private SimulationStep fortify(char player) {
        Position target = reinforcementTarget(player);
        Position source = fortificationSource(player, target);

        if (source == null) {
            return new SimulationStep("Runde " + round + ", " + playerName(player)
                    + ": Keine verbundene Verstaerkung moeglich.", null, null, false);
        }

        Field sourceField = getField(source);
        Field targetField = getField(target);
        int movedSoldiers = Math.max(1, (sourceField.getSoldiers() - 1) / 2);

        sourceField.setSoldiers(sourceField.getSoldiers() - movedSoldiers);
        targetField.setSoldiers(targetField.getSoldiers() + movedSoldiers);

        String message = "Runde " + round + ", " + playerName(player) + ": Sendet " + movedSoldiers
                + " Soldaten von " + source.label() + " nach " + target.label()
                + ", weil beide Felder verbunden sind.";
        return new SimulationStep(message, source, target, false);
    }

    private Position fortificationSource(char player, Position target) {
        Position best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Position position = new Position(row, col);
                Field field = getField(position);
                if (!position.equals(target)
                        && field.getOwner() == player
                        && field.getSoldiers() > 3
                        && connectedByOwnFields(position, target, player)) {
                    int score = field.getSoldiers() - countEnemyNeighbors(position, player) * 4;
                    if (score > bestScore) {
                        bestScore = score;
                        best = position;
                    }
                }
            }
        }

        return best;
    }

    private int reinforcementScore(Position position, char player) {
        Field field = getField(position);
        int enemyNeighbors = countEnemyNeighbors(position, player);
        int strongestEnemy = strongestEnemyNeighbor(position, player);
        int weakestEnemy = weakestEnemyNeighbor(position, player);
        int score = 0;

        if (enemyNeighbors > 0) {
            score += 40;
            score += enemyNeighbors * 12;
        }

        if (strongestEnemy >= field.getSoldiers()) {
            score += (strongestEnemy - field.getSoldiers() + 1) * 12;
        }

        if (weakestEnemy > 0 && field.getSoldiers() <= weakestEnemy + 2) {
            score += 14;
        }

        if (enemyNeighbors > 0 && field.getSoldiers() <= 3) {
            score += 20;
        }

        score += Math.max(0, 12 - field.getSoldiers()) * 3;
        score -= Math.max(0, field.getSoldiers() - 14) * 4;

        if (enemyNeighbors == 0) {
            score -= 30;
        }

        score += random.nextInt(5);
        return score;
    }

    private int countEnemyNeighbors(Position position, char player) {
        int count = 0;
        for (Position neighbor : neighbors(position)) {
            if (getField(neighbor).getOwner() != player) {
                count++;
            }
        }
        return count;
    }

    private int strongestEnemyNeighbor(Position position, char player) {
        int strongest = 0;
        for (Position neighbor : neighbors(position)) {
            Field field = getField(neighbor);
            if (field.getOwner() != player && field.getSoldiers() > strongest) {
                strongest = field.getSoldiers();
            }
        }
        return strongest;
    }

    private int weakestEnemyNeighbor(Position position, char player) {
        int weakest = Integer.MAX_VALUE;
        for (Position neighbor : neighbors(position)) {
            Field field = getField(neighbor);
            if (field.getOwner() != player && field.getSoldiers() < weakest) {
                weakest = field.getSoldiers();
            }
        }
        return weakest == Integer.MAX_VALUE ? 0 : weakest;
    }

    private Attack chooseBestAttack(List<Attack> attacks) {
        Attack bestAttack = attacks.get(0);
        for (Attack attack : attacks) {
            if (attackScore(attack) > attackScore(bestAttack)) {
                bestAttack = attack;
            }
        }
        return bestAttack;
    }

    private void startTurn() {
        char player = getCurrentPlayer();
        pendingReinforcements = fieldReinforcements(player)
                + countControlledQuadrants(player) * quadrantBonus()
                + comebackBonus(player)
                + firstRoundOffsetBonus()
                + consumeNextTurnBonus(player);
        attacksThisTurn = 0;
        fortificationDone = false;
    }

    private int consumeNextTurnBonus(char player) {
        int playerIndex = playerIndex(player);
        int bonus = nextTurnBonusSoldiers[playerIndex];
        nextTurnBonusSoldiers[playerIndex] = 0;
        return bonus;
    }

    private int fieldReinforcements(char player) {
        int cappedFields = Math.min(countFields(player), fieldReinforcementCap());
        return Math.max(3, cappedFields / 2);
    }

    private int fieldReinforcementCap() {
        return Math.max(4, (int) Math.round(size * size * FIELD_REINFORCEMENT_CAP_RATIO));
    }

    private int quadrantBonus() {
        return Math.max(1, size / 4);
    }

    private int comebackBonus(char player) {
        int playerFields = countFields(player);
        int leaderFields = 0;
        for (char candidate : PLAYERS) {
            leaderFields = Math.max(leaderFields, countFields(candidate));
        }

        int gap = leaderFields - playerFields;
        if (gap >= Math.max(2, size / 2)) {
            return 2;
        }
        if (gap >= Math.max(1, size / 4)) {
            return 1;
        }
        return 0;
    }

    private int firstRoundOffsetBonus() {
        return round == 1 ? currentPlayerIndex : 0;
    }

    private void advanceToNextLivingPlayer() {
        do {
            turnOffset++;
            if (turnOffset >= PLAYERS.length) {
                round++;
                roundStartIndex = (roundStartIndex + 1) % PLAYERS.length;
                turnOffset = 0;
            }
            currentPlayerIndex = (roundStartIndex + turnOffset) % PLAYERS.length;
        } while (!hasWinner() && countFields(getCurrentPlayer()) == 0);

        if (!hasWinner()) {
            startTurn();
        }
    }

    private void setupBoard() {
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                board[row][col] = new Field(startOwner(row, col), START_SOLDIERS);
            }
        }
    }

    private char startOwner(int row, int col) {
        if (row < size / 2 && col < size / 2) {
            return 'A';
        }
        if (row < size / 2) {
            return 'B';
        }
        if (col < size / 2) {
            return 'C';
        }
        return 'D';
    }

    private int attackScore(Attack attack) {
        Field from = getField(attack.from());
        Field to = getField(attack.to());
        int chanceScore = from.getSoldiers() - to.getSoldiers();
        int weakTargetScore = Math.max(0, 8 - to.getSoldiers());
        int quadrantScore = wouldCompleteQuadrant(attack.from(), attack.to()) ? 5 : 0;
        int leaderScore = isAttackAgainstDominantPlayer(attack) ? LEADER_ATTACK_SCORE_BONUS : 0;
        int weakPlayerScore = isAttackAgainstWeakTarget(attack) ? WEAK_TARGET_ATTACK_SCORE_BONUS : 0;
        return chanceScore + weakTargetScore + quadrantScore + leaderScore + weakPlayerScore;
    }

    private double attackChance(Attack attack) {
        int attackers = maxAttackers(attack.from());
        return attackers / (attackers + effectiveDefenders(attack.to()));
    }

    private List<Attack> worthwhileAttacks(char player) {
        List<Attack> worthwhile = new ArrayList<>();
        for (Attack attack : possibleAttacks(player)) {
            double minimumChance = minimumAttackChance(attack);
            if (attackChance(attack) >= minimumChance || wouldCompleteQuadrant(attack.from(), attack.to())) {
                worthwhile.add(attack);
            }
        }
        return worthwhile;
    }

    private double minimumAttackChance(Attack attack) {
        double minimumChance = MIN_AI_ATTACK_CHANCE;
        if (isAttackAgainstDominantPlayer(attack)) {
            minimumChance = Math.min(minimumChance, MIN_AI_LEADER_ATTACK_CHANCE);
        }
        if (isAttackAgainstWeakTarget(attack)) {
            minimumChance = Math.min(minimumChance, MIN_AI_LEADER_ATTACK_CHANCE);
        }
        return minimumChance;
    }

    private boolean isAttackAgainstDominantPlayer(Attack attack) {
        char dominantPlayer = dominantPlayer();
        return dominantPlayer != '-'
                && getField(attack.from()).getOwner() != dominantPlayer
                && getField(attack.to()).getOwner() == dominantPlayer;
    }

    private char dominantPlayer() {
        int totalFields = size * size;
        for (char player : PLAYERS) {
            if (countFields(player) > totalFields * LEADER_FIELD_RATIO) {
                return player;
            }
        }
        return '-';
    }

    private boolean isAttackAgainstWeakTarget(Attack attack) {
        char attacker = getField(attack.from()).getOwner();
        char target = weakestVulnerablePlayer(attacker);
        return target != '-' && getField(attack.to()).getOwner() == target;
    }

    private char weakestVulnerablePlayer(char attacker) {
        if (attacker != strongestPlayerBySoldiers()) {
            return '-';
        }

        int maximumFields = Math.max(1, (int) Math.floor(size * size * VULNERABLE_FIELD_RATIO));
        char weakestPlayer = '-';
        int weakestFields = Integer.MAX_VALUE;
        for (char player : PLAYERS) {
            int fields = countFields(player);
            if (player != attacker && fields > 0 && fields <= maximumFields && fields < weakestFields) {
                weakestPlayer = player;
                weakestFields = fields;
            }
        }
        return weakestPlayer;
    }

    private char strongestPlayerBySoldiers() {
        char strongestPlayer = '-';
        int strongestSoldiers = -1;
        for (char player : PLAYERS) {
            int fields = countFields(player);
            int soldiers = totalSoldiers(player);
            if (fields > 0 && soldiers > strongestSoldiers) {
                strongestPlayer = player;
                strongestSoldiers = soldiers;
            }
        }
        return strongestPlayer;
    }

    private double effectiveDefenders(Position position) {
        Field target = getField(position);
        if (isIsolatedField(position)) {
            return target.getSoldiers() * ISOLATED_DEFENSE_FACTOR;
        }
        if (isCutOffFromMainRegion(position)) {
            return target.getSoldiers() * CUT_OFF_DEFENSE_FACTOR;
        }
        return target.getSoldiers();
    }

    private boolean isIsolatedField(Position position) {
        char owner = getField(position).getOwner();
        for (Position neighbor : neighbors(position)) {
            if (getField(neighbor).getOwner() == owner) {
                return false;
            }
        }
        return true;
    }

    private boolean isCutOffFromMainRegion(Position position) {
        char owner = getField(position).getOwner();
        boolean[][] mainRegion = mainRegion(owner);
        return !mainRegion[position.row()][position.col()];
    }

    private String defenseStatus(Position position) {
        if (isIsolatedField(position)) {
            return "Das Ziel war ein isoliertes Feld.";
        }
        if (isCutOffFromMainRegion(position)) {
            return "Das Ziel war vom Hauptgebiet abgeschnitten.";
        }
        return "";
    }

    private boolean[][] mainRegion(char player) {
        boolean[][] visited = new boolean[size][size];
        boolean[][] largestRegion = new boolean[size][size];
        int largestRegionSize = 0;

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Position start = new Position(row, col);
                if (!visited[row][col] && getField(start).getOwner() == player) {
                    List<Position> region = collectRegion(start, player, visited);
                    if (region.size() > largestRegionSize) {
                        largestRegionSize = region.size();
                        largestRegion = toRegionMap(region);
                    }
                }
            }
        }

        return largestRegion;
    }

    private List<Position> collectRegion(Position start, char player, boolean[][] visited) {
        List<Position> region = new ArrayList<>();
        List<Position> open = new ArrayList<>();
        open.add(start);
        visited[start.row()][start.col()] = true;

        for (int i = 0; i < open.size(); i++) {
            Position current = open.get(i);
            region.add(current);

            for (Position neighbor : neighbors(current)) {
                if (!visited[neighbor.row()][neighbor.col()] && getField(neighbor).getOwner() == player) {
                    visited[neighbor.row()][neighbor.col()] = true;
                    open.add(neighbor);
                }
            }
        }

        return region;
    }

    private boolean[][] toRegionMap(List<Position> region) {
        boolean[][] regionMap = new boolean[size][size];
        for (Position position : region) {
            regionMap[position.row()][position.col()] = true;
        }
        return regionMap;
    }

    private boolean wouldCompleteQuadrant(Position from, Position to) {
        char attacker = getField(from).getOwner();
        int rowStart = to.row() < size / 2 ? 0 : size / 2;
        int colStart = to.col() < size / 2 ? 0 : size / 2;

        for (int row = rowStart; row < rowStart + size / 2; row++) {
            for (int col = colStart; col < colStart + size / 2; col++) {
                if (row == to.row() && col == to.col()) {
                    continue;
                }
                if (board[row][col].getOwner() != attacker) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<Attack> possibleAttacks(char player) {
        List<Attack> attacks = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Position from = new Position(row, col);
                Field field = getField(from);
                if (field.getOwner() == player && field.getSoldiers() > 1) {
                    addAttacksFrom(attacks, from, player);
                }
            }
        }
        return attacks;
    }

    private void addAttacksFrom(List<Attack> attacks, Position from, char player) {
        for (Position to : neighbors(from)) {
            if (getField(to).getOwner() != player) {
                attacks.add(new Attack(from, to));
            }
        }
    }

    private List<Position> neighbors(Position position) {
        List<Position> neighbors = new ArrayList<>();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int row = position.row() + direction[0];
            int col = position.col() + direction[1];
            if (row >= 0 && row < size && col >= 0 && col < size) {
                neighbors.add(new Position(row, col));
            }
        }
        return neighbors;
    }

    private boolean areNeighbors(Position a, Position b) {
        return Math.abs(a.row() - b.row()) + Math.abs(a.col() - b.col()) == 1;
    }

    private boolean connectedByOwnFields(Position from, Position to, char player) {
        boolean[][] visited = new boolean[size][size];
        List<Position> open = new ArrayList<>();
        open.add(from);
        visited[from.row()][from.col()] = true;

        for (int i = 0; i < open.size(); i++) {
            Position current = open.get(i);
            if (current.equals(to)) {
                return true;
            }

            for (Position neighbor : neighbors(current)) {
                if (!visited[neighbor.row()][neighbor.col()] && getField(neighbor).getOwner() == player) {
                    visited[neighbor.row()][neighbor.col()] = true;
                    open.add(neighbor);
                }
            }
        }

        return false;
    }

    private Position firstOwnedPosition(char player) {
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Position position = new Position(row, col);
                if (getField(position).getOwner() == player) {
                    return position;
                }
            }
        }
        throw new IllegalStateException("Spieler hat keine Felder mehr.");
    }

    private int countFields(char player) {
        int count = 0;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col].getOwner() == player) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countControlledQuadrants(char player) {
        int controlledQuadrants = 0;
        for (int rowStart = 0; rowStart < size; rowStart += size / 2) {
            for (int colStart = 0; colStart < size; colStart += size / 2) {
                if (controlsQuadrant(player, rowStart, colStart)) {
                    controlledQuadrants++;
                }
            }
        }
        return controlledQuadrants;
    }

    private boolean controlsQuadrant(char player, int rowStart, int colStart) {
        for (int row = rowStart; row < rowStart + size / 2; row++) {
            for (int col = colStart; col < colStart + size / 2; col++) {
                if (board[row][col].getOwner() != player) {
                    return false;
                }
            }
        }
        return true;
    }
}
