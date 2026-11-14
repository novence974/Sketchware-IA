package pro.sketchware.activities.game3d.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class Game3DModelTest {

    private static final float EPS = 1e-4f;

    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            f.setAccessible(true);
            fields.add(f);
        }
        return fields;
    }

    private static Object distinctValue(Field field, Object current) {
        Class<?> t = field.getType();
        if (t == boolean.class) return !((Boolean) current);
        if (t == int.class) return ((Integer) current) + 13;
        if (t == float.class) return ((Float) current) + 2.75f;
        if (t == String.class) return "probe_" + field.getName();
        throw new AssertionError("unhandled field type " + t + " on " + field.getName());
    }

    /**
     * copy() is maintained by hand, so a field added to the bean would be
     * dropped from every duplicate and every runtime instance until someone
     * noticed. Reflection makes the omission fail here instead.
     */
    @Test
    public void copyCarriesEveryField() throws Exception {
        Game3DObjectBean source = new Game3DObjectBean();
        List<Field> fields = instanceFields(Game3DObjectBean.class);
        assertFalse(fields.isEmpty());
        for (Field f : fields) {
            f.set(source, distinctValue(f, f.get(source)));
        }

        Game3DObjectBean clone = source.copy();
        List<String> dropped = new ArrayList<>();
        for (Field f : fields) {
            if (!f.get(source).equals(f.get(clone))) {
                dropped.add(f.getName());
            }
        }
        assertEquals("copy() dropped fields: " + dropped, 0, dropped.size());
    }

    @Test
    public void ensureInitializedRepairsAProjectSavedBeforeTheseFieldsExisted() {
        Game3DObjectBean legacy = new Game3DObjectBean();
        // Gson leaves absent numeric fields at 0.
        legacy.scaleX = 0f;
        legacy.scaleY = 0f;
        legacy.scaleZ = 0f;
        legacy.sizeX = 0f;
        legacy.radius = 0f;
        legacy.height = 0f;
        legacy.segments = 0;
        legacy.rings = 0;
        legacy.mass = 0f;
        legacy.collisionMask = 0;
        legacy.opacity = 0f;
        legacy.nearClip = 0f;
        legacy.farClip = 0f;
        legacy.fieldOfView = 0f;

        legacy.ensureInitialized();

        assertEquals("a zero scale hides the object and breaks its normal matrix",
                1f, legacy.scaleX, EPS);
        assertEquals(1f, legacy.scaleY, EPS);
        assertEquals(1f, legacy.scaleZ, EPS);
        assertEquals(1f, legacy.sizeX, EPS);
        assertTrue(legacy.radius > 0f);
        assertTrue(legacy.height > 0f);
        assertTrue("a sphere needs at least a triangle's worth of segments", legacy.segments >= 3);
        assertTrue(legacy.rings >= 2);
        assertEquals(1f, legacy.mass, EPS);
        assertEquals("a zero mask would collide with nothing", -1, legacy.collisionMask);
        assertEquals(1f, legacy.opacity, EPS);
        assertTrue(legacy.nearClip > 0f);
        assertTrue("far must stay beyond near or the depth buffer collapses",
                legacy.farClip > legacy.nearClip);
        assertEquals(60f, legacy.fieldOfView, EPS);
    }

    @Test
    public void collisionMaskFiltersSymmetrically() {
        Game3DObjectBean player = new Game3DObjectBean();
        Game3DObjectBean enemy = new Game3DObjectBean();
        enemy.collisionLayer = 3;
        assertTrue(player.collidesWith(enemy));

        player.collisionMask = ~(1 << 3);
        assertFalse(player.collidesWith(enemy));
        assertFalse(enemy.collidesWith(player));
        assertFalse("a null partner must not throw", player.collidesWith(null));
    }

    @Test
    public void materialMirrorsTheAppearanceFields() {
        Game3DObjectBean bean = new Game3DObjectBean();
        bean.color = 0xFF112233;
        bean.opacity = 0.5f;
        bean.shininess = 64f;
        bean.emissive = 0.2f;
        bean.doubleSided = true;

        var mat = bean.toMaterial();
        assertEquals(0xFF112233, mat.baseColor);
        assertEquals(0.5f, mat.opacity, EPS);
        assertEquals(64f, mat.shininess, EPS);
        assertTrue(mat.isTransparent());
        assertTrue(mat.doubleSided);
        assertEquals(0x11 / 255f, mat.red(), 1e-3f);
    }

    @Test
    public void approximateRadiusGrowsWithScale() {
        Game3DObjectBean sphere = Game3DObjectBean.createDefault(Game3DObjectBean.MESH_SPHERE, 1);
        float before = sphere.approximateRadius();
        sphere.scaleX = sphere.scaleY = sphere.scaleZ = 4f;
        assertTrue("scaling an object must widen its cull radius",
                sphere.approximateRadius() > before);
        for (String mesh : new String[]{
                Game3DObjectBean.MESH_CUBE, Game3DObjectBean.MESH_SPHERE,
                Game3DObjectBean.MESH_CYLINDER, Game3DObjectBean.MESH_CONE,
                Game3DObjectBean.MESH_TORUS, Game3DObjectBean.MESH_PLANE,
                Game3DObjectBean.MESH_QUAD, Game3DObjectBean.MESH_NONE}) {
            Game3DObjectBean bean = Game3DObjectBean.createDefault(mesh, 1);
            assertTrue(mesh + " must have a positive cull radius", bean.approximateRadius() > 0f);
        }
    }

    @Test
    public void everyPaletteEntryProducesAValidObject() {
        for (String mesh : new String[]{
                Game3DObjectBean.MESH_CUBE, Game3DObjectBean.MESH_SPHERE,
                Game3DObjectBean.MESH_PLANE, Game3DObjectBean.MESH_CYLINDER,
                Game3DObjectBean.MESH_CONE, Game3DObjectBean.MESH_TORUS,
                Game3DObjectBean.MESH_QUAD, Game3DObjectBean.MESH_MODEL,
                Game3DObjectBean.MESH_NONE}) {
            Game3DObjectBean bean = Game3DObjectBean.createDefault(mesh, 2);
            assertEquals(mesh, bean.meshType);
            assertFalse(bean.id.isEmpty());
            assertFalse(bean.name.isEmpty());
            assertTrue(bean.scaleX > 0f && bean.scaleY > 0f && bean.scaleZ > 0f);
        }
        assertEquals(Game3DObjectBean.KIND_LIGHT, Game3DObjectBean.createLight(0, 1).kind);
        assertEquals(Game3DObjectBean.KIND_CAMERA, Game3DObjectBean.createCamera(1).kind);
    }

    @Test
    public void starterSceneIsUsableNotEmpty() {
        Game3DScene scene = Game3DScene.createStarter("Level 1");
        assertEquals("Level 1", scene.name);
        assertTrue("a new scene must have something to look at", scene.objects.size() >= 3);
        assertNotNull("and a camera to look through", scene.primaryCamera());
        assertEquals(1, scene.countOfKind(Game3DObjectBean.KIND_LIGHT));
        assertNotNull(scene.findById(scene.objects.get(0).id));
        assertNull(scene.findById("nope"));
    }

    @Test
    public void sceneOrdersItsFogPlanes() {
        Game3DScene scene = new Game3DScene();
        scene.fogStart = 100f;
        scene.fogEnd = 10f;
        scene.ensureInitialized();
        assertTrue("fog with start past end gives a negative factor and black geometry",
                scene.fogEnd > scene.fogStart);
    }

    @Test
    public void sceneClampsTheEditorCameraPitch() {
        Game3DScene scene = new Game3DScene();
        scene.editorCamPitch = 150f;
        scene.editorCamDistance = 0f;
        scene.ensureInitialized();
        assertTrue(scene.editorCamPitch <= 89.5f);
        assertTrue(scene.editorCamDistance > 0f);
    }

    @Test
    public void projectAlwaysHasAStartScene() {
        Game3DProject project = new Game3DProject();
        project.ensureInitialized("sc-1");
        assertEquals("sc-1", project.scId);
        assertFalse("an empty project must be given a scene", project.scenes.isEmpty());
        assertNotNull(project.startScene());
        assertEquals(project.scenes.get(0).id, project.startSceneId);
    }

    @Test
    public void projectRepairsADanglingStartSceneId() {
        Game3DProject project = new Game3DProject();
        project.scenes.add(Game3DScene.createStarter("A"));
        project.startSceneId = "deleted-scene";
        project.ensureInitialized("sc-2");
        assertEquals("a start scene that no longer exists must fall back",
                project.scenes.get(0).id, project.startSceneId);
    }

    @Test
    public void sceneAfterWrapsAtTheEnd() {
        Game3DProject project = new Game3DProject();
        Game3DScene a = Game3DScene.createStarter("A");
        Game3DScene b = Game3DScene.createStarter("B");
        project.scenes.add(a);
        project.scenes.add(b);
        project.ensureInitialized("sc-3");
        assertEquals(b.id, project.sceneAfter(a.id).id);
        assertEquals("the last scene wraps to the first", a.id, project.sceneAfter(b.id).id);
        assertEquals("an unknown id falls back to the first", a.id, project.sceneAfter("nope").id);
    }
}
