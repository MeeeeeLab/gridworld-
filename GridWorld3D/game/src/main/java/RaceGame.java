import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.*;
import com.jme3.light.*;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;

/** First-person view. This client never receives undiscovered terrain or hidden opponents. */
public final class RaceGame extends SimpleApplication {
    private static final float CELL=2.5f, TILE=6;
    private static final ColorRGBA SCOUT=new ColorRGBA(.12f,.9f,.87f,1), TANK=new ColorRGBA(1,.56f,.22f,1);
    private final RaceConnection connection;
    private final Runnable onViewReady;
    private final java.util.Map<Integer, java.util.function.Consumer<Boolean>> keyHandlers=new java.util.HashMap<>();
    private boolean readinessReported;
    private final Geometry[] mapTiles=new Geometry[RaceWorld.SIZE*RaceWorld.SIZE];
    private final Node enemy=new Node("opponent"), effects=new Node("attacks"), mini=new Node("minimap");
    private final char[] drawn=new char[RaceWorld.SIZE*RaceWorld.SIZE];
    private BitmapText status, nav, weapon, result, clock, position, scoring, breakdown;
    private Geometry selfDot, enemyDot;
    private float mapLeft, mapBottom;
    private boolean placed;
    private int lastTick=-1;

    public RaceGame(RaceConnection connection, Runnable onViewReady) {
        this.connection=connection; this.onViewReady=onViewReady;
    }
    @Override public void simpleRender(com.jme3.renderer.RenderManager manager) {
        if(placed && !readinessReported) {
            onViewReady.run();
            readinessReported=true;
        }
    }
    @Override public void handleError(String message, Throwable failure) {
        System.err.println("Player " + (connection.id+1) + " window failed: " + message);
        if(failure!=null)failure.printStackTrace();
        connection.close();
        System.exit(1);
    }
    private ColorRGBA color(int id) { return id==0 ? SCOUT : TANK; }

    @Override public void simpleInitApp() {
        flyCam.setEnabled(false); setDisplayFps(false); setDisplayStatView(false);
        setPauseOnLostFocus(false); inputManager.setCursorVisible(true);
        viewPort.setBackgroundColor(new ColorRGBA(.065f,.09f,.14f,1));
        cam.setFrustumPerspective(78,(float)cam.getWidth()/cam.getHeight(),.08f,24);
        AmbientLight ambient=new AmbientLight(); ambient.setColor(ColorRGBA.White.mult(.7f)); rootNode.addLight(ambient);
        DirectionalLight sun=new DirectionalLight(); sun.setDirection(new Vector3f(-1,-3,-2).normalizeLocal()); rootNode.addLight(sun);
        rootNode.attachChild(enemy); rootNode.attachChild(effects);
        createOpponent(); hud(); controls();
        inputManager.addListener((ActionListener)(name,down,tpf)->{
            if(down)System.out.println("Player "+(connection.id+1)+" requested exit with Esc");
        },INPUT_MAPPING_EXIT);
    }

