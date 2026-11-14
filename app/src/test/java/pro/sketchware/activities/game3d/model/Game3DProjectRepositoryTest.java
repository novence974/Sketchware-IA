package pro.sketchware.activities.game3d.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JSON is how a project survives between sessions, so what matters is that a
 * round trip loses nothing and that a damaged file still opens.
 */
public class Game3DProjectRepositoryTest {

    private static final float EPS = 1e-4f;

    private static Game3DProject sampleProject() {
        Game3DProject project = new Game3DProject();
        project.scId = "sc-42";
        project.name = "Dungeon";
        project.landscape = false;
        project.msaaSamples = 2;
        project.showGrid = false;
        project.globalVariables.put("score", "0");

        Game3DScene scene = new Game3DScene();
        scene.name = "Crypt";
        scene.fogEnabled = true;
        scene.fogStart = 5f;
        scene.fogEnd = 80f;
        scene.ambientIntensity = 0.2f;
        scene.gravityY = -20f;

        Game3DObjectBean hero = Game3DObjectBean.createDefault(Game3DObjectBean.MESH_SPHERE, 1);
        hero.name = "Hero";
        hero.tag = "player";
        hero.x = 1.5f;
        hero.y = 2.25f;
        hero.z = -3.75f;
        hero.rotationY = 42f;
        hero.scaleX = 1.5f;
        hero.maxHealth = 5f;
        hero.health = 3f;
        hero.customProps = "speed=7";
        hero.collisionLayer = 2;
        hero.collisionMask = 0b1011;
        scene.objects.add(hero);
        scene.objects.add(Game3DObjectBean.createLight(1, 1));
        scene.objects.add(Game3DObjectBean.createCamera(1));

        project.scenes.add(scene);
        project.ensureInitialized("sc-42");
        return project;
    }

    @Test
    public void roundTripPreservesTheProject() {
        Game3DProject original = sampleProject();
        String json = Game3DProjectRepository.toJson(original);
        assertNotNull(json);
        assertFalse(json.trim().isEmpty());

        Game3DProject reloaded = Game3DProjectRepository.fromJson(json, "sc-42");
        assertEquals("Dungeon", reloaded.name);
        assertEquals("sc-42", reloaded.scId);
        assertFalse(reloaded.landscape);
        assertEquals(2, reloaded.msaaSamples);
        assertFalse(reloaded.showGrid);
        assertEquals("0", reloaded.globalVariables.get("score"));
        assertEquals(1, reloaded.scenes.size());

        Game3DScene scene = reloaded.scenes.get(0);
        assertEquals("Crypt", scene.name);
        assertTrue(scene.fogEnabled);
        assertEquals(80f, scene.fogEnd, EPS);
        assertEquals(-20f, scene.gravityY, EPS);
        assertEquals(3, scene.objects.size());

        Game3DObjectBean hero = null;
        for (Game3DObjectBean obj : scene.objects) {
            if ("Hero".equals(obj.name)) {
                hero = obj;
            }
        }
        assertNotNull("the hero must survive the round trip", hero);
        assertEquals(1.5f, hero.x, EPS);
        assertEquals(2.25f, hero.y, EPS);
        assertEquals(-3.75f, hero.z, EPS);
        assertEquals(42f, hero.rotationY, EPS);
        assertEquals(1.5f, hero.scaleX, EPS);
        assertEquals(5f, hero.maxHealth, EPS);
        assertEquals("a partly damaged hero must stay partly damaged", 3f, hero.health, EPS);
        assertEquals("speed=7", hero.customProps);
        assertEquals(2, hero.collisionLayer);
        assertEquals(0b1011, hero.collisionMask);
        assertEquals("player", hero.tag);
    }

    @Test
    public void roundTripIsStable() {
        // A second pass must not drift: ensureInitialized runs on every load,
        // so a value it "repairs" each time would change the file forever.
        Game3DProject first = Game3DProjectRepository.fromJson(
                Game3DProjectRepository.toJson(sampleProject()), "sc-42");
        String json1 = Game3DProjectRepository.toJson(first);
        String json2 = Game3DProjectRepository.toJson(
                Game3DProjectRepository.fromJson(json1, "sc-42"));
        assertEquals("saving a loaded project must produce the same file", json1, json2);
    }

    @Test
    public void emptyAndMalformedJsonStillOpen() {
        for (String bad : new String[]{
                null, "", "   ", "{", "not json at all", "[]", "{\"scenes\":null}"}) {
            Game3DProject project = Game3DProjectRepository.fromJson(bad, "sc-x");
            assertNotNull("input " + bad + " must still give a project", project);
            assertFalse("and it must be openable", project.scenes.isEmpty());
            assertNotNull(project.startScene());
        }
    }

    @Test
    public void aProjectSavedWithoutTheNewerFieldsIsRepairedOnLoad() {
        // Gson leaves absent numbers at 0; the loader must not hand the editor
        // zero-scaled objects and a collapsed camera.
        String legacy = "{\"name\":\"Old\",\"scenes\":[{\"id\":\"s1\",\"name\":\"A\","
                + "\"objects\":[{\"id\":\"o1\",\"name\":\"Box\",\"meshType\":\"cube\"}]}],"
                + "\"startSceneId\":\"s1\"}";
        Game3DProject project = Game3DProjectRepository.fromJson(legacy, "sc-old");
        Game3DObjectBean box = project.scenes.get(0).objects.get(0);
        assertEquals(1f, box.scaleX, EPS);
        assertEquals(1f, box.scaleY, EPS);
        assertEquals(1f, box.scaleZ, EPS);
        assertTrue(box.farClip > box.nearClip);
        assertEquals(-1, box.collisionMask);
        assertEquals("s1", project.startSceneId);
    }

    @Test
    public void jsonIsReadableRatherThanOneLine() {
        String json = Game3DProjectRepository.toJson(sampleProject());
        assertTrue("the file should be diffable in a repository", json.contains("\n"));
        assertTrue(json.contains("\"name\""));
    }

    @Test
    public void aNullProjectSerialisesToAnEmptyObject() {
        assertEquals("{}", Game3DProjectRepository.toJson(null));
    }
}
