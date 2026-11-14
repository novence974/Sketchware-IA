package pro.sketchware.activities.game3d.render;

import android.opengl.GLES20;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.activities.game3d.math.Mat4;
import pro.sketchware.activities.game3d.math.Vec3;
import pro.sketchware.activities.game3d.model.Game3DObjectBean;
import pro.sketchware.activities.game3d.model.Game3DScene;

/**
 * Draws a {@link Game3DScene}.
 *
 * <p>Owns the GL objects: the shader programs, the uploaded meshes and the
 * sky quad. Everything it allocates lives until {@link #releaseGlResources()},
 * and every GL call must happen on the GL thread.</p>
 */
public final class Renderer3D {

    private static final String TAG = "Game3D.Renderer";

    private Shader unlit;
    private Shader lambert;
    private Shader phong;
    private Shader sky;

    private int skyQuadBuffer = 0;
    private boolean glReady;

    /** Uploaded geometry, keyed by the parameters that define its shape. */
    private final Map<String, GpuMesh> meshCache = new HashMap<>();
    /** Imported .obj geometry, supplied by the host before a frame is drawn. */
    private final Map<String, Mesh> importedModels = new HashMap<>();

    private final Mat4 model = new Mat4();
    private final Mat4 rotation = new Mat4();
    private final Mat4 scratch = new Mat4();
    private final Mat4 normalMatrix = new Mat4();
    private final Mat4 mvp = new Mat4();
    private final Vec3 cameraPos = new Vec3();

    private final float[] pointPositions = new float[ShaderLibrary.MAX_POINT_LIGHTS * 3];
    private final float[] pointColors = new float[ShaderLibrary.MAX_POINT_LIGHTS * 3];
    private final float[] pointRanges = new float[ShaderLibrary.MAX_POINT_LIGHTS];

    private final List<Game3DObjectBean> opaque = new ArrayList<>();
    private final List<Game3DObjectBean> transparent = new ArrayList<>();

    private int drawnObjects;
    private int drawnTriangles;

