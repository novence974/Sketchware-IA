package pro.sketchware.activities.game.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class GameObjectBeanTest {

    /** Every instance field, in declaration order. */
    private static List<Field> instanceFields() {
        List<Field> fields = new ArrayList<>();
        for (Field f : GameObjectBean.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                continue;
            }
            f.setAccessible(true);
            fields.add(f);
        }
        return fields;
    }

    /** A value that differs from whatever a fresh bean holds. */
    private static Object distinctValue(Field field, Object current) {
        Class<?> type = field.getType();
        if (type == boolean.class) {
            return !((Boolean) current);
        }
        if (type == int.class) {
            return ((Integer) current) + 7;
        }
        if (type == float.class) {
            return ((Float) current) + 3.5f;
        }
        if (type == String.class) {
            return "probe_" + field.getName();
        }
        throw new AssertionError("unhandled field type " + type + " on " + field.getName());
    }

    /**
     * {@link GameObjectBean#copy()} is written out by hand, so a field added
     * to the class is silently dropped from every duplicate, clone and
     * runtime instance until someone remembers to extend it. This walks the
     * class by reflection so the omission fails here instead.
     */
    @Test
    public void copyCarriesEveryField() throws Exception {
        GameObjectBean source = new GameObjectBean();
        List<Field> fields = instanceFields();
        assertFalse("the bean must have fields to check", fields.isEmpty());

        for (Field field : fields) {
            field.set(source, distinctValue(field, field.get(source)));
        }

        GameObjectBean clone = source.copy();

        List<String> dropped = new ArrayList<>();
        for (Field field : fields) {
            if (!field.get(source).equals(field.get(clone))) {
                dropped.add(field.getName());
            }
        }
        assertEquals("copy() dropped fields: " + dropped, 0, dropped.size());
    }

    @Test
    public void ensureInitializedNormalisesLegacyZeroes() {
        // A project saved before these fields existed deserializes them as 0,
        // which would read as "no collider, weightless, collides with nothing".
        GameObjectBean legacy = new GameObjectBean();
        legacy.colliderScaleX = 0f;
        legacy.colliderScaleY = 0f;
        legacy.mass = 0f;
        legacy.collisionMask = 0;
        legacy.pivotX = 0f;
        legacy.pivotY = 0f;

        legacy.ensureInitialized();

        assertEquals(1f, legacy.colliderScaleX, 0.0001f);
        assertEquals(1f, legacy.colliderScaleY, 0.0001f);
        assertEquals(1f, legacy.mass, 0.0001f);
        assertEquals("a zero mask would disable every collision", -1, legacy.collisionMask);
        assertEquals("an explicit 0 pivot is a corner, and legal", 0f, legacy.pivotX, 0.0001f);
    }

    @Test
    public void ensureInitializedClampsOutOfRangeValues() {
        GameObjectBean bean = new GameObjectBean();
        bean.pivotX = 4f;
        bean.pivotY = -2f;
        bean.collisionLayer = 99;
        bean.maxHealth = 50f;
        bean.health = 999f;
        bean.invincibleSec = -5f;

        bean.ensureInitialized();

        assertEquals(1f, bean.pivotX, 0.0001f);
        assertEquals(0f, bean.pivotY, 0.0001f);
        assertEquals(0, bean.collisionLayer);
        assertEquals("health cannot exceed maxHealth", 50f, bean.health, 0.0001f);
        assertEquals(0f, bean.invincibleSec, 0.0001f);
    }

    @Test
    public void healthDefaultsToFullWhenMaxIsSet() {
        GameObjectBean bean = new GameObjectBean();
        bean.maxHealth = 3f;
        bean.ensureInitialized();
        assertEquals(3f, bean.health, 0.0001f);
    }

    @Test
    public void colliderFollowsOffsetAndScale() {
        GameObjectBean bean = new GameObjectBean();
        bean.x = 100f;
        bean.y = 200f;
        bean.width = 80f;
        bean.height = 40f;
        bean.colliderScaleX = 0.5f;
        bean.colliderScaleY = 0.5f;

        // Shrinking keeps the collider centred on the object.
        assertEquals(40f, bean.colliderWidth(), 0.0001f);
        assertEquals(20f, bean.colliderHeight(), 0.0001f);
        assertEquals(120f, bean.colliderLeft(), 0.0001f);
        assertEquals(210f, bean.colliderTop(), 0.0001f);

        bean.colliderOffsetX = 10f;
        assertEquals(130f, bean.colliderLeft(), 0.0001f);
    }

    @Test
    public void colliderNeverCollapsesToNothing() {
        GameObjectBean bean = new GameObjectBean();
        bean.width = 0.001f;
        bean.height = 0.001f;
        assertTrue(bean.colliderWidth() >= 1f);
        assertTrue(bean.colliderHeight() >= 1f);
    }

    @Test
    public void collisionMaskFiltersBothWays() {
        GameObjectBean player = new GameObjectBean();
        player.collisionLayer = 0;
        GameObjectBean enemy = new GameObjectBean();
        enemy.collisionLayer = 1;
        GameObjectBean scenery = new GameObjectBean();
        scenery.collisionLayer = 2;

        // Default: everything sees everything.
        assertTrue(player.collidesWith(enemy));

        // The player stops seeing layer 1 — and the pair stops matching even
        // though the enemy still lists the player's layer.
        player.collisionMask = ~(1 << 1);
        assertFalse(player.collidesWith(enemy));
        assertFalse("filtering must be symmetric", enemy.collidesWith(player));
        assertTrue(player.collidesWith(scenery));
    }

    @Test
    public void customPropsReadBackByKey() {
        GameObjectBean bean = new GameObjectBean();
        bean.customProps = "speedBoost=2.5; dropsKey = yes ;empty=";

        assertEquals("2.5", bean.customProp("speedBoost", "0"));
        assertEquals("whitespace around keys and values is trimmed",
                "yes", bean.customProp("dropsKey", "no"));
        assertEquals("", bean.customProp("empty", "fallback"));
        assertEquals("fallback", bean.customProp("absent", "fallback"));
        assertEquals(2.5f, bean.customPropFloat("speedBoost", 0f), 0.0001f);
        assertEquals("a non-numeric value falls back instead of throwing",
                1f, bean.customPropFloat("dropsKey", 1f), 0.0001f);
    }

    @Test
    public void customPropsToleratesMalformedInput() {
        GameObjectBean bean = new GameObjectBean();
        bean.customProps = ";;=novalue;nokey;a=1";
        assertEquals("1", bean.customProp("a", "x"));
        assertEquals("x", bean.customProp("nokey", "x"));
    }

    @Test
    public void damageStopsAtZeroAndMarksDeath() {
        GameObjectBean bean = new GameObjectBean();
        bean.maxHealth = 2f;
        bean.ensureInitialized();

        assertTrue(bean.applyDamage(1f));
        assertEquals(1f, bean.health, 0.0001f);
        assertFalse(bean.isDead());

        assertTrue(bean.applyDamage(5f));
        assertEquals("health never goes negative", 0f, bean.health, 0.0001f);
        assertTrue(bean.isDead());
    }

    @Test
    public void damageIsIgnoredWithoutAHealthPool() {
        GameObjectBean bean = new GameObjectBean();
        assertEquals(0f, bean.maxHealth, 0.0001f);
        assertFalse("maxHealth 0 means the object has no health system",
                bean.applyDamage(10f));
        assertFalse(bean.isDead());
    }

    @Test
    public void createDefaultForTypeStillProducesValidObjects() {
        for (String type : new String[]{
                GameObjectBean.TYPE_SPRITE, GameObjectBean.TYPE_PHYSICS_CIRCLE,
                GameObjectBean.TYPE_HUD_BUTTON, GameObjectBean.TYPE_SPAWNER,
                GameObjectBean.TYPE_PROJECTILE}) {
            GameObjectBean bean = GameObjectBean.createDefaultForType(type, 10f, 20f, 1);
            bean.ensureInitialized();
            assertEquals(type, bean.type);
            assertTrue("new transform defaults must survive creation",
                    bean.colliderScaleX > 0f && bean.mass > 0f);
            assertNotEquals("", bean.id);
        }
    }
}
