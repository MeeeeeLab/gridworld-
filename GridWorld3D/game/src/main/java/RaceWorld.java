import java.util.*;

/** Authoritative rules. Coordinates are cells; time is fixed 20 Hz server ticks. */
public final class RaceWorld {
    public static final int SIZE = 25, HZ = 20, GOAL = SIZE / 2;
    public static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
    public enum Action { FORWARD, BACK, TURN_LEFT, TURN_RIGHT, FIRE, NAV }
    public final char[][] map = maze();
    public final Player[] players = {new Player(1, 1, 1), new Player(SIZE - 2, SIZE - 2, 3)};
    public int tick, winner = -1;

    public static final class Player {
        public int x, y, facing, moveAt, turnAt, fireAt, stunnedUntil, immuneUntil, jammedUntil, flashUntil;
        public int shots, hits, navUntil, steps, pendingTurn;
        public boolean navUsed;
        public final boolean[][] seen = new boolean[SIZE][SIZE];
        public final boolean[][] visited = new boolean[SIZE][SIZE];
        public final EnumSet<Action> held = EnumSet.noneOf(Action.class);
        final ArrayDeque<int[]> route = new ArrayDeque<>();
        Player(int x, int y, int facing) { this.x = x; this.y = y; this.facing = facing; }
    }

    public RaceWorld() { for (Player p : players) { p.visited[p.y][p.x]=true; reveal(p); } }

    public static int score(Player player) { return Math.max(0, 1000 - 10 * player.steps); }

    public void input(int id, Action action, boolean down) {
        Player p = players[id];
        boolean fresh = down && p.held.add(action);
        if (!down) p.held.remove(action);
        if (winner >= 0 || !fresh || tick < p.stunnedUntil) return;
        if ((action == Action.TURN_LEFT || action == Action.TURN_RIGHT) && p.route.isEmpty()) {
            p.pendingTurn = action == Action.TURN_LEFT ? -1 : 1;
        }
        if (action == Action.NAV && !p.navUsed) {
            p.pendingTurn = 0;
            p.navUsed = true;
            p.navUntil = tick + HZ;
            plan(p); // Only the server knows the maze; route cells are never sent to clients.
        }
    }

    public void step() {
        if (winner >= 0) return;
        tick++;
        // Alternate priority when both inputs arrive on the same tick.
        for (int n = 0; n < 2 && winner < 0; n++) {
            int id = (tick + n) % 2;
            Player p = players[id];
            // Expire before checking stun: disabling a player never extends the one-second charge.
            if (tick >= p.navUntil) p.route.clear();
            if (tick < p.stunnedUntil) continue;
            if (p.held.contains(Action.FIRE)) fire(id);
            if (winner >= 0) break;
            if (tick >= p.moveAt && !p.route.isEmpty()) {
                int[] next = p.route.removeFirst();
                int dx = next[1] - p.x, dy = next[0] - p.y;
                for (int d = 0; d < 4; d++) if (DX[d] == dx && DY[d] == dy) p.facing = d;
                move(p, dx, dy);
                p.moveAt = tick + (id == 0 ? 4 : 7);
            } else if (p.route.isEmpty()) {
                if (tick >= p.turnAt && p.pendingTurn != 0) {
                    turn(p, p.pendingTurn, id);
                    p.pendingTurn = 0;
                }
                if (tick >= p.moveAt) {
                    int d = p.facing, sign = 1;
                    boolean moving = true;
                    if (p.held.contains(Action.FORWARD)) { }
                    else if (p.held.contains(Action.BACK)) sign = -1;
                    else moving = false;
                    if (moving) {
                        move(p, DX[d] * sign, DY[d] * sign);
                        p.moveAt = tick + (id == 0 ? 4 : 7);
                    }
                }
            }
            if (p.x == GOAL && p.y == GOAL) winner = id;
        }
    }

