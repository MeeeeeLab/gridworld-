import java.io.*;
import java.util.function.BooleanSupplier;

/** Run with :desktop:raceCheck. No test framework or display required. */
public final class RaceChecks {
    private static int checks;
    private static void check(boolean condition,String message) {
        checks++; if(!condition)throw new AssertionError(message);
    }
    private static RaceWorld arena() {
        RaceWorld w=new RaceWorld();
        for(int y=1;y<24;y++)for(int x=1;x<24;x++)w.map[y][x]='.';
        w.map[12][12]='G';
        w.players[0].x=5;w.players[0].y=5;w.players[0].facing=1;
        w.players[1].x=7;w.players[1].y=5;w.players[1].facing=3;
        return w;
    }
    private static void press(RaceWorld w,int id,RaceWorld.Action action) {w.input(id,action,true);w.step();w.input(id,action,false);}
    private static void await(BooleanSupplier predicate,String message) throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while(!predicate.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(15);
        check(predicate.getAsBoolean(),message);
    }
    public static void main(String[] args) throws Exception {
        if(args.length==2 && args[0].equals("--startup-probe")) {
            if(!args[1].equals("-"))java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]),"ready");
            Thread.sleep(30000);return;
        }
        fogAndSearch(); movement(); combat(); navigation(); networking(); startup(); scoringAndTurns();
        System.out.println("PASS: "+checks+" checks (original search, asymmetric rules, fog, victory, loopback TCP)");
    }
    private static void fogAndSearch() throws Exception {
        RaceWorld w=new RaceWorld();
        RaceSnapshot a=w.snapshot(0,true),b=w.snapshot(1,true);
        check(a.known().length()==625 && a.known().chars().filter(c->c=='?').count()>580,"Initial fog exposes maze");
        check(a.enemyX()==-1 && b.enemyX()==-1,"Remote spawn leaks through fog");
        check(a.known().charAt(23*25+23)=='?' && b.known().charAt(26)=='?',"Players share exploration");
        for(int id=0;id<2;id++) {
            RaceWorld.Player p=w.players[id];
            char[][] copy=java.util.Arrays.stream(w.map).map(char[]::clone).toArray(char[][]::new);
            SearchController c=new SearchController(new GridMap(copy),p.y,p.x,12,12);
            SearchStatus status=SearchStatus.RUNNING;
            for(int i=0;i<2000 && status==SearchStatus.RUNNING;i++)status=c.nextStep();
            check(status==SearchStatus.SUCCESS,"Original search cannot reach goal from player "+id);
            int[][] route=c.getPath();
            check(route[0][0]==p.y && route[0][1]==p.x,"Search ignores arbitrary start");
            check(route[route.length-1][0]==12 && route[route.length-1][1]==12,"Route has wrong goal");
            for(int i=1;i<route.length;i++)check(w.walkable(route[i][1],route[i][0]) && Math.abs(route[i][0]-route[i-1][0])+Math.abs(route[i][1]-route[i-1][1])==1,"Illegal path segment");
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();a.write(new DataOutputStream(bytes));
        check(a.equals(RaceSnapshot.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))),"Snapshot roundtrip");
        RaceWorld blocked=arena();blocked.map[5][6]='#';
        check(!blocked.lineOfSight(5,5,7,5),"Vision crosses wall");
        check(blocked.snapshot(0,true).enemyX()==-1,"Enemy leaks through wall");
        blocked.map[5][6]='.';blocked.map[5][7]='#';
        check(!blocked.lineOfSight(6,5,7,6),"Vision cuts diagonal corner");
    }
    private static void movement() {
        RaceWorld w=arena();
        press(w,0,RaceWorld.Action.TURN_LEFT);
        check(w.players[0].y==5 && w.players[0].x==5 && w.players[0].facing==0,"Scout left turn must rotate without moving");
        int x=w.players[1].x,y=w.players[1].y;
        press(w,1,RaceWorld.Action.TURN_LEFT);
        check(w.players[1].x==x && w.players[1].y==y && w.players[1].facing==2,"Tank must turn in place");
        w=arena();w.input(0,RaceWorld.Action.FORWARD,true);w.input(1,RaceWorld.Action.FORWARD,true);
        for(int i=0;i<14;i++)w.step();
        check(w.players[0].x-5>7-w.players[1].x,"Scout should move faster");
        w=arena();w.map[5][6]='#';press(w,0,RaceWorld.Action.FORWARD);
        check(w.players[0].x==5,"Movement crosses wall");
    }
    private static void combat() {
        RaceWorld w=arena();w.players[0].facing=0;
        press(w,0,RaceWorld.Action.FIRE);
        check(w.players[0].hits==1 && w.players[1].stunnedUntil>w.tick,"EMP must hit sideways within radius");
        check(w.players[1].jammedUntil-w.tick==100 && w.players[1].stunnedUntil-w.tick==40 && w.players[1].x==7,"EMP must jam without knockback");
        int until=w.players[1].stunnedUntil;
        w.players[0].fireAt=0;press(w,0,RaceWorld.Action.FIRE);
        check(w.players[1].stunnedUntil==until,"Recovery immunity must prevent stun lock");
        w=arena();w.map[5][6]='#';press(w,0,RaceWorld.Action.FIRE);
        check(w.players[0].hits==0,"EMP must not cross cover");
        w=arena();w.players[1].x=10;press(w,0,RaceWorld.Action.FIRE);
        check(w.players[0].hits==0,"EMP range too long");
        w=arena();w.players[0].x=2;press(w,1,RaceWorld.Action.FIRE);
        check(w.players[1].hits==1 && w.players[0].x==1,"Rail must hit at range and push away");
        check(w.players[0].jammedUntil==0 && w.players[0].stunnedUntil-w.tick==50,"Rail effects must differ from EMP");
        int shots=w.players[1].shots;press(w,1,RaceWorld.Action.FIRE);
        check(w.players[1].shots==shots,"Weapon cooldown ignored");
        w=arena();w.players[0].y=6;press(w,1,RaceWorld.Action.FIRE);
        check(w.players[1].hits==0,"Rail must require alignment");
        w=arena();w.map[5][6]='#';press(w,1,RaceWorld.Action.FIRE);
        check(w.players[1].hits==0,"Rail must stop at wall");
        w=arena();w.players[1].x=11;w.players[1].y=12;w.players[1].facing=1;
        w.players[0].x=10;w.players[0].y=12;w.players[0].facing=1;
        press(w,1,RaceWorld.Action.FIRE);
        check(w.players[1].hits==0,"Cannon cannot fire backwards");
    }
    private static void navigation() {
        for(int id=0;id<2;id++) {
            RaceWorld w=new RaceWorld();
            w.input(id,RaceWorld.Action.NAV,true); // Keep held through expiry.
            check(w.players[id].navUsed && !w.players[id].route.isEmpty(),"Auto failed to activate");
            for(int i=0;i<19;i++)w.step();
            check(w.snapshot(id,true).navigating(),"Navigation ended before its one-second window");
            w.step();
            check(w.tick==20 && !w.snapshot(id,true).navigating(),"Navigation must expire at exactly one second");
            int x=w.players[id].x,y=w.players[id].y;
            for(int i=0;i<80;i++)w.step();
            check(w.players[id].x==x && w.players[id].y==y && w.winner<0,"Navigation continued after expiry");
            w.input(id,RaceWorld.Action.NAV,false);press(w,id,RaceWorld.Action.NAV);
            check(!w.snapshot(id,true).navigating(),"Second navigation use was granted");
            int facing=w.players[id].facing;
            press(w,id,RaceWorld.Action.TURN_LEFT);
            check(w.players[id].facing==(facing+3)%4,"Manual control did not return after expiry");
            w=new RaceWorld();press(w,id,RaceWorld.Action.NAV);
            x=w.players[id].x;y=w.players[id].y;
            w.players[id].stunnedUntil=50;
            while(w.tick<60)w.step();
            check(w.players[id].x==x && w.players[id].y==y && !w.snapshot(id,true).navigating(),"Stun extended the navigation deadline");
        }
        for(int attacker=0;attacker<2;attacker++) {
            RaceWorld w=arena();int victim=1-attacker;
            w.players[victim].moveAt=100;
            press(w,victim,RaceWorld.Action.NAV);
            press(w,attacker,RaceWorld.Action.FIRE);
            check(w.players[attacker].hits==1 && w.players[victim].navUsed && !w.snapshot(victim,true).navigating(),"Attack must cancel navigation without refund");
            while(w.tick<110)w.step();
            press(w,victim,RaceWorld.Action.NAV);
            check(w.players[victim].route.isEmpty(),"Interrupted navigation resumed or was refunded");
        }
        RaceWorld w=arena();press(w,1,RaceWorld.Action.FIRE);
        check(w.players[0].x==3,"Cannon must push two free cells");
        w=arena();w.map[5][3]='#';press(w,1,RaceWorld.Action.FIRE);
        check(w.players[0].x==4,"Two-cell push crossed a wall");
        w=arena();w.players[1].x=10;press(w,0,RaceWorld.Action.FIRE);
        check(w.players[0].hits==0,"EMP reached beyond radius three");
        w=arena();w.players[1].x=8;press(w,0,RaceWorld.Action.FIRE);
        check(w.players[0].hits==1,"EMP must reach radius three");
        w=arena();w.players[1].x=10;w.players[1].y=12;w.players[1].facing=1;
        w.players[0].x=11;w.players[0].y=12;
        press(w,1,RaceWorld.Action.FIRE);
        check(w.winner==0 && w.players[0].x==12,"Knockback must stop and award victory on the goal");
        int tick=w.tick;w.step();check(w.tick==tick,"Finished match must freeze");
    }
    private static void scoringAndTurns() {
        for(int id=0;id<2;id++) {
            RaceWorld w=arena();RaceWorld.Player p=w.players[id];
            int facing=p.facing;
            w.input(id,RaceWorld.Action.TURN_LEFT,true);
            w.input(id,RaceWorld.Action.TURN_LEFT,false); // A quick tap between server ticks must still turn.
            w.step();
            check(p.facing==(facing+3)%4 && p.steps==0,"Quick turn lost or charged a movement step");
            while(w.tick<p.turnAt)w.step();
            w.input(id,RaceWorld.Action.TURN_RIGHT,true);
            for(int i=0;i<40;i++)w.step();
            check(p.facing==facing,"Holding turn rotated more than once");
            check(RaceWorld.score(p)==1000,"Turning changed efficiency score");
            w.input(id,RaceWorld.Action.TURN_RIGHT,false);
            press(w,id,RaceWorld.Action.FORWARD);
            check(p.steps==1 && RaceWorld.score(p)==990,"Forward movement did not deduct ten points");
            int x=p.x,y=p.y;
            check(w.snapshot(id,true).known().charAt(y*25+x)==',',"Walked tile missing from trail");
            while(w.tick<p.moveAt)w.step();
            press(w,id,RaceWorld.Action.BACK);
            check(p.steps==2 && RaceWorld.score(p)==980,"Backtracking must cost another step");
            w.map[p.y+RaceWorld.DY[p.facing]][p.x+RaceWorld.DX[p.facing]]='#';
            while(w.tick<p.moveAt)w.step();
            press(w,id,RaceWorld.Action.FORWARD);
            check(p.steps==2,"Blocked movement charged a step");
        }
        RaceWorld w=arena();press(w,1,RaceWorld.Action.FIRE);
        check(w.players[0].steps==2 && RaceWorld.score(w.players[0])==980,"Knockback steps not scored");
        check(w.players[1].steps==0 && RaceWorld.score(w.players[1])==1000,"Shooting should not charge movement steps");
        w=new RaceWorld();press(w,0,RaceWorld.Action.NAV);while(w.tick<25)w.step();
        check(w.players[0].steps>0 && RaceWorld.score(w.players[0])==1000-10*w.players[0].steps,"Navigation movement bypassed scoring");
        check(w.snapshot(0,true).opponentSteps()==-1 && w.snapshot(0,true).opponentScore()==-1,"Live opponent progress leaked");
        w=arena();w.players[0].x=11;w.players[0].y=12;w.players[0].facing=1;
        press(w,0,RaceWorld.Action.FORWARD);
        RaceSnapshot result=w.snapshot(0,true);
        check(w.winner==0 && result.steps()==1 && result.score()==990 && result.opponentSteps()==0 && result.opponentScore()==1000,"Finish scoreboard incorrect");
        press(w,0,RaceWorld.Action.BACK);
        check(w.players[0].steps==1,"Final score changed after match end");
        w.players[0].steps=200;
        check(RaceWorld.score(w.players[0])==0,"Score fell below zero");
    }
    private static void startup() throws Exception {
        var folder=java.nio.file.Files.createTempDirectory("race-startup-check-");
        var ready=folder.resolve("ready");var log=folder.resolve("player.log");
        String javaExecutable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
        try {
            Process exited=new ProcessBuilder(javaExecutable,"-version").redirectErrorStream(true).redirectOutput(log.toFile()).start();
            exited.waitFor();
            boolean rejected=false;
            try {RaceLauncher.awaitWindow(exited,ready,log,5);}catch(IOException expected){rejected=true;}
            check(rejected,"Launcher accepted an exited process without a window");
            for(boolean announces:new boolean[]{true,false}) {
                Process child=new ProcessBuilder(javaExecutable,"-cp",System.getProperty("java.class.path"),"RaceChecks","--startup-probe",announces?ready.toString():"-")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    if(announces) {
                        RaceLauncher.awaitWindow(child,ready,log,5);
                        check(child.isAlive(),"Launcher did not wait for a live ready process");
                    } else {
                        rejected=false;
                        try {RaceLauncher.awaitWindow(child,ready,log,0);}catch(IOException expected){rejected=true;}
                        check(rejected,"Launcher did not time out an unready process");
                    }
                } finally {child.destroy();child.waitFor();java.nio.file.Files.deleteIfExists(ready);}
            }
        } finally {java.nio.file.Files.deleteIfExists(log);java.nio.file.Files.deleteIfExists(folder);}
    }
    private static void networking() throws Exception {
        try(RaceServer server=new RaceServer(0);RaceConnection a=new RaceConnection(server.port());RaceConnection b=new RaceConnection(server.port())) {
            await(()->a.latest!=null && b.latest!=null && a.latest.ready() && b.latest.ready(),"TCP clients never became ready");
            check(a.id==0 && b.id==1,"Server assigned duplicate roles");
            check(a.latest.enemyX()==-1 && b.latest.enemyX()==-1,"Network leaked opponent spawn");
            a.send(0,RaceWorld.Action.NAV,true);a.send(0,RaceWorld.Action.NAV,false);
            a.send(1,RaceWorld.Action.NAV,true);a.send(1,RaceWorld.Action.NAV,false);
            await(()->a.latest.navUsed() && b.latest.navUsed(),"Shared keyboard did not control both clients");
            await(()->(a.latest.x()!=1 || a.latest.y()!=1) && (b.latest.x()!=23 || b.latest.y()!=23),"Network snapshots did not update positions");
            await(()->!a.latest.navigating() && !b.latest.navigating(),"Navigation expiry did not reach both clients");
            check(a.latest.navUsed() && b.latest.navUsed(),"Network restored navigation charges");
            a.releaseAll();b.close();
            await(()->a.error!=null,"Disconnect did not stop the other client");
        }
    }
}
