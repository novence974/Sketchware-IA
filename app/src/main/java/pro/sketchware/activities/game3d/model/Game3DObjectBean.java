package pro.sketchware.activities.game3d.model;

import java.util.UUID;

import pro.sketchware.activities.game3d.render.Material;

/**
 * One entity in a 3D scene: a transform, something to draw, and how it
 * behaves. Mirrors the role {@code GameObjectBean} plays in the 2D engine.
 */
public class Game3DObjectBean {

    // What the object draws.
    public static final String MESH_CUBE = "cube";
    public static final String MESH_BOX = "box";
    public static final String MESH_SPHERE = "sphere";
    public static final String MESH_PLANE = "plane";
    public static final String MESH_CYLINDER = "cylinder";
    public static final String MESH_CONE = "cone";
    public static final String MESH_TORUS = "torus";
    public static final String MESH_QUAD = "quad";
    /** Geometry loaded from an imported .obj asset named by {@link #modelAsset}. */
    public static final String MESH_MODEL = "model";
    /** No geometry: lights, cameras, empty pivots. */
    public static final String MESH_NONE = "none";

    // What the object is.
    public static final String KIND_MESH = "mesh";
    public static final String KIND_LIGHT = "light";
    public static final String KIND_CAMERA = "camera";
    public static final String KIND_EMPTY = "empty";

    // How it collides.
    public static final String COLLIDER_NONE = "none";
    public static final String COLLIDER_BOX = "box";
    public static final String COLLIDER_SPHERE = "sphere";
    public static final String COLLIDER_MESH = "mesh";

    public String id;
    public String name = "Object";
    public String kind = KIND_MESH;

    // --- Transform ---------------------------------------------------------
    public float x = 0f, y = 0f, z = 0f;
    /** Euler angles in degrees. */
    public float rotationX = 0f, rotationY = 0f, rotationZ = 0f;
    public float scaleX = 1f, scaleY = 1f, scaleZ = 1f;

    // --- Geometry ----------------------------------------------------------
    public String meshType = MESH_CUBE;
    public String modelAsset = "";
    /** Primitive sizing; meaning depends on meshType. */
    public float sizeX = 1f, sizeY = 1f, sizeZ = 1f;
    public float radius = 0.5f;
    public float radiusTop = 0.5f;
    public float height = 1f;
    public int segments = 32;
    public int rings = 16;

    // --- Appearance --------------------------------------------------------
    public int shading = Material.SHADING_LAMBERT;
    public int color = 0xFFCCCCCC;
    public String textureAsset = "";
    public float opacity = 1f;
    public float specularStrength = 0.35f;
    public float shininess = 32f;
    public float emissive = 0f;
    public boolean doubleSided = false;
    public boolean castsShadow = true;
    public boolean visible = true;

    // --- Light (kind = light) ----------------------------------------------
    /** 0 directional, 1 point. */
    public int lightType = 0;
    public float lightIntensity = 1f;
    public float lightRange = 25f;

    // --- Camera (kind = camera) --------------------------------------------
    public float fieldOfView = 60f;
    public float nearClip = 0.1f;
    public float farClip = 500f;
    public boolean orthographic = false;
    public float orthoHeight = 10f;

    // --- Physics -----------------------------------------------------------
    public boolean physicsEnabled = false;
    public boolean isStatic = true;
    public boolean isTrigger = false;
    public String colliderType = COLLIDER_BOX;
    public float mass = 1f;
    public float bounce = 0f;
    public float friction = 0.4f;
    public float gravityScale = 1f;
    public float linearDamping = 0.02f;
    public float velocityX = 0f, velocityY = 0f, velocityZ = 0f;
    public int collisionLayer = 0;
    public int collisionMask = -1;

    // --- Gameplay ----------------------------------------------------------
    public String tag = "default";
    public float maxHealth = 0f;
    public float health = 0f;
    public String parentId = "";
    public String customProps = "";
    public boolean locked = false;

