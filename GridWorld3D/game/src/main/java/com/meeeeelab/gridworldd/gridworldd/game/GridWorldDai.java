package com.meeeeelab.gridworldd.gridworldd.game;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.app.state.AppState;
import com.jme3.scene.Node;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.ActionListener;

/**
 * The JMonkeyEngine game entry, you should only do initializations for your game here, game logic is handled by
 * Custom states {@link com.jme3.app.state.BaseAppState}, Custom controls {@link com.jme3.scene.control.AbstractControl}
 * and your custom entities implementations of the previous.
 *
 */
public class GridWorldDai extends SimpleApplication implements ActionListener {

    private Node carNode;
    private char[][] grid;

    private int carRow = 0;
    private int carCol = 0;
    private int facingRow = 0;
    private int facingCol = 1;


    public GridWorldDai() {
    }

    public GridWorldDai(AppState... initialStates) {
        super(initialStates);
    }

    @Override
    public void simpleInitApp() {
        viewPort.setBackgroundColor(new ColorRGBA(0.75f, 0.84f, 0.90f, 1f));

        // 1. 创建立方体形状
        Box b = new Box(0.95f, 1f, 0.95f);

        // 2. 创建蓝色材质
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");

        mat.setBoolean("UseMaterialColors", true);

        ColorRGBA wallColor = new ColorRGBA(0.35f, 0.55f, 0.75f, 1f);

        mat.setColor("Diffuse", wallColor);
        mat.setColor("Ambient", wallColor);
        mat.setColor("Specular", ColorRGBA.Black);
        grid = new char[][]{{ 'A', '.', '.', '.', '.' }, { '.', '#', '#', '.', '#' }, { '.', '.', '.', '#', '.' }, { '.', '.', '.', '.', '.' }, { '.', '.', '.', '.', 'G' } };

        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {

                float x = col * 2 - 4;
                float z = row * 2 - 4;

                if (grid[row][col] == '#') {

                    Geometry wall = new Geometry("Wall", b);
                    wall.setMaterial(mat);
                    wall.setLocalTranslation(x, 0, z);
                    rootNode.attachChild(wall);

                } else if (grid[row][col] == 'A' || grid[row][col] == 'G') {

                    Box markerBox = new Box(0.75f, 0.03f, 0.75f);
                    Geometry marker = new Geometry("Marker", markerBox);

                    Material markerMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");

                    if (grid[row][col] == 'A') {
                        markerMat.setColor("Color", new ColorRGBA(0.95f, 0.6f, 0.25f, 1f));
                    } else {
                        markerMat.setColor("Color", new ColorRGBA(0.25f, 0.75f, 0.45f, 1f));
                    }

                    marker.setMaterial(markerMat);
                    marker.setLocalTranslation(x, -0.96f, z);
                    rootNode.attachChild(marker);
                }
            }
        }
        // 1. 创建小车节点
        carNode = new Node("Car");

        // 2. 创建橙色车身
        Box bodyBox = new Box(0.55f, 0.2f, 0.75f);
        Geometry body = new Geometry("Body", bodyBox);

        Material bodyMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");

        bodyMat.setColor("Color", new ColorRGBA(0.95f, 0.55f, 0.25f, 1f));

        body.setMaterial(bodyMat);
        carNode.attachChild(body);

        // 3. 创建驾驶舱
        Box cabinBox = new Box(0.35f, 0.16f, 0.33f);
        Geometry cabin = new Geometry("Cabin", cabinBox);

        Material cabinMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");

        cabinMat.setColor("Color", ColorRGBA.White);
        cabin.setMaterial(cabinMat);

        // 驾驶舱相对于车身向上移动
        cabin.setLocalTranslation(0, 0.27f, 0);
        carNode.attachChild(cabin);

        // 4. 将整辆车放到起点 A
        carNode.setLocalTranslation(-4, -0.8f, -4);

        // 5. 添加到场景
        rootNode.attachChild(carNode);
        // 创建地面形状
        Box floorBox = new Box(5, 0.1f, 5);
        Geometry floor = new Geometry("Floor", floorBox);

        // 创建浅灰色材质
        Material floorMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");

        floorMat.setColor("Color", ColorRGBA.LightGray);
        floor.setMaterial(floorMat);

        // 将地面放在方块下方
        floor.setLocalTranslation(0, -1.1f, 0);

        // 加入场景
        rootNode.attachChild(floor);
       
        // 环境光：让整个场景有基本亮度
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.5f));
        rootNode.addLight(ambient);

        // 方向光：模拟阳光
        DirectionalLight sun = new DirectionalLight();

        sun.setDirection(new Vector3f(-1, -2, -1).normalizeLocal());

        sun.setColor(ColorRGBA.White.mult(1.0f));
        rootNode.addLight(sun);
        // 关闭默认自由摄像机控制
        flyCam.setEnabled(false);

        // 车本位控制：前进/后退 + 原地左转/右转
        inputManager.addMapping("Forward", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("Backward", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("TurnLeft", new KeyTrigger(KeyInput.KEY_A), new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping("TurnRight", new KeyTrigger(KeyInput.KEY_D), new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addListener(this, "Forward", "Backward", "TurnLeft", "TurnRight");
        updateCamera();
    }
    
    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed) {
            return;
        }

        // 转向只改变车头方向，不改变所在格子
        if (name.equals("TurnLeft")) {
            int oldRow = facingRow;
            facingRow = -facingCol;
            facingCol = oldRow;
            updateCamera();
            return;
        } else if (name.equals("TurnRight")) {
            int oldRow = facingRow;
            facingRow = facingCol;
            facingCol = -oldRow;
            updateCamera();
            return;
        }

        // 根据车头朝向决定前进或倒车的目标格子
        int step;
        if (name.equals("Forward")) {
            step = 1;
        } else if (name.equals("Backward")) {
            step = -1;
        } else {
            return;
        }

        int nextRow = carRow + facingRow * step;
        int nextCol = carCol + facingCol * step;

        if (nextRow < 0 || nextRow >= grid.length
                || nextCol < 0 || nextCol >= grid[nextRow].length
                || grid[nextRow][nextCol] == '#') {
            System.out.println("Blocked at: " + nextRow + ", " + nextCol);
            return;
        }

        // 先更新逻辑状态，后同步渲染状态
        carRow = nextRow;
        carCol = nextCol;
        float x = carCol * 2 - 4;
        float z = carRow * 2 - 4;
        carNode.setLocalTranslation(x, -0.8f, z);
        updateCamera();
        System.out.println("Car: " + carRow + ", " + carCol);
    }

    private void updateCamera() {
        float x = carCol * 2 - 4;
        float z = carRow * 2 - 4;

        // 车头方向：(facingCol, 0, facingRow)
        // 摄像机略靠车前，水平看向前方
        float eyeY = 0.1f;
        cam.setLocation(new Vector3f(
            x + facingCol * 0.25f,
            eyeY,
            z + facingRow * 0.25f
        ));
        cam.lookAt(new Vector3f(
            x + facingCol * 5f,
            eyeY,
            z + facingRow * 5f
        ), Vector3f.UNIT_Y);

        // 让可视车身与车头朝向保持一致（车身局部 +Z 视为前方）
        float yaw = (float) Math.atan2(facingCol, facingRow);
        carNode.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
    }
}
