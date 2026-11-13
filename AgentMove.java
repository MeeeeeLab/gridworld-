public class AgentMove {
     public static void moveAgent(GridMap grid,Agent agent, char direction){//移动
        int row = agent.row;
        int col = agent.col;

        if(direction=='U'){
            if(grid.isWalkable(row-1,col)){
                grid.setCell(row, col, '.');
                grid.setCell(row-1, col, 'A');
                agent.row = row-1;
            }
        }
        if(direction=='D'){
            if(grid.isWalkable(row+1,col)){
                grid.setCell(row, col, '.');
                grid.setCell(row+1, col, 'A');
                agent.row = row+1;
            }
        }
        if(direction=='L'){
            if(grid.isWalkable(row, col-1)){
                grid.setCell(row, col, '.');
                grid.setCell(row, col-1, 'A');
                agent.col = col-1;
            }
        }
        if(direction=='R'){
            if(grid.isWalkable(row, col+1)){
                grid.setCell(row, col, '.');
                grid.setCell(row, col+1, 'A');
                agent.col = col+1;
            }
        }
    }
}
