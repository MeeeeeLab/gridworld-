import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.export.binary.*;
import com.jme3.font.BitmapText;
import com.jme3.light.*;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;
import java.nio.file.*;

/** A model turntable; optional output directory exports native jME models and a preview. */
public final class VehicleGarage extends SimpleApplication {
    private final Node display=new Node("turntable");
    private final Path output;
    private ScreenshotAppState screenshot;
    private int frames;
    VehicleGarage(Path output) {this.output=output;}
    public static void main(String[] args) {
        VehicleGarage app=new VehicleGarage(args.length==0?null:Path.of(args[0]));
        AppSettings settings=new AppSettings(true);
        settings.setTitle("GRID//RIFT | Vehicle workshop");settings.setResolution(1100,720);
        settings.setAudioRenderer(null);settings.setSamples(4);settings.setFrameRate(60);
        app.setSettings(settings);app.setShowSettings(false);app.start();
    }
    @Override public void simpleInitApp() {
        flyCam.setMoveSpeed(5);flyCam.setDragToRotate(true);
        setDisplayFps(false);setDisplayStatView(false);setPauseOnLostFocus(false);
        viewPort.setBackgroundColor(new ColorRGBA(.025f,.04f,.065f,1));
        AmbientLight fill=new AmbientLight();fill.setColor(ColorRGBA.White.mult(.55f));rootNode.addLight(fill);
        DirectionalLight key=new DirectionalLight();key.setDirection(new Vector3f(-1,-2,1).normalizeLocal());key.setColor(ColorRGBA.White.mult(1.1f));rootNode.addLight(key);
        DirectionalLight rim=new DirectionalLight();rim.setDirection(new Vector3f(1,-1,-1).normalizeLocal());rim.setColor(new ColorRGBA(.3f,.5f,.7f,1));rootNode.addLight(rim);
        rootNode.attachChild(display);
        for(int id=0;id<2;id++) {
            Node model=RaceVehicles.create(assetManager,id);
            if(model.getTriangleCount()<200)throw new IllegalStateException("Vehicle geometry missing");
            if(output!=null) export(model,id);
            model.setLocalTranslation(id==0?1.65f:-1.65f,0,0);display.attachChild(model);
            Geometry plinth=new Geometry("plinth",new Box(1.3f,.08f,1.55f));
            Material mat=new Material(assetManager,"Common/MatDefs/Light/Lighting.j3md");
            mat.setBoolean("UseMaterialColors",true);mat.setColor("Diffuse",new ColorRGBA(.09f,.13f,.18f,1));mat.setColor("Ambient",ColorRGBA.White.mult(.15f));
            plinth.setMaterial(mat);plinth.setLocalTranslation(model.getLocalTranslation().x,-.08f,0);display.attachChild(plinth);
        }
        cam.setLocation(new Vector3f(4.6f,4.1f,-7.8f));cam.lookAt(new Vector3f(0,.45f,0),Vector3f.UNIT_Y);
        text("GRID // RIFT",35,675,30,ColorRGBA.White);
        text("VEHICLE WORKSHOP   /   LOW-POLY ORIGINALS",37,639,14,new ColorRGBA(.5f,.66f,.77f,1));
        text("01 / SCOUT BUGGY",65,105,23,new ColorRGBA(.1f,.9f,.85f,1));
        text("4 wheels / roll cage / roof EMP coil",65,73,14,ColorRGBA.LightGray);
        text("02 / SIEGE CRAWLER",650,105,23,new ColorRGBA(1,.55f,.18f,1));
        text("Armored tracks / turret / rail cannon",650,73,14,ColorRGBA.LightGray);
        text("Drag mouse to look | WASD to inspect | Esc to exit",35,30,13,ColorRGBA.LightGray);
        if(output!=null) {
            screenshot=new ScreenshotAppState(output.toAbsolutePath()+java.io.File.separator,"garage");screenshot.setIsNumbered(false);stateManager.attach(screenshot);
        }
    }
    private void export(Node model,int id) {
        try {
            Files.createDirectories(output);
            Path file=output.resolve(id==0?"scout-buggy.j3o":"siege-crawler.j3o");
            BinaryExporter.getInstance().save(model,file.toFile());
            BinaryImporter importer=BinaryImporter.getInstance();importer.setAssetManager(assetManager);
            Node restored=(Node)importer.load(file.toFile());
            if(restored.getTriangleCount()!=model.getTriangleCount())throw new IllegalStateException("Model export lost geometry");
            System.out.println("PASS model export/reload: "+file.getFileName()+" / "+model.getTriangleCount()+" triangles");
        } catch(java.io.IOException e) {throw new RuntimeException(e);}
    }
    private void text(String value,int x,int y,int size,ColorRGBA color) {
        BitmapText label=new BitmapText(guiFont);label.setText(value);label.setSize(size);label.setColor(color);label.setLocalTranslation(x,y,0);guiNode.attachChild(label);
    }
    @Override public void simpleUpdate(float tpf) {
        if(output==null)return;
        frames++;
        if(frames==60)screenshot.takeScreenshot();
        if(frames==90)stop();
    }
}
