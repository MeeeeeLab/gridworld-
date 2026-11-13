package com.meeeeelab.gridworldd.gridworldd.game;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
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
public class GridWorldD extends SimpleApplication implements ActionListener {

    private Node carNode;
    private char[][] grid;

    private int carRow = 0;
    private int carCol = 0;
    private int facingRow = 0;
    private int facingCol = 1;

    // 逻辑格子会立即更新，但 3D 小车逐帧接近这个目标位置。
    private Vector3f moveTarget;
    private boolean moving = false;
    private boolean turning = false;

    // 把离散的 90 度转向变成连续动画（弧度）。
    private float currentYaw = FastMath.HALF_PI; // 初始朝 +X
    private float targetYaw = FastMath.HALF_PI;

    // 右上角 2D 小地图（以屏幕坐标绘制，不依赖主摄像机视角）。
    private Node minimapCarNode;
    private float minimapLeft;
    private float minimapBottom;
    private final float minimapCellSize = 24f;
    private final float minimapPadding = 8f;


    public GridWorldD() {
    }

    public GridWorldD(AppState... initialStates) {
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
        moveTarget = carNode.getLocalTranslation().clone();

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
        createMinimap();
    }
    
    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        // 一个动作动画结束后才接收下一次动作，避免跳格或提前转弯。
        if (!isPressed || moving || turning) {
            return;
        }

        // 转向只改变车头方向，不改变所在格子
        if (name.equals("TurnLeft")) {
            int oldRow = facingRow;
            facingRow = -facingCol;
            facingCol = oldRow;
            targetYaw += FastMath.HALF_PI;
            turning = true;
            return;
        } else if (name.equals("TurnRight")) {
            int oldRow = facingRow;
            facingRow = facingCol;
            facingCol = -oldRow;
            targetYaw -= FastMath.HALF_PI;
            turning = true;
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
        moveTarget.set(x, -0.8f, z);
        moving = true;
    }

    @Override
    public void simpleUpdate(float tpf) {
        // tpf = 距离上一帧经过的秒数，使速度不依赖 FPS。
        if (moving) {
            Vector3f remaining = moveTarget.subtract(carNode.getLocalTranslation());
            float maxStep = 4f * tpf; // 每秒 4 个世界单位（每格 2 个单位）
            if (remaining.length() <= maxStep) {
                carNode.setLocalTranslation(moveTarget);
                moving = false;
                System.out.println("Arrived: " + carRow + ", " + carCol);
            } else {
                carNode.move(remaining.normalizeLocal().multLocal(maxStep));
            }
        }

        if (turning) {
            float remainingAngle = targetYaw - currentYaw;
            float maxTurn = FastMath.PI * tpf; // 每秒转 180 度
            if (FastMath.abs(remainingAngle) <= maxTurn) {
                currentYaw = targetYaw;
                turning = false;
            } else {
                currentYaw += Math.signum(remainingAngle) * maxTurn;
            }
        }

        // 车身与摄像机从可见位置、可见朝向更新，而非直接从下一格跳转。
        updateCamera();
        updateMinimap();
    }

    private void updateCamera() {
        Vector3f position = carNode.getLocalTranslation();
        float forwardX = FastMath.sin(currentYaw);
        float forwardZ = FastMath.cos(currentYaw);

        float eyeY = 0.1f;
        cam.setLocation(new Vector3f(
            position.x + forwardX * 0.25f,
            eyeY,
            position.z + forwardZ * 0.25f
        ));
        cam.lookAt(new Vector3f(
            position.x + forwardX * 5f,
            eyeY,
            position.z + forwardZ * 5f
        ), Vector3f.UNIT_Y);

        carNode.setLocalRotation(
            new Quaternion().fromAngleAxis(currentYaw, Vector3f.UNIT_Y)
        );
    }

