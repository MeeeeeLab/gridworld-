public class PathFinder {
      public static char chooseDirection(GridMap grid, Agent agent, int goalRow, int goalCol,boolean [][]visited){ //自主决策何时距离最短
        int row = agent.row;
        int col = agent.col;
        int ldis=Integer.MAX_VALUE;
        int udis=Integer.MAX_VALUE;
        int rdis=Integer.MAX_VALUE;
        int ddis=Integer.MAX_VALUE;
        int minDistance = Integer.MAX_VALUE;
        char bestdirection = 'X';
        if (grid.isWalkable( agent.row, agent.col + 1)&&!visited[agent.row][agent.col + 1]) 
{
     rdis = distance(agent, goalRow, goalCol - 1);
}
        if (grid.isWalkable(agent.row,agent.col-1)&&!visited[agent.row][agent.col - 1]) {
             ldis = distance(agent, goalRow, goalCol + 1);
        }
        if ( grid.isWalkable(agent.row+1, agent.col)&&!visited[agent.row + 1][agent.col]) {
            ddis = distance(agent, goalRow - 1, goalCol);
        }
        if ( grid.isWalkable(agent.row-1, agent.col)&&!visited[agent.row - 1][agent.col]) {
             udis = distance(agent, goalRow + 1, goalCol);
        }
        minDistance = Math.min(Math.min(rdis, ddis),
                           Math.min(ldis, udis));
        if(minDistance==Integer.MAX_VALUE){
            return 'X';
        }
        if (minDistance == rdis) {
            bestdirection = 'R';
        } else if (minDistance == ddis) {
            bestdirection = 'D';}
        else if (minDistance == ldis) {
            bestdirection = 'L';
        } else if (minDistance ==udis) {
            bestdirection = 'U';
        }
        return bestdirection;
    }
    public static int distance(Agent agent, int goalRow, int goalCol){
        int row = agent.row;
        int col = agent.col;
        int distance=((row-goalRow)*(row-goalRow)+(col-goalCol)*(col-goalCol));
        return distance;
    }
}   