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
        fogAndSearch(); movement(); combat(); navigation(); networking();
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
        press(w,0,RaceWorld.Action.LEFT);
        check(w.players[0].y==4 && w.players[0].facing==1,"Scout must strafe without turning");
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
        check(w.players[1].jammedUntil>w.tick && w.players[1].x==7,"EMP must jam without knockback");
        int until=w.players[1].stunnedUntil;
        w.players[0].fireAt=0;press(w,0,RaceWorld.Action.FIRE);
        check(w.players[1].stunnedUntil==until,"Recovery immunity must prevent stun lock");
        w=arena();w.map[5][6]='#';press(w,0,RaceWorld.Action.FIRE);
        check(w.players[0].hits==0,"EMP must not cross cover");
        w=arena();w.players[1].x=10;press(w,0,RaceWorld.Action.FIRE);
        check(w.players[0].hits==0,"EMP range too long");
        w=arena();w.players[0].x=2;press(w,1,RaceWorld.Action.FIRE);
        check(w.players[1].hits==1 && w.players[0].x==1,"Rail must hit at range and push away");
        check(w.players[0].jammedUntil==0 && w.players[0].stunnedUntil-w.tick==30,"Rail effects must differ from EMP");
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
            RaceWorld w=new RaceWorld();press(w,id,RaceWorld.Action.NAV);
            check(w.players[id].navUsed && !w.players[id].route.isEmpty(),"Auto failed to activate");
            int x=w.players[id].x,y=w.players[id].y;
            w.players[id].stunnedUntil=w.tick+15;
            for(int i=0;i<14;i++)w.step();
            check(w.players[id].x==x && w.players[id].y==y,"Stun must pause autopilot");
            for(int i=0;i<3000 && w.winner<0;i++)w.step();
            check(w.winner==id,"Auto failed to finish for player "+id);
            int tick=w.tick;w.step();check(w.tick==tick,"Finished match must freeze");
        }
        RaceWorld w=new RaceWorld();press(w,0,RaceWorld.Action.NAV);w.players[0].route.clear();
        press(w,0,RaceWorld.Action.NAV);
        check(w.players[0].route.isEmpty(),"A second navigation use was granted");
        w=arena();w.players[1].x=8;w.players[1].y=5;w.players[1].facing=3;
        w.players[0].x=6;w.players[0].y=5;w.players[0].moveAt=100;
        press(w,0,RaceWorld.Action.NAV); // Autopilot remains valid if displaced by the other weapon.
        press(w,1,RaceWorld.Action.FIRE);
        check(w.players[0].navUsed && w.players[0].x==5 && w.players[1].hits==1,"Knockback during navigation failed");
        int[] next=w.players[0].route.peekFirst();
        check(next!=null && Math.abs(next[1]-w.players[0].x)+Math.abs(next[0]-w.players[0].y)==1,"Knockback left a stale route");
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
            a.releaseAll();b.close();
            await(()->a.error!=null,"Disconnect did not stop the other client");
        }
    }
}
