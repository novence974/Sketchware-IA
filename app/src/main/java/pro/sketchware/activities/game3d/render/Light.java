package pro.sketchware.activities.game3d.render;

import pro.sketchware.activities.game3d.math.Vec3;

/** A light source. Directional lights ignore position; point lights use it. */
public final class Light {

    public static final int TYPE_DIRECTIONAL = 0;
    public static final int TYPE_POINT = 1;

    public int type = TYPE_DIRECTIONAL;
    public final Vec3 position = new Vec3(0f, 10f, 0f);
    /** Direction the light travels; normalised on use. */
    public final Vec3 direction = new Vec3(-0.4f, -1f, -0.3f);
    public int color = 0xFFFFFFFF;
    public float intensity = 1f;
    /** Distance at which a point light has faded out. */
    public float range = 25f;
    public boolean enabled = true;

    public static Light directional(float dx, float dy, float dz, int color, float intensity) {
        Light light = new Light();
        light.type = TYPE_DIRECTIONAL;
        light.direction.set(dx, dy, dz).normalize();
        light.color = color;
        light.intensity = intensity;
        return light;
    }

    public static Light point(float x, float y, float z, int color, float intensity, float range) {
        Light light = new Light();
        light.type = TYPE_POINT;
        light.position.set(x, y, z);
        light.color = color;
        light.intensity = intensity;
        light.range = Math.max(0.01f, range);
        return light;
    }

    public float red() {
        return ((color >> 16) & 0xFF) / 255f;
    }

    public float green() {
        return ((color >> 8) & 0xFF) / 255f;
    }

    public float blue() {
        return (color & 0xFF) / 255f;
    }
}