    private void turn(Player p, int direction, int id) {
        p.facing = (p.facing + direction + 4) % 4;
        p.turnAt = tick + (id == 0 ? 3 : 5);
    }

    private void move(Player p, int dx, int dy) {
        if (Math.abs(dx) + Math.abs(dy) != 1 || !walkable(p.x + dx, p.y + dy)) return;
        p.x += dx; p.y += dy;
        p.steps++;
        p.visited[p.y][p.x]=true;
        reveal(p);
    }

    private void plan(Player p) {
        p.route.clear();
        char[][] copy = Arrays.stream(map).map(char[]::clone).toArray(char[][]::new);
        SearchController search = new SearchController(new GridMap(copy), p.y, p.x, GOAL, GOAL);
        SearchStatus status = SearchStatus.RUNNING;
        for (int i = 0; i < SIZE * SIZE * 3 && status == SearchStatus.RUNNING; i++) status = search.nextStep();
        if (status != SearchStatus.SUCCESS) throw new IllegalStateException("Generated maze is disconnected");
        int[][] path = search.getPath();
        for (int i = 1; i < path.length; i++) p.route.addLast(path[i]);
    }

    private void fire(int id) {
        Player p = players[id], enemy = players[1 - id];
        if (tick < p.fireAt) return;
        p.fireAt = tick + (id == 0 ? 60 : 70);
        p.flashUntil = tick + 5;
        p.shots++;
        int dx = enemy.x - p.x, dy = enemy.y - p.y;
        boolean hit = id == 0
            ? dx * dx + dy * dy <= 9 && lineOfSight(p.x, p.y, enemy.x, enemy.y)
            : cannonHit(p, enemy);
        if (!hit || tick < enemy.immuneUntil) return;
        p.hits++;
        enemy.stunnedUntil = tick + (id == 0 ? 40 : 50);
        enemy.immuneUntil = enemy.stunnedUntil + 20; // Recovery window prevents permanent stun locks.
        enemy.pendingTurn = 0;
        enemy.route.clear(); // A successful hit permanently interrupts this navigation charge.
        enemy.navUntil = tick;
        if (id == 0) enemy.jammedUntil = tick + 100;
        else {
            for (int distance = 0; distance < 2; distance++) {
                if (!walkable(enemy.x + DX[p.facing], enemy.y + DY[p.facing])) break;
                move(enemy, DX[p.facing], DY[p.facing]);
                if (enemy.x == GOAL && enemy.y == GOAL) break;
            }
        }
        // Being pushed onto the goal also counts as finishing.
        if (enemy.x == GOAL && enemy.y == GOAL) winner = 1 - id;
    }

    private boolean cannonHit(Player p, Player enemy) {
        for (int distance = 1; distance <= 8; distance++) {
            int x = p.x + DX[p.facing] * distance, y = p.y + DY[p.facing] * distance;
            if (!walkable(x, y)) break;
            if (x == enemy.x && y == enemy.y) return true;
        }
        return false;
    }

    public boolean walkable(int x, int y) {
        return x >= 0 && y >= 0 && x < SIZE && y < SIZE && map[y][x] != '#';
    }

    // Conservative grid ray: a wall at either edge of a diagonal corner blocks vision/EMP.
    public boolean lineOfSight(int x0, int y0, int x1, int y1) {
        int dx = x1 - x0, dy = y1 - y0, steps = Math.max(Math.abs(dx), Math.abs(dy)) * 4;
        int lastX = x0, lastY = y0;
        for (int i = 1; i <= steps; i++) {
            int x = (int)Math.floor(x0 + .5 + dx * (double)i / steps);
            int y = (int)Math.floor(y0 + .5 + dy * (double)i / steps);
            if (!walkable(x, y)) return false;
            if (x != lastX && y != lastY && (!walkable(x, lastY) || !walkable(lastX, y))) return false;
            lastX = x; lastY = y;
        }
        return true;
    }

