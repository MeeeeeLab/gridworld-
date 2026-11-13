import java.util.ArrayDeque;
import java.util.Deque;
public class SearchController {
    private GridMap grid;
    private Agent agent;//agent对象
    private boolean[][] visited;
    private Deque<Agent> path;
    private int goalRow;
    private int goalCol;
    private int startRow;
    private int startCol;
    private int steps;
    private int expCount;
    private int backCount;
    private int failCount;
    public SearchController(GridMap grid, int goalRow, int goalCol) {
        this(grid, 0, 0, goalRow, goalCol);
    }
    public SearchController(GridMap grid, int startRow, int startCol, int goalRow, int goalCol) {
        this.grid = grid;
       if (!grid.isWalkable(startRow, startCol) || !grid.isWalkable(goalRow, goalCol)) {
           throw new IllegalArgumentException("Start and goal must be walkable");
       }
       this.agent = new Agent(startRow, startCol);
       this.visited = new boolean[grid.getRows()][];
       for (int i = 0; i < grid.getRows(); i++) {
           visited[i] = new boolean[grid.getRowLength(i)];
       }
       this.visited[startRow][startCol] = true;
       this.path = new ArrayDeque<>();
       path.push(new Agent(agent.row, agent.col));//新建agent对象，两个独立对象，初始坐标相同
       this.goalRow = goalRow;
       this.goalCol = goalCol;
       this.startRow = startRow;
       this.startCol = startCol;
    }
    /** Current DFS path, from the actual start to the current position. */
    public int[][] getPath() {
        int[][] result = new int[path.size()][2];
        int i = 0;
        for (var it = path.descendingIterator(); it.hasNext();) {
            Agent cell = it.next();
            result[i++] = new int[]{cell.row, cell.col};
        }
        return result;
    }
     public Agent getAgent() {
    return agent;
}
     public int getPathLength() {
    return path.size() - 1;
}
     public boolean isGoal(){
        return agent.row == goalRow && agent.col == goalCol;
     }
     public SearchStatus nextStep() {
      if(isGoal()){
         return SearchStatus.SUCCESS;
      }
      char dir=PathFinder.chooseDirection(grid, agent, goalRow, goalCol,visited);
      
      if(dir!='X'){
         int oldRow = agent.row;
         int oldCol = agent.col;
         AgentMove.moveAgent(grid ,agent,dir);
         visited[agent.row][agent.col]=true;
         path.push(new Agent(agent.row, agent.col));
         steps++;
         if(oldRow==startRow&&oldCol==startCol&&(agent.row!=startRow||agent.col!=startCol)){
            expCount++;
         }
         return SearchStatus.RUNNING;
      } 
      else{
         if(path.size()==1){
            return SearchStatus.FAILED;
        }
        else {
         path.pop();
         Agent prev=path.peek();
        grid.setCell(agent.row, agent.col, '.');
         backCount++;
         steps++;
         if(prev.row==startRow&&prev.col==startCol&&(agent.row!=startRow||agent.col!=startCol)){
            failCount++;
         }
         agent.row=prev.row;
         agent.col=prev.col;
         grid.setCell(agent.row, agent.col, 'A');
        
         return SearchStatus.RUNNING;
        }
         }
      }
       public int getSteps(){
         return steps;
      }
      public int getExpCount(){
         return expCount;
      }
      public int getBackCount(){
         return backCount;
      }
      public int getFailCount(){
         return failCount;
      }
   
}
