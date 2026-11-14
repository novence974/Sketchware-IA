package pro.sketchware.activities.game3d.render;

import pro.sketchware.activities.game3d.math.Mat4;
import pro.sketchware.activities.game3d.math.Vec3;

/**
 * A perspective or orthographic camera.
 *
 * <p>Holds its own view and projection matrices and rebuilds them only when
 * something changed, so a still camera costs nothing per frame.</p>
 */
public final class Camera3D {

    public static final int PROJECTION_PERSPECTIVE = 0;
    public static final int PROJECTION_ORTHOGRAPHIC = 1;

    private final Vec3 position = new Vec3(0f, 2f, 6f);
    private final Vec3 target = new Vec3(0f, 0f, 0f);
    private final Vec3 up = new Vec3(0f, 1f, 0f);

    private int projectionType = PROJECTION_PERSPECTIVE;
    private float fovYDegrees = 60f;
    private float orthoHeight = 10f;
    private float aspect = 1f;
    private float near = 0.1f;
    private float far = 500f;

    private final Mat4 view = new Mat4();
    private final Mat4 projection = new Mat4();
    private final Mat4 viewProjection = new Mat4();

    private boolean viewDirty = true;
    private boolean projectionDirty = true;

    public Camera3D setPosition(float x, float y, float z) {
        position.set(x, y, z);
        viewDirty = true;
        return this;
    }

    public Camera3D setTarget(float x, float y, float z) {
        target.set(x, y, z);
        viewDirty = true;
        return this;
    }

    public Camera3D setUp(float x, float y, float z) {
        up.set(x, y, z);
        viewDirty = true;
        return this;
    }

    public Vec3 position(Vec3 dst) {
        return dst.set(position);
    }

    public Vec3 target(Vec3 dst) {
        return dst.set(target);
    }

    /**
     * Places the camera on a sphere around {@link #target}: {@code yaw} spins
     * around Y, {@code pitch} rises toward the pole. This is the orbit control
     * the editor viewport drives from touch.
     */
    public Camera3D orbit(float yawDegrees, float pitchDegrees, float distance) {
        // Clamped just short of the poles: exactly at them the up vector and
        // the view direction line up and the view matrix has no unique answer.
        float pitch = Math.max(-89.5f, Math.min(89.5f, pitchDegrees));
        float d = Math.max(0.01f, distance);
        double yaw = Math.toRadians(yawDegrees);
        double p = Math.toRadians(pitch);
        float cosP = (float) Math.cos(p);
        position.set(
                target.x + d * cosP * (float) Math.sin(yaw),
                target.y + d * (float) Math.sin(p),
                target.z + d * cosP * (float) Math.cos(yaw));
        viewDirty = true;
        return this;
    }

    public Camera3D setPerspective(float fovYDegrees, float near, float far) {
        this.projectionType = PROJECTION_PERSPECTIVE;
        this.fovYDegrees = Math.max(1f, Math.min(179f, fovYDegrees));
        setClipPlanes(near, far);
        return this;
    }

    public Camera3D setOrthographic(float height, float near, float far) {
        this.projectionType = PROJECTION_ORTHOGRAPHIC;
        this.orthoHeight = Math.max(0.01f, height);
        setClipPlanes(near, far);
        return this;
    }

    /**
     * Near must stay above zero and below far, or the depth buffer collapses
     * and the scene z-fights or disappears entirely.
     */
    public Camera3D setClipPlanes(float near, float far) {
        this.near = Math.max(1e-3f, near);
        this.far = Math.max(this.near + 1e-3f, far);
        projectionDirty = true;
        return this;
    }

    public Camera3D setViewport(int widthPx, int heightPx) {
        float a = heightPx <= 0 ? 1f : (float) widthPx / (float) heightPx;
        if (a <= 0f || Float.isNaN(a) || Float.isInfinite(a)) {
            a = 1f;
        }
        if (a != aspect) {
            aspect = a;
            projectionDirty = true;
        }
        return this;
    }

    public float aspect() {
        return aspect;
    }

    public float near() {
        return near;
    }

    public float far() {
        return far;
    }

    public int projectionType() {
        return projectionType;
    }

    public Mat4 view() {
        if (viewDirty) {
            view.setLookAt(position, target, up);
            viewDirty = false;
        }
        return view;
    }

    public Mat4 projection() {
        if (projectionDirty) {
            if (projectionType == PROJECTION_ORTHOGRAPHIC) {
                float halfH = orthoHeight * 0.5f;
                float halfW = halfH * aspect;
                projection.setOrtho(-halfW, halfW, -halfH, halfH, near, far);
            } else {
                projection.setPerspective(fovYDegrees, aspect, near, far);
            }
            projectionDirty = false;
        }
        return projection;
    }

    /** {@code projection * view}, the matrix a vertex shader needs. */
    public Mat4 viewProjection() {
        Mat4 v = view();
        Mat4 p = projection();
        return Mat4.multiply(p, v, viewProjection);
    }

    /** Unit vector from the camera toward its target. */
    public Vec3 forward(Vec3 dst) {
        return dst.set(target).sub(position).normalize();
    }

    /**
     * Projects a world point to normalised device coordinates, then to pixels.
     * Returns false when the point is behind the camera, where the divide
     * would mirror it onto the screen as if it were in front.
     */
    public boolean worldToScreen(Vec3 world, int viewportW, int viewportH, Vec3 dstPixels) {
        Mat4 vp = viewProjection();
        float x = world.x, y = world.y, z = world.z;
        float cx = vp.m[0] * x + vp.m[4] * y + vp.m[8] * z + vp.m[12];
        float cy = vp.m[1] * x + vp.m[5] * y + vp.m[9] * z + vp.m[13];
        float cz = vp.m[2] * x + vp.m[6] * y + vp.m[10] * z + vp.m[14];
        float cw = vp.m[3] * x + vp.m[7] * y + vp.m[11] * z + vp.m[15];
        if (cw <= 1e-6f) {
            return false;
        }
        float ndcX = cx / cw;
        float ndcY = cy / cw;
        float ndcZ = cz / cw;
        dstPixels.set(
                (ndcX * 0.5f + 0.5f) * viewportW,
                (1f - (ndcY * 0.5f + 0.5f)) * viewportH,
                ndcZ);
        return true;
    }
}