    private void reveal(Player p) {
        for (int y = Math.max(0, p.y - 3); y <= Math.min(SIZE - 1, p.y + 3); y++)
            for (int x = Math.max(0, p.x - 3); x <= Math.min(SIZE - 1, p.x + 3); x++) {
                // Reveal the wall face, but never tiles behind it.
                if ((x-p.x)*(x-p.x)+(y-p.y)*(y-p.y) <= 10 && visibleTile(p, x, y)) p.seen[y][x] = true;
            }
    }

    private boolean visibleTile(Player p, int x, int y) {
        if (walkable(x, y)) return lineOfSight(p.x, p.y, x, y);
        // Stop just before the wall center so the surface itself can be discovered.
        double dx=x-p.x, dy=y-p.y;
        int steps=(int)(Math.max(Math.abs(dx),Math.abs(dy))*8);
        for(int i=1;i<steps;i++) {
            double t=(double)i/steps;
            int cx=(int)Math.floor(p.x+.5+dx*t), cy=(int)Math.floor(p.y+.5+dy*t);
            if(cx==x && cy==y) return true;
            if(!walkable(cx,cy)) return false;
        }
        return true;
    }

    public RaceSnapshot snapshot(int id, boolean ready) {
        Player p = players[id], other = players[1-id];
        StringBuilder known = new StringBuilder(SIZE * SIZE);
        for (int y=0; y<SIZE; y++) for (int x=0; x<SIZE; x++)
            known.append(!p.seen[y][x] ? '?' : map[y][x]=='.' && p.visited[y][x] ? ',' : map[y][x]);
        boolean visible = (p.x-other.x)*(p.x-other.x)+(p.y-other.y)*(p.y-other.y) <= 10
            && lineOfSight(p.x,p.y,other.x,other.y);
        return new RaceSnapshot(tick, winner, ready, p.x, p.y, p.facing, p.navUsed, !p.route.isEmpty(),
            Math.max(0,p.stunnedUntil-tick), Math.max(0,p.fireAt-tick), Math.max(0,p.jammedUntil-tick),
            Math.max(0,p.flashUntil-tick), visible ? other.x : -1, visible ? other.y : -1,
            visible ? other.facing : -1, visible ? Math.max(0,other.flashUntil-tick) : 0,
            p.shots, p.hits, p.steps, score(p), winner>=0?other.steps:-1, winner>=0?score(other):-1, known.toString());
    }

    private static char[][] maze() {
        char[][] cells = new char[SIZE][SIZE];
        for (char[] row : cells) Arrays.fill(row, '#');
        Random random = new Random(20261113);
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        cells[1][1]='.'; stack.push(new int[]{1,1});
        while (!stack.isEmpty()) {
            int[] current=stack.peek();
            List<Integer> dirs=new ArrayList<>(List.of(0,1,2,3));
            Collections.shuffle(dirs,random);
            boolean carved=false;
            for(int d:dirs) {
                int x=current[0]+DX[d]*2,y=current[1]+DY[d]*2;
                if(x<=0||y<=0||x>=SIZE-1||y>=SIZE-1||cells[y][x]!='#') continue;
                cells[current[1]+DY[d]][current[0]+DX[d]]='.';
                cells[y][x]='.'; stack.push(new int[]{x,y}); carved=true; break;
            }
            if(!carved) stack.pop();
        }
        // Loops and a shared arena let players meet, dodge and attack instead of following one corridor.
        for(int i=0;i<65;i++) {
            int x=1+random.nextInt(SIZE-2), y=1+random.nextInt(SIZE-2);
            if((x%2==0) != (y%2==0)) cells[y][x]='.';
        }
        for(int y=9;y<=15;y++) for(int x=9;x<=15;x++) cells[y][x]='.';
        cells[11][11]='#'; cells[13][13]='#'; // Cover inside the central arena.
        cells[GOAL][GOAL]='G';
        return cells;
    }
}
