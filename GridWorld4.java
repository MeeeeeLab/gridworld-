
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.GridLayout;
import javax.swing.JLabel;
import java.awt.Color;
import javax.swing.Timer;


public class GridWorld4 {
    public static void printGrid(char[][] grid) {
        for(int i=0;i<grid.length;i++){
            for(int j=0;j<grid[i].length;j++){
                System.out.print(grid[i][j]+" ");
            }
            System.out.println();
        }
    }

    public static void updateGrid(char[][]grid,JLabel[][] cells,int goalRow,int goalCol){//同步cells与grid
        for(int i=0;i<grid.length;i++){
            for(int j=0;j<grid[i].length;j++){
                cells[i][j].setText(String.valueOf(grid[i][j]));
             if (grid[i][j] == '#') {
    cells[i][j].setBackground(Color.DARK_GRAY);
} 
else if(grid[i][j] == 'A'&&i==goalRow&&j==goalCol){
    cells[i][j].setBackground(Color.GREEN);
}
else if (grid[i][j] == 'A') {
    cells[i][j].setBackground(Color.BLUE);
} else if (grid[i][j] == 'G') {
    cells[i][j].setBackground(Color.RED);
} else {
    cells[i][j].setBackground(Color.WHITE);
}
            }
        }
    }
    public static void main(String[] args) {
 char[][] grid = {
    {'A', '.', '.', '.', '.'},
    {'.', '#', '#', '.', '#'},
    {'.', '.', '.', '#', '.'},
    {'.', '.', '.', '.', '.'},
    {'.', '.', '.', '.', 'G'}
};
        
        printGrid(grid);
        JFrame frame = new JFrame("GridWorld");//创建窗口
frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

JPanel panel = new JPanel();//创建容器
JLabel[][] cells = new JLabel[grid.length][grid[0].length];//创建二维数组，类也可以作为数据类型
panel.setLayout(new GridLayout(5, 5));//设置网格排列方式

for (int i = 0; i < grid.length; i++) {
    for (int j = 0; j < grid[i].length; j++) {
        JLabel cell = new JLabel(//显示数组内容
            String.valueOf(grid[i][j]),//转为字符串
            JLabel.CENTER//居中显示
        );
        cell.setOpaque(true);//这个 JLabel 需要绘制自己的背景，否则即使设置了背景颜色，也可能看不到效果

if (grid[i][j] == '#') {
    cell.setBackground(Color.DARK_GRAY);
    cell.setForeground(Color.WHITE);
} 
else if (grid[i][j] == 'A') {
    cell.setBackground(Color.BLUE);
    cell.setForeground(Color.WHITE);
} 
else if (grid[i][j] == 'G') {
    cell.setBackground(Color.RED);
} 
else {
    cell.setBackground(Color.WHITE);
}
        cells[i][j] = cell;
        panel.add(cell);//把当前组件添加到画板上
    }
}

frame.add(panel);
frame.setSize(500, 500);
frame.setLocationRelativeTo(null);
frame.setVisible(true);
        int goalRow = 4;
        int goalCol = 4;
       SearchController controller = new SearchController(new GridMap(grid), goalRow, goalCol);
Timer timer = new Timer(500, e -> {
   SearchStatus status = controller.nextStep();

updateGrid(grid, cells, goalRow, goalCol);
printGrid(grid);

if (status == SearchStatus.SUCCESS) {
    System.out.println("You win!");
    System.out.println("Final Path Length: " + controller.getPathLength());

System.out.println("Consistency Check: " +
    (controller.getSteps() ==
    controller.getPathLength() + 2 * controller.getBackCount()));
}

if (status == SearchStatus.FAILED) {
    System.out.println("No available move!");
}

if (status != SearchStatus.RUNNING) {
    System.out.println("Backtrack count: " + controller.getBackCount());
    System.out.println("Total Steps: " + controller.getSteps());
    System.out.println("Exploration Count: " + controller.getExpCount());
    System.out.println("Failure Count: " + controller.getFailCount());

    ((Timer) e.getSource()).stop();
}
} );
timer.start();

          
        
    }
}