    private Material material(ColorRGBA color, boolean lit) {
        Material m=new Material(assetManager,lit ? "Common/MatDefs/Light/Lighting.j3md" : "Common/MatDefs/Misc/Unshaded.j3md");
        if(lit) { m.setBoolean("UseMaterialColors",true);m.setColor("Diffuse",color);m.setColor("Ambient",color); }
        else m.setColor("Color",color);
        return m;
    }
    private Geometry box(Node parent,String name,float x,float y,float z,float sx,float sy,float sz,ColorRGBA color,boolean lit) {
        Geometry g=new Geometry(name,new Box(sx,sy,sz));g.setMaterial(material(color,lit));g.setLocalTranslation(x,y,z);parent.attachChild(g);return g;
    }
    private Geometry rect(Node parent,float x,float y,float w,float h,ColorRGBA color) {
        Geometry g=new Geometry("panel",new Quad(w,h));g.setMaterial(material(color,false));g.setLocalTranslation(x,y,0);parent.attachChild(g);return g;
    }
    private BitmapText label(String text,float x,float y,int size,ColorRGBA color) {
        BitmapText t=new BitmapText(guiFont);t.setText(text);t.setSize(size);t.setColor(color);t.setLocalTranslation(x,y,2);guiNode.attachChild(t);return t;
    }
    private void hud() {
        float w=cam.getWidth(),h=cam.getHeight();
        ColorRGBA panel=new ColorRGBA(.025f,.04f,.07f,1), muted=new ColorRGBA(.55f,.65f,.72f,1);
        rect(guiNode,16,h-94,285,78,panel);rect(guiNode,16,h-94,3,78,color(connection.id));
        label(connection.id==0?"01 / SCOUT":"02 / SIEGE",30,h-28,24,color(connection.id));
        label(connection.id==0?"MOBILITY  //  EMP PULSE":"ARMOR  //  RAIL CANNON",30,h-59,12,muted);
        clock=label("",w-188,h-30,15,ColorRGBA.White);
        position=label("",30,h-81,12,muted);
        mapLeft=w-190;mapBottom=h-270;
        rect(guiNode,mapLeft-12,mapBottom-37,176,210,panel);
        label("EXPLORATION",mapLeft,mapBottom+162,14,color(connection.id));
        guiNode.attachChild(mini);
        for(int y=0;y<RaceWorld.SIZE;y++) for(int x=0;x<RaceWorld.SIZE;x++)
            mapTiles[y*RaceWorld.SIZE+x]=rect(mini,mapLeft+x*TILE,mapBottom+(RaceWorld.SIZE-1-y)*TILE,TILE-1,TILE-1,new ColorRGBA(.07f,.09f,.12f,1));
        selfDot=rect(mini,0,0,5,5,color(connection.id));selfDot.setLocalTranslation(0,0,1);
        enemyDot=rect(mini,0,0,5,5,color(1-connection.id));enemyDot.setLocalTranslation(0,0,1);
        label("DARK = UNKNOWN / COLOR = TRAIL",mapLeft-3,mapBottom-10,9,muted);
        label("GREEN = SHARED EXIT",mapLeft,mapBottom-25,10,ColorRGBA.Green);
        rect(guiNode,16,16,w-32,105,panel);rect(guiNode,16,119,w-32,2,color(connection.id));
        status=label("WAITING FOR BOTH PLAYERS",30,104,16,ColorRGBA.White);
        nav=label("",30,78,14,color(connection.id));
        weapon=label("",30,54,13,muted);
        label("P1 W/S drive A/D turn SPACE fire N auto",30,34,11,SCOUT);
        label("P2  ARROWS drive/turn  ENTER cannon  M auto",w-344,34,11,TANK);
        label("+",w/2-6,h/2+8,24,color(connection.id));
        result=label("",w/2-220,h/2+70,22,ColorRGBA.White);
        breakdown=label("",w/2-220,h/2+40,16,ColorRGBA.White);
        rect(guiNode,16,h-160,285,56,panel);
        scoring=label("",30,h-121,18,color(connection.id));
        label("-10 / cell   |   shortest route, higher rating",30,h-146,11,muted);
        // A simple cockpit silhouette gives the first-person view a different identity per role.
        rect(guiNode,w/2-48,122,96,17,color(connection.id).mult(.45f));
        rect(guiNode,w/2-12,139,24,connection.id==0?30:60,color(connection.id).mult(.7f));
    }

    private void controls() {
        bind(0,RaceWorld.Action.FORWARD,KeyInput.KEY_W); bind(0,RaceWorld.Action.BACK,KeyInput.KEY_S);
        bind(0,RaceWorld.Action.TURN_LEFT,KeyInput.KEY_A); bind(0,RaceWorld.Action.TURN_RIGHT,KeyInput.KEY_D);
        bind(0,RaceWorld.Action.FIRE,KeyInput.KEY_SPACE);bind(0,RaceWorld.Action.NAV,KeyInput.KEY_N);
        bind(1,RaceWorld.Action.FORWARD,KeyInput.KEY_UP);bind(1,RaceWorld.Action.BACK,KeyInput.KEY_DOWN);
        bind(1,RaceWorld.Action.TURN_LEFT,KeyInput.KEY_LEFT);bind(1,RaceWorld.Action.TURN_RIGHT,KeyInput.KEY_RIGHT);
        bind(1,RaceWorld.Action.FIRE,KeyInput.KEY_RETURN);bind(1,RaceWorld.Action.NAV,KeyInput.KEY_M);
    }
    private void bind(int id,RaceWorld.Action action,int key) {
        String name=id+":"+action;
        inputManager.addMapping(name,new KeyTrigger(key));
        keyHandlers.put(key,down->connection.send(id,action,down));
        inputManager.addListener((ActionListener)(n,down,tpf)->handleKey(key,down),name);
    }
    public void handleKey(int key,boolean down) {
        var handler=keyHandlers.get(key);
        if(handler!=null)handler.accept(down);
    }
    @Override public void loseFocus() {
        connection.releaseAll(); // Prevent stuck movement when a player switches windows.
        super.loseFocus();
    }