    // 在屏幕右上角绘制一个俯视 2D 小地图。
    private void createMinimap() {
        float mapWidth = grid[0].length * minimapCellSize + minimapPadding * 2f;
        float mapHeight = grid.length * minimapCellSize + minimapPadding * 2f;

        minimapLeft = cam.getWidth() - mapWidth - 20f;
        minimapBottom = cam.getHeight() - mapHeight - 20f;

        // 深色底板，让小地图与第一人称画面区分开。
        addMinimapQuad(
            "MinimapBackground", mapWidth, mapHeight,
            new ColorRGBA(0.10f, 0.14f, 0.20f, 1f),
            minimapLeft, minimapBottom, 0f
        );

        // 根据原始 grid 绘制墙壁、道路、起点和终点。
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                ColorRGBA color;
                if (grid[row][col] == '#') {
                    color = new ColorRGBA(0.30f, 0.40f, 0.53f, 1f);
                } else if (grid[row][col] == 'A') {
                    color = new ColorRGBA(0.95f, 0.62f, 0.25f, 1f);
                } else if (grid[row][col] == 'G') {
                    color = new ColorRGBA(0.25f, 0.72f, 0.46f, 1f);
                } else {
                    color = new ColorRGBA(0.82f, 0.86f, 0.89f, 1f);
                }

                float cellX = minimapLeft + minimapPadding + col * minimapCellSize;
                float cellY = minimapBottom + minimapPadding
                    + (grid.length - 1 - row) * minimapCellSize;

                addMinimapQuad(
                    "MinimapCell_" + row + "_" + col,
                    minimapCellSize - 2f, minimapCellSize - 2f,
                    color, cellX + 1f, cellY + 1f, 1f
                );
            }
        }

        // 用细长的橙色指针表示小车位置和朝向。
        minimapCarNode = new Node("MinimapCarMarker");
        Geometry marker = new Geometry("MinimapCarArrow", new Quad(7f, 14f));
        Material markerMat = new Material(
            assetManager, "Common/MatDefs/Misc/Unshaded.j3md"
        );
        markerMat.setColor("Color", new ColorRGBA(0.10f, 0.16f, 0.23f, 1f));
        marker.setMaterial(markerMat);
        marker.setLocalTranslation(-3.5f, -7f, 0f); // 让指针以中心点旋转
        minimapCarNode.attachChild(marker);
        guiNode.attachChild(minimapCarNode);

        updateMinimap();
    }

    private void addMinimapQuad(
            String name, float width, float height, ColorRGBA color,
            float x, float y, float z) {
        Geometry quad = new Geometry(name, new Quad(width, height));
        Material material = new Material(
            assetManager, "Common/MatDefs/Misc/Unshaded.j3md"
        );
        material.setColor("Color", color);
        quad.setMaterial(material);
        quad.setLocalTranslation(x, y, z);
        guiNode.attachChild(quad);
    }

    // 使用小车当前的实际 3D 位置，而不是逻辑目标格子，保证动画同步。
    private void updateMinimap() {
        if (minimapCarNode == null || carNode == null) {
            return;
        }

        Vector3f position = carNode.getLocalTranslation();
        float col = (position.x + 4f) / 2f;
        float row = (position.z + 4f) / 2f;

        float markerX = minimapLeft + minimapPadding
            + (col + 0.5f) * minimapCellSize;
        float markerY = minimapBottom + minimapPadding
            + (grid.length - row - 0.5f) * minimapCellSize;

        minimapCarNode.setLocalTranslation(markerX, markerY, 2f);

        // GUI 的 Y 轴向上；地图 row 增大代表向下。
        float screenDirX = FastMath.sin(currentYaw);
        float screenDirY = -FastMath.cos(currentYaw);
        float angle = FastMath.atan2(screenDirY, screenDirX) - FastMath.HALF_PI;
        minimapCarNode.setLocalRotation(
            new Quaternion().fromAngleAxis(angle, Vector3f.UNIT_Z)
        );
    }

}
