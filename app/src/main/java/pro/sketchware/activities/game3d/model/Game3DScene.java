package pro.sketchware.activities.game3d.model;

import java.util.ArrayList;
import java.util.UUID;

/** One 3D level: its objects, its environment and its physics settings. */
public class Game3DScene {

    public static final String SKY_SOLID = "solid";
    public static final String SKY_GRADIENT = "gradient";

    public String id;
    public String name = "Scene";

    public ArrayList<Game3DObjectBean> objects = new ArrayList<>();

    // --- Environment -------------------------------------------------------
    public String skyMode = SKY_GRADIENT;
    public int skyColorTop = 0xFF4A7BC8;
    public int skyColorBottom = 0xFFBFD4F2;
    public int ambientColor = 0xFF404040;
    public float ambientIntensity = 0.45f;
    public boolean fogEnabled = false;
    public int fogColor = 0xFFBFD4F2;
    public float fogStart = 30f;
    public float fogEnd = 160f;

    // --- Physics -----------------------------------------------------------
    public float gravityX = 0f;
    public float gravityY = -9.81f;
    public float gravityZ = 0f;

    // --- Editor camera, remembered per scene -------------------------------
    public float editorCamYaw = 35f;
    public float editorCamPitch = 25f;
    public float editorCamDistance = 12f;
    public float editorCamTargetX = 0f;
    public float editorCamTargetY = 0f;
    public float editorCamTargetZ = 0f;

    public Game3DScene() {
        this.id = "scene3d_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public void ensureInitialized() {
        if (id == null || id.isEmpty()) {
            id = "scene3d_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (name == null || name.isEmpty()) name = "Scene";
        if (objects == null) objects = new ArrayList<>();
        if (skyMode == null || skyMode.isEmpty()) skyMode = SKY_GRADIENT;
        if (ambientIntensity < 0f) ambientIntensity = 0f;
        // Fog with start beyond end produces a negative factor and black
        // geometry, so the pair is ordered rather than trusted.
        if (fogEnd <= fogStart) fogEnd = fogStart + 1f;
        if (editorCamDistance <= 0f) editorCamDistance = 12f;
        editorCamPitch = Math.max(-89.5f, Math.min(89.5f, editorCamPitch));
        for (Game3DObjectBean obj : objects) {
            if (obj != null) obj.ensureInitialized();
        }
    }

    public Game3DObjectBean findById(String objectId) {
        if (objectId == null) return null;
        for (Game3DObjectBean obj : objects) {
            if (obj != null && objectId.equals(obj.id)) {
                return obj;
            }
        }
        return null;
    }

    /** The first camera object, or null when the scene has none. */
    public Game3DObjectBean primaryCamera() {
        for (Game3DObjectBean obj : objects) {
            if (obj != null && Game3DObjectBean.KIND_CAMERA.equals(obj.kind)) {
                return obj;
            }
        }
        return null;
    }

    public int countOfKind(String kind) {
        int n = 0;
        for (Game3DObjectBean obj : objects) {
            if (obj != null && obj.kind != null && obj.kind.equals(kind)) {
                n++;
            }
        }
        return n;
    }

    /** A new scene that is not empty: a ground plane, a light and a camera. */
    public static Game3DScene createStarter(String name) {
        Game3DScene scene = new Game3DScene();
        scene.name = name == null || name.isEmpty() ? "Scene 1" : name;

        Game3DObjectBean ground = Game3DObjectBean.createDefault(Game3DObjectBean.MESH_PLANE, 1);
        ground.name = "Ground";
        scene.objects.add(ground);

        Game3DObjectBean cube = Game3DObjectBean.createDefault(Game3DObjectBean.MESH_CUBE, 1);
        cube.y = 0.5f;
        scene.objects.add(cube);

        scene.objects.add(Game3DObjectBean.createLight(0, 1));
        scene.objects.add(Game3DObjectBean.createCamera(1));

        scene.ensureInitialized();
        return scene;
    }
}