    private void createOpponent() {
        enemy.attachChild(RaceVehicles.create(assetManager,1-connection.id));
    }

    private void terrain(RaceSnapshot s) {
        for(int i=0;i<drawn.length;i++) {
            char tile=s.known().charAt(i);
            if(tile==drawn[i])continue;
            drawn[i]=tile;
            if(tile=='?')continue;
            // Visited floors change color on the minimap without duplicating world geometry.
            boolean newlyDiscovered=mapTiles[i].getUserData("discovered")==null;
            mapTiles[i].setUserData("discovered",true);
            if(!newlyDiscovered) {
                mapTiles[i].setMaterial(material(color(connection.id).mult(.55f),false));
                continue;
            }
            int x=i%RaceWorld.SIZE,y=i/RaceWorld.SIZE;
            ColorRGBA floor=new ColorRGBA(.17f,.21f,.25f,1);
            if((x+y)%2==0)floor=floor.mult(.82f);
            box(rootNode,"floor",x*CELL,-.08f,y*CELL,CELL/2,.08f,CELL/2,floor,true);
            if(tile=='#') {
                ColorRGBA wall=x<12?new ColorRGBA(.19f,.32f,.4f,1):new ColorRGBA(.39f,.29f,.22f,1);
                box(rootNode,"wall",x*CELL,1.8f,y*CELL,1.24f,1.8f,1.24f,wall,true);
                box(rootNode,"wall trim",x*CELL,.3f,y*CELL,1.253f,.025f,1.253f,color(x<12?0:1).mult(.5f),false);
            } else if(tile=='G') {
                box(rootNode,"exit pad",x*CELL,.035f,y*CELL,1.1f,.035f,1.1f,ColorRGBA.Green,false);
                box(rootNode,"exit beacon",x*CELL,2.5f,y*CELL,.18f,2.5f,.18f,new ColorRGBA(.3f,1,.4f,1),false);
            }
            mapTiles[i].setMaterial(material(tile=='#'?new ColorRGBA(.35f,.42f,.49f,1):tile=='G'?ColorRGBA.Green:tile==','?color(connection.id).mult(.55f):new ColorRGBA(.15f,.23f,.29f,1),false));
        }
    }

