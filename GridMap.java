public class GridMap {
    private char[][] grid;
    public GridMap(char[][] grid) {
        this.grid = grid;
    }
    public int getRows(){
        return grid.length;
    }
   public boolean inBounds(int row, int col){
    return row >= 0 && row < getRows() && col >= 0 && col < grid[row].length;
   }
   public boolean isWalkable(int row, int col){
    return inBounds(row, col) && grid[row][col] != '#';
   }
   public int getRowLength(int row){
    return grid[row].length;
   }
   public void setCell(int row, int col, char value){
    grid[row][col] = value;
   }
   }


