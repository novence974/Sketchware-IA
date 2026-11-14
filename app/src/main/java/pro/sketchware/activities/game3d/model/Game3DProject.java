package pro.sketchware.activities.game3d.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** A whole 3D game: its scenes and the settings that apply to all of them. */
public class Game3DProject {

    public String scId = "";
    public String name = "3D Game";
    public ArrayList<Game3DScene> scenes = new ArrayList<>();
    public String startSceneId = "";

    public boolean landscape = true;
    public int targetFps = 60;
    /** Multi-sample anti-aliasing samples; 0 turns it off. */
    public int msaaSamples = 4;
    public boolean showDebugColliders = false;
    public boolean showGrid = true;
    public boolean backFaceCulling = true;

    public LinkedHashMap<String, String> globalVariables = new LinkedHashMap<>();

    public void ensureInitialized(String scId) {
        if (this.scId == null || this.scId.isEmpty()) {
            this.scId = scId == null ? "" : scId;
        }
        if (name == null || name.isEmpty()) name = "3D Game";
        if (scenes == null) scenes = new ArrayList<>();
        if (globalVariables == null) globalVariables = new LinkedHashMap<>();
        if (targetFps < 15) targetFps = 60;
        if (msaaSamples < 0) msaaSamples = 0;

        if (scenes.isEmpty()) {
            scenes.add(Game3DScene.createStarter("Scene 1"));
        }
        for (Game3DScene scene : scenes) {
            if (scene != null) scene.ensureInitialized();
        }
        if (startSceneId == null || findScene(startSceneId) == null) {
            startSceneId = scenes.get(0).id;
        }
    }

    /** A project that already has something in it, for a brand new game. */
    public static Game3DProject createDefault(String scId) {
        Game3DProject project = new Game3DProject();
        project.scId = scId == null ? "" : scId;
        project.scenes.add(Game3DScene.createStarter("Scene 1"));
        project.ensureInitialized(project.scId);
        return project;
    }

    public Game3DScene findScene(String sceneId) {
        if (sceneId == null) return null;
        for (Game3DScene scene : scenes) {
            if (scene != null && sceneId.equals(scene.id)) {
                return scene;
            }
        }
        return null;
    }

    public Game3DScene startScene() {
        Game3DScene scene = findScene(startSceneId);
        return scene != null ? scene : (scenes.isEmpty() ? null : scenes.get(0));
    }

    /** The scene after {@code sceneId} in order, wrapping at the end. */
    public Game3DScene sceneAfter(String sceneId) {
        if (scenes.isEmpty()) return null;
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i) != null && scenes.get(i).id.equals(sceneId)) {
                return scenes.get((i + 1) % scenes.size());
            }
        }
        return scenes.get(0);
    }
}
