public record Position(int row, int col) {
    public String label() {
        return "(" + (row + 1) + "," + (col + 1) + ")";
    }
}
