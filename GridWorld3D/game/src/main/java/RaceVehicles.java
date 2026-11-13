import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;

/** Original low-poly vehicle models, in cell-scale units, facing local -Z. */
public final class RaceVehicles {
    private final AssetManager assets;
    private final Node car;
    private final Material paint, rubber, metal, glass, lamp, red;
    private RaceVehicles(AssetManager assets,int role) {
        this.assets=assets;car=new Node(role==0?"Scout buggy":"Siege crawler");
        paint=mat(role==0?new ColorRGBA(.03f,.63f,.62f,1):new ColorRGBA(.85f,.36f,.07f,1),false);
        rubber=mat(new ColorRGBA(.035f,.045f,.055f,1),false);
        metal=mat(new ColorRGBA(.21f,.28f,.33f,1),false);
        glass=mat(new ColorRGBA(.025f,.08f,.12f,1),false);
        lamp=mat(new ColorRGBA(.65f,.95f,1,1),true);red=mat(new ColorRGBA(1,.09f,.04f,1),true);
    }
    public static Node create(AssetManager assets,int role) {
        RaceVehicles v=new RaceVehicles(assets,role);
        if(role==0)v.scout();else v.tank();
        return v.car;
    }
    private Material mat(ColorRGBA color,boolean glow) {
        Material m=new Material(assets,glow?"Common/MatDefs/Misc/Unshaded.j3md":"Common/MatDefs/Light/Lighting.j3md");
        if(glow)m.setColor("Color",color);
        else {m.setBoolean("UseMaterialColors",true);m.setColor("Diffuse",color);m.setColor("Ambient",color);m.setColor("Specular",ColorRGBA.White.mult(.4f));m.setFloat("Shininess",48);}
        return m;
    }
    private Geometry part(String name,Mesh mesh,float x,float y,float z,Material m) {
        Geometry g=new Geometry(name,mesh);g.setMaterial(m);g.setLocalTranslation(x,y,z);car.attachChild(g);return g;
    }
    private Geometry box(String name,float x,float y,float z,float a,float b,float c,Material m) {
        return part(name,new Box(a,b,c),x,y,z,m);
    }
    private void wheel(float x,float y,float z,float radius,float width) {
        part("tire",new Cylinder(2,16,radius,width,true),x,y,z,rubber).rotate(0,FastMath.HALF_PI,0);
        part("hub",new Cylinder(2,8,radius*.5f,width+.012f,true),x,y,z,metal).rotate(0,FastMath.HALF_PI,0);
        part("hub cap",new Cylinder(2,8,radius*.19f,width+.025f,true),x,y,z,paint).rotate(0,FastMath.HALF_PI,0);
    }
    private void scout() {
        box("chassis",0,.39f,0,.48f,.12f,.83f,metal);
        box("body",0,.55f,0,.48f,.13f,.77f,paint);
        box("sloping hood",0,.64f,-.59f,.46f,.065f,.32f,paint).rotate(-.14f,0,0);
        box("cabin",0,.84f,.05f,.38f,.25f,.35f,glass);
        box("roof",0,1.12f,.08f,.42f,.05f,.4f,paint);
        box("windshield",0,.89f,-.31f,.33f,.17f,.015f,glass).rotate(-.18f,0,0);
        for(int side:new int[]{-1,1}) {
            wheel(side*.58f,.31f,-.57f,.31f,.24f);wheel(side*.58f,.31f,.6f,.31f,.24f);
            for(float z:new float[]{-.57f,.6f})box("fender",side*.55f,.64f,z,.19f,.035f,.35f,metal);
            box("roll cage",side*.37f,.86f,-.28f,.025f,.23f,.025f,metal).rotate(-.18f,0,0);
            box("headlamp",side*.31f,.6f,-.91f,.12f,.055f,.025f,lamp);
            box("tail lamp",side*.34f,.59f,.79f,.09f,.04f,.02f,red);
            box("side stripe",side*.485f,.54f,0,.012f,.025f,.47f,lamp);
            box("spoiler mount",side*.3f,.83f,.71f,.025f,.17f,.03f,metal);
        }
        box("spoiler",0,1.0f,.72f,.52f,.035f,.14f,paint);
        box("front bumper",0,.37f,-.94f,.5f,.055f,.07f,rubber);
        part("EMP coil",new Torus(16,8,.035f,.17f),0,1.23f,.1f,lamp).rotate(FastMath.HALF_PI,0,0);
        box("EMP core",0,1.2f,.1f,.07f,.09f,.07f,metal);
        box("antenna",.28f,1.37f,.3f,.012f,.23f,.012f,metal);
    }
    private void tank() {
        box("lower hull",0,.45f,0,.56f,.19f,.85f,metal);
        box("armored hull",0,.74f,0,.62f,.15f,.87f,paint);
        box("front armor",0,.66f,-.87f,.56f,.16f,.06f,paint).rotate(-.28f,0,0);
        for(int side:new int[]{-1,1}) {
            box("track belt bottom",side*.73f,.11f,0,.2f,.07f,.96f,rubber);
            box("track belt top",side*.73f,.57f,0,.2f,.06f,.96f,rubber);
            for(int i=0;i<4;i++)wheel(side*.73f,.32f,-.69f+i*.46f,.23f,.37f);
            for(int i=0;i<12;i++) {
                float z=-.88f+i*.16f;
                box("track tread",side*.73f,.64f,z,.205f,.018f,.035f,metal);
                box("lower tread",side*.73f,.035f,z,.205f,.018f,.035f,metal);
            }
            box("track guard",side*.73f,.71f,0,.23f,.045f,.98f,paint);
            box("headlamp",side*.47f,.83f,-.9f,.085f,.045f,.025f,lamp);
            box("rear light",side*.47f,.73f,.89f,.07f,.035f,.02f,red);
            box("turret armor",side*.46f,1.13f,.05f,.045f,.17f,.4f,paint);
        }
        part("turret bearing",new Cylinder(2,20,.39f,.12f,true),0,.96f,.08f,metal).rotate(FastMath.HALF_PI,0,0);
        box("turret",0,1.14f,.08f,.43f,.17f,.43f,paint);
        box("hatch",0,1.33f,.16f,.22f,.025f,.22f,metal);
        part("gun barrel",new Cylinder(2,12,.09f,.94f,true),0,1.16f,-.76f,metal);
        box("muzzle brake",0,1.16f,-1.23f,.13f,.12f,.12f,paint);
        box("muzzle opening",0,1.16f,-1.355f,.075f,.065f,.005f,rubber);
        box("sight",.24f,1.36f,-.13f,.09f,.055f,.1f,glass);
        box("antenna",-.31f,1.53f,.36f,.012f,.26f,.012f,metal);
        for(int i=0;i<5;i++)box("engine vent",-.24f+i*.12f,.9f,.66f,.025f,.013f,.14f,rubber);
    }
}