    /** Compiles the programs. Must run on the GL thread, after the context exists. */
    public boolean initGl() {
        releaseGlResources();
        unlit = Shader.compile(ShaderLibrary.VERTEX, ShaderLibrary.FRAGMENT_UNLIT);
        lambert = Shader.compile(ShaderLibrary.VERTEX, ShaderLibrary.FRAGMENT_LAMBERT);
        phong = Shader.compile(ShaderLibrary.VERTEX, ShaderLibrary.FRAGMENT_PHONG);
        sky = Shader.compile(ShaderLibrary.SKY_VERTEX, ShaderLibrary.SKY_FRAGMENT);
        if (unlit == null || lambert == null || phong == null || sky == null) {
            Log.e(TAG, "shader compilation failed; nothing will be drawn");
            glReady = false;
            return false;
        }
        createSkyQuad();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);
        glReady = true;
        return true;
    }

    private void createSkyQuad() {
        float[] quad = {-1f, -1f, 3f, -1f, -1f, 3f};   // one oversized triangle
        int[] handle = new int[1];
        GLES20.glGenBuffers(1, handle, 0);
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(quad.length * 4)
                .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(quad).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, handle[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quad.length * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        skyQuadBuffer = handle[0];
    }

    /** Registers geometry for {@code MESH_MODEL} objects that name {@code asset}. */
    public void putImportedModel(String asset, Mesh mesh) {
        if (asset != null && !asset.isEmpty() && mesh != null) {
            importedModels.put(asset, mesh);
            // Drop any cached upload so the new geometry is picked up.
            GpuMesh stale = meshCache.remove("model:" + asset);
            if (stale != null) {
                stale.release();
            }
        }
    }

    public void setViewportSize(int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
    }

    public int lastDrawnObjects() {
        return drawnObjects;
    }

    public int lastDrawnTriangles() {
        return drawnTriangles;
    }

    /** Draws one frame. */
    public void drawScene(Game3DScene scene, Camera3D camera, boolean cullBackFaces) {
        drawnObjects = 0;
        drawnTriangles = 0;
        if (!glReady || scene == null || camera == null) {
            GLES20.glClearColor(0.1f, 0.1f, 0.12f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            return;
        }

        GLES20.glClearColor(
                red(scene.skyColorBottom), green(scene.skyColorBottom), blue(scene.skyColorBottom), 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (Game3DScene.SKY_GRADIENT.equals(scene.skyMode)) {
            drawSky(scene);
        }

        if (cullBackFaces) {
            GLES20.glEnable(GLES20.GL_CULL_FACE);
        } else {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
        }

        camera.position(cameraPos);
        collectLights(scene);

        // Transparent surfaces are drawn after every opaque one, or they
        // would blend against an unfinished frame and show through walls.
        opaque.clear();
        transparent.clear();
        for (Game3DObjectBean obj : scene.objects) {
            if (obj == null || !obj.visible || !Game3DObjectBean.KIND_MESH.equals(obj.kind)) {
                continue;
            }
            if (Game3DObjectBean.MESH_NONE.equals(obj.meshType)) {
                continue;
            }
            if (obj.opacity < 0.999f) {
                transparent.add(obj);
            } else {
                opaque.add(obj);
            }
        }

        for (Game3DObjectBean obj : opaque) {
            drawObject(obj, scene, camera);
        }

        if (!transparent.isEmpty()) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            // Still depth-tested, but not written: transparent surfaces must
            // not hide each other.
            GLES20.glDepthMask(false);
            for (Game3DObjectBean obj : transparent) {
                drawObject(obj, scene, camera);
            }
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }

    private void drawSky(Game3DScene scene) {
        GLES20.glDepthMask(false);
        sky.use();
        sky.setVec3("uTopColor", red(scene.skyColorTop), green(scene.skyColorTop), blue(scene.skyColorTop));
        sky.setVec3("uBottomColor", red(scene.skyColorBottom), green(scene.skyColorBottom), blue(scene.skyColorBottom));
        int loc = sky.attribute("aPosition");
        if (loc >= 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyQuadBuffer);
            GLES20.glEnableVertexAttribArray(loc);
            GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 0, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
            GLES20.glDisableVertexAttribArray(loc);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
        GLES20.glDepthMask(true);
    }

    private int pointCount;
    private float dirX = -0.4f, dirY = -1f, dirZ = -0.3f;
    private float dirR = 1f, dirG = 1f, dirB = 1f;

    private void collectLights(Game3DScene scene) {
        pointCount = 0;
        dirX = 0f; dirY = -1f; dirZ = 0f;
        dirR = dirG = dirB = 0f;

        for (Game3DObjectBean obj : scene.objects) {
            if (obj == null || !obj.visible || !Game3DObjectBean.KIND_LIGHT.equals(obj.kind)) {
                continue;
            }
            float r = red(obj.color) * obj.lightIntensity;
            float g = green(obj.color) * obj.lightIntensity;
            float b = blue(obj.color) * obj.lightIntensity;
            if (obj.lightType == 0) {
                // The light travels along the object's -Y axis after rotation.
                rotation.setRotationEuler(obj.rotationX, obj.rotationY, obj.rotationZ);
                Vec3 dir = rotation.transformDirection(new Vec3(0f, -1f, 0f), new Vec3()).normalize();
                dirX = dir.x; dirY = dir.y; dirZ = dir.z;
                dirR = r; dirG = g; dirB = b;
            } else if (pointCount < ShaderLibrary.MAX_POINT_LIGHTS) {
                int o = pointCount * 3;
                pointPositions[o] = obj.x;
                pointPositions[o + 1] = obj.y;
                pointPositions[o + 2] = obj.z;
                pointColors[o] = r;
                pointColors[o + 1] = g;
                pointColors[o + 2] = b;
                pointRanges[pointCount] = obj.lightRange;
                pointCount++;
            }
        }
    }

    private void drawObject(Game3DObjectBean obj, Game3DScene scene, Camera3D camera) {
        GpuMesh gpu = meshFor(obj);
        if (gpu == null) {
            return;
        }
        Shader shader = switch (obj.shading) {
            case Material.SHADING_UNLIT -> unlit;
            case Material.SHADING_PHONG -> phong;
            default -> lambert;
        };
        shader.use();

        buildModelMatrix(obj);
        Mat4.multiply(camera.viewProjection(), model, mvp);
        shader.setMatrix(ShaderLibrary.U_MVP, mvp.m);
        shader.setMatrix(ShaderLibrary.U_MODEL, model.m);
        if (!model.normalMatrixInto(normalMatrix)) {
            // A zero scale makes the model matrix singular; fall back to the
            // model matrix so the object still draws, just lit flatly.
            normalMatrix.set(model);
        }
        shader.setMatrix(ShaderLibrary.U_NORMAL_MATRIX, normalMatrix.m);

        shader.setVec3(ShaderLibrary.U_BASE_COLOR, red(obj.color), green(obj.color), blue(obj.color));
        shader.setFloat(ShaderLibrary.U_OPACITY, Math.max(0f, Math.min(1f, obj.opacity)));
        shader.setFloat(ShaderLibrary.U_EMISSIVE, obj.emissive);
        shader.setFloat(ShaderLibrary.U_HAS_TEXTURE, 0f);
        shader.setVec3(ShaderLibrary.U_CAMERA_POS, cameraPos.x, cameraPos.y, cameraPos.z);
        shader.setVec3(ShaderLibrary.U_FOG_COLOR, red(scene.fogColor), green(scene.fogColor), blue(scene.fogColor));
        shader.setVec2(ShaderLibrary.U_FOG_RANGE, scene.fogStart, scene.fogEnd);
        shader.setFloat(ShaderLibrary.U_FOG_ENABLED, scene.fogEnabled ? 1f : 0f);

        if (ShaderLibrary.usesLighting(obj.shading)) {
            float ai = scene.ambientIntensity;
            shader.setVec3(ShaderLibrary.U_AMBIENT,
                    red(scene.ambientColor) * ai, green(scene.ambientColor) * ai, blue(scene.ambientColor) * ai);
            shader.setVec3(ShaderLibrary.U_DIR_LIGHT_DIR, dirX, dirY, dirZ);
            shader.setVec3(ShaderLibrary.U_DIR_LIGHT_COLOR, dirR, dirG, dirB);
            shader.setInt(ShaderLibrary.U_POINT_COUNT, pointCount);
            if (pointCount > 0) {
                shader.setVec3Array(ShaderLibrary.U_POINT_POS, pointPositions, pointCount);
                shader.setVec3Array(ShaderLibrary.U_POINT_COLOR, pointColors, pointCount);
                shader.setFloatArray(ShaderLibrary.U_POINT_RANGE, pointRanges, pointCount);
            }
            if (obj.shading == Material.SHADING_PHONG) {
                shader.setFloat(ShaderLibrary.U_SPECULAR, obj.specularStrength);
                shader.setFloat(ShaderLibrary.U_SHININESS, Math.max(1f, obj.shininess));
            }
        }

        boolean wasCulling = !obj.doubleSided;
        if (obj.doubleSided) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
        }

        int pLoc = shader.attribute(ShaderLibrary.A_POSITION);
        int nLoc = shader.attribute(ShaderLibrary.A_NORMAL);
        int uLoc = shader.attribute(ShaderLibrary.A_UV);
        gpu.bind(pLoc, nLoc, uLoc);
        gpu.draw();
        gpu.unbind(pLoc, nLoc, uLoc);

        if (!wasCulling) {
            GLES20.glEnable(GLES20.GL_CULL_FACE);
        }

        drawnObjects++;
        drawnTriangles += gpu.indexCount() / 3;
    }

    private void buildModelMatrix(Game3DObjectBean obj) {
        model.setTranslation(obj.x, obj.y, obj.z);
        rotation.setRotationEuler(obj.rotationX, obj.rotationY, obj.rotationZ);
        Mat4.multiply(model, rotation, model);
        scratch.setScale(obj.scaleX, obj.scaleY, obj.scaleZ);
        Mat4.multiply(model, scratch, model);
    }

    /** Uploads the object's geometry on first use and caches it by shape. */
    private GpuMesh meshFor(Game3DObjectBean obj) {
        String key = meshKey(obj);
        GpuMesh cached = meshCache.get(key);
        if (cached != null) {
            return cached;
        }
        Mesh mesh = buildMesh(obj);
        if (mesh == null) {
            return null;
        }
        GpuMesh uploaded = GpuMesh.upload(mesh);
        meshCache.put(key, uploaded);
        return uploaded;
    }

    private Mesh buildMesh(Game3DObjectBean obj) {
        return switch (obj.meshType) {
            case Game3DObjectBean.MESH_SPHERE -> MeshFactory.sphere(obj.radius, obj.segments, obj.rings);
            case Game3DObjectBean.MESH_PLANE -> MeshFactory.plane(obj.sizeX, obj.sizeZ, obj.segments, obj.rings);
            case Game3DObjectBean.MESH_CYLINDER -> MeshFactory.cylinder(obj.radius, obj.height, obj.segments);
            case Game3DObjectBean.MESH_CONE -> MeshFactory.cone(obj.radius, obj.radiusTop, obj.height, obj.segments);
            case Game3DObjectBean.MESH_TORUS -> MeshFactory.torus(obj.sizeX, obj.radius, obj.segments, obj.rings);
            case Game3DObjectBean.MESH_QUAD -> MeshFactory.quad(obj.sizeX, obj.sizeY);
            case Game3DObjectBean.MESH_MODEL -> importedModels.get(obj.modelAsset);
            case Game3DObjectBean.MESH_NONE -> null;
            default -> MeshFactory.box(obj.sizeX, obj.sizeY, obj.sizeZ);
        };
    }

    /**
     * Objects that share a shape share one upload. The key holds every
     * parameter that changes the geometry and none that do not — colour and
     * transform must not split the cache.
     */
    private static String meshKey(Game3DObjectBean obj) {
        return switch (obj.meshType) {
            case Game3DObjectBean.MESH_SPHERE ->
                    String.format(Locale.US, "sphere:%.4f:%d:%d", obj.radius, obj.segments, obj.rings);
            case Game3DObjectBean.MESH_PLANE ->
                    String.format(Locale.US, "plane:%.4f:%.4f:%d:%d", obj.sizeX, obj.sizeZ, obj.segments, obj.rings);
            case Game3DObjectBean.MESH_CYLINDER ->
                    String.format(Locale.US, "cyl:%.4f:%.4f:%d", obj.radius, obj.height, obj.segments);
            case Game3DObjectBean.MESH_CONE ->
                    String.format(Locale.US, "cone:%.4f:%.4f:%.4f:%d", obj.radius, obj.radiusTop, obj.height, obj.segments);
            case Game3DObjectBean.MESH_TORUS ->
                    String.format(Locale.US, "torus:%.4f:%.4f:%d:%d", obj.sizeX, obj.radius, obj.segments, obj.rings);
            case Game3DObjectBean.MESH_QUAD ->
                    String.format(Locale.US, "quad:%.4f:%.4f", obj.sizeX, obj.sizeY);
            case Game3DObjectBean.MESH_MODEL -> "model:" + obj.modelAsset;
            case Game3DObjectBean.MESH_NONE -> "none";
            default ->
                    String.format(Locale.US, "box:%.4f:%.4f:%.4f", obj.sizeX, obj.sizeY, obj.sizeZ);
        };
    }

    /** Frees every GL object. Must run on the GL thread. */
    public void releaseGlResources() {
        for (GpuMesh mesh : meshCache.values()) {
            mesh.release();
        }
        meshCache.clear();
        if (unlit != null) unlit.release();
        if (lambert != null) lambert.release();
        if (phong != null) phong.release();
        if (sky != null) sky.release();
        unlit = lambert = phong = sky = null;
        if (skyQuadBuffer != 0) {
            GLES20.glDeleteBuffers(1, new int[]{skyQuadBuffer}, 0);
            skyQuadBuffer = 0;
        }
        glReady = false;
    }

    static float red(int argb) {
        return ((argb >> 16) & 0xFF) / 255f;
    }

    static float green(int argb) {
        return ((argb >> 8) & 0xFF) / 255f;
    }

    static float blue(int argb) {
        return (argb & 0xFF) / 255f;
    }
}
