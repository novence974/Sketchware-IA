package pro.sketchware.activities.game3d.render;

/**
 * How a surface answers light. The shading model picks which shader program
 * the renderer binds.
 */
public final class Material {

    /** Flat colour, no lighting: UI, wireframes, unlit effects. */
    public static final int SHADING_UNLIT = 0;
    /** Diffuse only: cheap and stable on low-end phones. */
    public static final int SHADING_LAMBERT = 1;
    /** Diffuse plus a specular highlight. */
    public static final int SHADING_PHONG = 2;

    public int shading = SHADING_LAMBERT;
    public int baseColor = 0xFFCCCCCC;
    /** Texture asset name; empty means the base colour is used alone. */
    public String textureName = "";
    public float specularStrength = 0.35f;
    /** Larger is a tighter highlight. */
    public float shininess = 32f;
    /** 0 is fully transparent, 1 opaque. */
    public float opacity = 1f;
    public boolean doubleSided = false;
    /** Added regardless of lighting; useful for screens and lamps. */
    public float emissive = 0f;

    public Material() {
    }

    public Material(int shading, int baseColor) {
        this.shading = shading;
        this.baseColor = baseColor;
    }

    public boolean isTransparent() {
        return opacity < 0.999f;
    }

    public float red() {
        return ((baseColor >> 16) & 0xFF) / 255f;
    }

    public float green() {
        return ((baseColor >> 8) & 0xFF) / 255f;
    }

    public float blue() {
        return (baseColor & 0xFF) / 255f;
    }

    public float alpha() {
        return Math.max(0f, Math.min(1f, opacity * (((baseColor >>> 24) & 0xFF) / 255f)));
    }

    public Material copy() {
        Material c = new Material();
        c.shading = shading;
        c.baseColor = baseColor;
        c.textureName = textureName;
        c.specularStrength = specularStrength;
        c.shininess = shininess;
        c.opacity = opacity;
        c.doubleSided = doubleSided;
        c.emissive = emissive;
        return c;
    }
}
