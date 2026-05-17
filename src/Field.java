public class Field {
    private char owner;
    private int soldiers;

    public Field(char owner, int soldiers) {
        this.owner = owner;
        this.soldiers = soldiers;
    }

    public char getOwner() {
        return owner;
    }

    public void setOwner(char owner) {
        this.owner = owner;
    }

    public int getSoldiers() {
        return soldiers;
    }

    public void setSoldiers(int soldiers) {
        this.soldiers = soldiers;
    }

    public void addSoldier() {
        soldiers++;
    }
}
