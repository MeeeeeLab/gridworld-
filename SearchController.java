import java.util.ArrayDeque;
import java.util.Deque;
public class SearchController {
    private GridMap grid;
    private Agent agent;//agent对象
    private boolean[][] visited;
    private Deque<Agent> path;
    private int goalRow;
    private int goalCol;
    private int steps;
    private int expCount;
    private int backCount;
    private int failCount;
    public SearchController(GridMap grid, int goalRow, int goalCol) {
        this.grid = grid;
       this.agent = new Agent(0, 0);
       this.visited = new boolean[grid.getRows()][];
       for (int i = 0; i < grid.getRows(); i++) {
           visited[i] = new boolean[grid.getRowLength(i)];
       }
       this.visited[0][0] = true;
       this.path = new ArrayDeque<>();
       path.push(new Agent(agent.row, agent.col));//新建agent对象，两个独立对象，初始坐标相同
       this.goalRow = goalRow;
       this.goalCol = goalCol;
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
         if(oldRow==0&&oldCol==0&&(agent.row!=0||agent.col!=0)){
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
         if(prev.row==0&&prev.col==0&&(agent.row!=0||agent.col!=0)){
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