    @Override public void simpleUpdate(float tpf) {
        if(connection.error!=null) { status.setText(connection.error); return; }
        RaceSnapshot s=connection.latest;
        if(s==null)return;
        if(s.tick()!=lastTick || !placed) { terrain(s); lastTick=s.tick(); }
        Vector3f target=new Vector3f(s.x()*CELL,1.4f,s.y()*CELL);
        float blend=1-(float)Math.exp(-12*tpf);
        if(!placed) {cam.setLocation(target);placed=true;}
        else cam.getLocation().interpolateLocal(target,blend);
        Quaternion rotation=new Quaternion().lookAt(new Vector3f(RaceWorld.DX[s.facing()],0,RaceWorld.DY[s.facing()]),Vector3f.UNIT_Y);
        cam.getRotation().slerp(rotation,blend);cam.setRotation(cam.getRotation());cam.setLocation(cam.getLocation());
        enemy.setCullHint(s.enemyX()<0?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
        if(s.enemyX()>=0) {
            enemy.setLocalTranslation(s.enemyX()*CELL,0,s.enemyY()*CELL);
            enemy.setLocalRotation(new Quaternion().fromAngleAxis(-s.enemyFacing()*FastMath.HALF_PI,Vector3f.UNIT_Y));
        }
        mini.setCullHint(s.jammed()>0?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
        selfDot.setLocalTranslation(mapLeft+s.x()*TILE,mapBottom+(RaceWorld.SIZE-1-s.y())*TILE,1);
        enemyDot.setCullHint(s.enemyX()<0?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
        enemyDot.setLocalTranslation(mapLeft+s.enemyX()*TILE,mapBottom+(RaceWorld.SIZE-1-s.enemyY())*TILE,1);
        clock.setText(String.format("RACE  %02d:%02d",s.tick()/20/60,s.tick()/20%60));
        position.setText("SECTOR "+s.x()+" / "+s.y()+"   HEADING "+"NESW".charAt(s.facing()));
        status.setText(!s.ready()?"WAITING FOR BOTH PLAYERS":s.stunned()>0?"DISABLED  //  "+seconds(s.stunned())+"s":s.jammed()>0?"EMP INTERFERENCE  //  MINIMAP OFFLINE":"REACH THE GREEN BEACON IN THE CENTRAL ARENA");
        nav.setText(s.navigating()?"AUTO PILOT ACTIVE  //  1s burst, hits cancel navigation":s.navUsed()?"AUTO PILOT SPENT  //  manual control": "AUTO PILOT  1 / 1  //  max 1s  //  "+(connection.id==0?"N":"M")+" to activate");
        weapon.setText((connection.id==0?"EMP / range 3 cells":"RAIL / range 8 cells")+"    "+(s.cooldown()==0?"READY":"cooldown "+seconds(s.cooldown())+"s")+"    HITS "+s.hits()+" / SHOTS "+s.shots());
        scoring.setText("STEPS "+s.steps()+"    RATING "+s.score());
        if(s.winner()>=0) {
            result.setText(s.winner()==connection.id?"VICTORY / EXIT REACHED":"DEFEAT / OPPONENT FINISHED");
            breakdown.setText("YOU "+s.steps()+" steps / "+s.score()+" pts\nRIVAL "+s.opponentSteps()+" steps / "+s.opponentScore()+" pts");
        }
        effects.detachAllChildren();
        if(s.flash()>0) attackEffect(connection.id,s.x(),s.y(),s.facing());
        if(s.enemyFlash()>0 && s.enemyX()>=0) attackEffect(1-connection.id,s.enemyX(),s.enemyY(),s.enemyFacing());
    }
    private String seconds(int ticks) {return String.format("%.1f",ticks/20f);}
    private void attackEffect(int id,int x,int y,int facing) {
        if(id==0) {
            for(int d=0;d<4;d++) box(effects,"EMP pulse",x*CELL+RaceWorld.DX[d]*1.4f,.9f,y*CELL+RaceWorld.DY[d]*1.4f,.16f,.6f,.16f,SCOUT,false);
        } else {
            for(int distance=1;distance<=8;distance++) {
                int cx=x+RaceWorld.DX[facing]*distance,cy=y+RaceWorld.DY[facing]*distance;
                if(cx<0||cy<0||cx>=25||cy>=25)break;
                char tile=drawn[cy*25+cx];
                if(tile=='#'||tile=='?')break;
                box(effects,"rail trail",cx*CELL,1.12f,cy*CELL,facing%2==1?1.25f:.045f,.045f,facing%2==0?1.25f:.045f,TANK,false);
            }
        }
    }
    public boolean isViewReady() { return placed; }
    public void verifyView() {
        RaceSnapshot s=connection.latest;
        if(enemy.getTriangleCount()<200)throw new AssertionError("Vehicle model missing");
        if(!placed || s==null || s.known().chars().filter(c->c=='?').count()==0) throw new AssertionError("Missing view/fog");
        if(Math.abs(cam.getLocation().y-1.4f)>.01f || rootNode.getChild("floor")==null) throw new AssertionError("First-person scene missing");
        if(mini.getChildren().size()!=RaceWorld.SIZE*RaceWorld.SIZE+2) throw new AssertionError("Minimap missing");
    }
    @Override public void destroy() {
        System.out.println("Player "+(connection.id+1)+" window closed; first frame rendered="+readinessReported);
        connection.close();super.destroy();
    }
}