    public Game3DObjectBean() {
        this.id = "obj3d_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Fills in anything a project saved by an older version left at zero. */
    public void ensureInitialized() {
        if (id == null || id.isEmpty()) {
            id = "obj3d_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (name == null || name.isEmpty()) name = "Object";
        if (kind == null || kind.isEmpty()) kind = KIND_MESH;
        if (meshType == null || meshType.isEmpty()) meshType = MESH_CUBE;
        if (modelAsset == null) modelAsset = "";
        if (textureAsset == null) textureAsset = "";
        if (colliderType == null || colliderType.isEmpty()) colliderType = COLLIDER_BOX;
        if (tag == null || tag.isEmpty()) tag = "default";
        if (parentId == null) parentId = "";
        if (customProps == null) customProps = "";

        // A zero scale makes the object invisible and its normal matrix
        // singular, so it is treated as "never set" rather than as a choice.
        if (scaleX == 0f) scaleX = 1f;
        if (scaleY == 0f) scaleY = 1f;
        if (scaleZ == 0f) scaleZ = 1f;

        if (sizeX <= 0f) sizeX = 1f;
        if (sizeY <= 0f) sizeY = 1f;
        if (sizeZ <= 0f) sizeZ = 1f;
        if (radius <= 0f) radius = 0.5f;
        if (radiusTop < 0f) radiusTop = 0f;
        if (height <= 0f) height = 1f;
        if (segments < 3) segments = 32;
        if (rings < 2) rings = 16;

        if (opacity <= 0f) opacity = 1f;
        opacity = Math.min(1f, opacity);
        if (shininess <= 0f) shininess = 32f;
        if (specularStrength < 0f) specularStrength = 0f;
        if (emissive < 0f) emissive = 0f;

        if (mass <= 0f) mass = 1f;
        if (linearDamping < 0f) linearDamping = 0f;
        if (collisionLayer < 0 || collisionLayer > 31) collisionLayer = 0;
        if (collisionMask == 0) collisionMask = -1;

        if (lightIntensity < 0f) lightIntensity = 1f;
        if (lightRange <= 0f) lightRange = 25f;

        if (fieldOfView < 1f || fieldOfView > 179f) fieldOfView = 60f;
        if (nearClip <= 0f) nearClip = 0.1f;
        if (farClip <= nearClip) farClip = nearClip + 500f;
        if (orthoHeight <= 0f) orthoHeight = 10f;

        if (maxHealth < 0f) maxHealth = 0f;
        if (maxHealth > 0f && health <= 0f) health = maxHealth;
        if (maxHealth > 0f && health > maxHealth) health = maxHealth;
    }

    public boolean collidesWith(Game3DObjectBean other) {
        if (other == null) return false;
        return (collisionMask & (1 << other.collisionLayer)) != 0
                && (other.collisionMask & (1 << collisionLayer)) != 0;
    }

    public Material toMaterial() {
        Material mat = new Material();
        mat.shading = shading;
        mat.baseColor = color;
        mat.textureName = textureAsset;
        mat.opacity = opacity;
        mat.specularStrength = specularStrength;
        mat.shininess = shininess;
        mat.emissive = emissive;
        mat.doubleSided = doubleSided;
        return mat;
    }

    /** Longest half-extent after scale, used for culling and framing. */
    public float approximateRadius() {
        float sx = Math.abs(scaleX), sy = Math.abs(scaleY), sz = Math.abs(scaleZ);
        return switch (meshType) {
            case MESH_SPHERE -> radius * Math.max(sx, Math.max(sy, sz));
            case MESH_CYLINDER, MESH_CONE ->
                    Math.max(radius * Math.max(sx, sz), height * 0.5f * sy);
            case MESH_TORUS -> (sizeX + radius) * Math.max(sx, sz);
            case MESH_PLANE, MESH_QUAD -> Math.max(sizeX * sx, sizeZ * sz) * 0.5f;
            case MESH_NONE -> 0.5f;
            default -> 0.5f * (float) Math.sqrt(
                    sizeX * sx * sizeX * sx + sizeY * sy * sizeY * sy + sizeZ * sz * sizeZ * sz);
        };
    }

    public Game3DObjectBean copy() {
        Game3DObjectBean c = new Game3DObjectBean();
        c.id = id;
        c.name = name;
        c.kind = kind;
        c.x = x; c.y = y; c.z = z;
        c.rotationX = rotationX; c.rotationY = rotationY; c.rotationZ = rotationZ;
        c.scaleX = scaleX; c.scaleY = scaleY; c.scaleZ = scaleZ;
        c.meshType = meshType;
        c.modelAsset = modelAsset;
        c.sizeX = sizeX; c.sizeY = sizeY; c.sizeZ = sizeZ;
        c.radius = radius;
        c.radiusTop = radiusTop;
        c.height = height;
        c.segments = segments;
        c.rings = rings;
        c.shading = shading;
        c.color = color;
        c.textureAsset = textureAsset;
        c.opacity = opacity;
        c.specularStrength = specularStrength;
        c.shininess = shininess;
        c.emissive = emissive;
        c.doubleSided = doubleSided;
        c.castsShadow = castsShadow;
        c.visible = visible;
        c.lightType = lightType;
        c.lightIntensity = lightIntensity;
        c.lightRange = lightRange;
        c.fieldOfView = fieldOfView;
        c.nearClip = nearClip;
        c.farClip = farClip;
        c.orthographic = orthographic;
        c.orthoHeight = orthoHeight;
        c.physicsEnabled = physicsEnabled;
        c.isStatic = isStatic;
        c.isTrigger = isTrigger;
        c.colliderType = colliderType;
        c.mass = mass;
        c.bounce = bounce;
        c.friction = friction;
        c.gravityScale = gravityScale;
        c.linearDamping = linearDamping;
        c.velocityX = velocityX; c.velocityY = velocityY; c.velocityZ = velocityZ;
        c.collisionLayer = collisionLayer;
        c.collisionMask = collisionMask;
        c.tag = tag;
        c.maxHealth = maxHealth;
        c.health = health;
        c.parentId = parentId;
        c.customProps = customProps;
        c.locked = locked;
        return c;
    }

    public static String displayNameForMesh(String meshType) {
        if (meshType == null) return "Cube";
        return switch (meshType) {
            case MESH_CUBE -> "Cube";
            case MESH_BOX -> "Box";
            case MESH_SPHERE -> "Sphere";
            case MESH_PLANE -> "Ground Plane";
            case MESH_CYLINDER -> "Cylinder";
            case MESH_CONE -> "Cone";
            case MESH_TORUS -> "Torus";
            case MESH_QUAD -> "Quad";
            case MESH_MODEL -> "Imported Model";
            case MESH_NONE -> "None";
            default -> meshType;
        };
    }

    /** Sensible starting values for each palette entry. */
    public static Game3DObjectBean createDefault(String meshType, int index) {
        Game3DObjectBean obj = new Game3DObjectBean();
        obj.meshType = meshType == null ? MESH_CUBE : meshType;
        obj.name = displayNameForMesh(obj.meshType).replace(" ", "") + index;
        switch (obj.meshType) {
            case MESH_SPHERE -> {
                obj.radius = 0.5f;
                obj.color = 0xFF4FC3F7;
                obj.colliderType = COLLIDER_SPHERE;
            }
            case MESH_PLANE -> {
                obj.sizeX = 20f;
                obj.sizeZ = 20f;
                obj.segments = 10;
                obj.rings = 10;
                obj.color = 0xFF607D8B;
                obj.y = 0f;
                obj.isStatic = true;
                obj.physicsEnabled = true;
            }
            case MESH_CYLINDER -> {
                obj.radius = 0.5f;
                obj.radiusTop = 0.5f;
                obj.height = 2f;
                obj.color = 0xFFFFB74D;
            }
            case MESH_CONE -> {
                obj.radius = 0.6f;
                obj.radiusTop = 0f;
                obj.height = 1.6f;
                obj.color = 0xFFE57373;
            }
            case MESH_TORUS -> {
                obj.sizeX = 1f;
                obj.radius = 0.3f;
                obj.segments = 48;
                obj.rings = 24;
                obj.color = 0xFFBA68C8;
            }
            case MESH_QUAD -> {
                obj.sizeX = 1f;
                obj.sizeY = 1f;
                obj.color = 0xFFFFFFFF;
                obj.doubleSided = true;
                obj.colliderType = COLLIDER_NONE;
            }
            case MESH_MODEL -> {
                obj.color = 0xFFE0E0E0;
                obj.colliderType = COLLIDER_BOX;
            }
            case MESH_NONE -> {
                obj.kind = KIND_EMPTY;
                obj.colliderType = COLLIDER_NONE;
            }
            default -> {
                obj.sizeX = obj.sizeY = obj.sizeZ = 1f;
                obj.color = 0xFF81C784;
            }
        }
        obj.ensureInitialized();
        return obj;
    }

    public static Game3DObjectBean createLight(int lightType, int index) {
        Game3DObjectBean obj = new Game3DObjectBean();
        obj.kind = KIND_LIGHT;
        obj.meshType = MESH_NONE;
        obj.lightType = lightType;
        obj.name = (lightType == 0 ? "SunLight" : "PointLight") + index;
        obj.color = 0xFFFFF4E5;
        obj.y = 6f;
        obj.rotationX = -50f;
        obj.rotationY = -30f;
        obj.colliderType = COLLIDER_NONE;
        obj.ensureInitialized();
        return obj;
    }

    public static Game3DObjectBean createCamera(int index) {
        Game3DObjectBean obj = new Game3DObjectBean();
        obj.kind = KIND_CAMERA;
        obj.meshType = MESH_NONE;
        obj.name = "Camera" + index;
        obj.x = 0f;
        obj.y = 3f;
        obj.z = 8f;
        obj.rotationX = -15f;
        obj.colliderType = COLLIDER_NONE;
        obj.ensureInitialized();
        return obj;
    }
}
