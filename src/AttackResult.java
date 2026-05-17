public record AttackResult(boolean success, int chancePercent, Position from, Position to, char attacker,
                           String defenseStatus) {
    public String message() {
        String defenseText = defenseStatus.isEmpty() ? "" : " " + defenseStatus;
        if (success) {
            return Game.playerName(attacker) + " erobert Feld " + to.label() + " mit " + chancePercent
                    + "% Chance." + defenseText;
        }
        return "Angriff von " + Game.playerName(attacker) + " auf " + to.label() + " scheitert trotz "
                + chancePercent + "% Chance." + defenseText;
    }
}
