package pro.sketchware.activities.game3d.math;

import java.util.Arrays;

/**
 * A 4x4 matrix in the column-major layout OpenGL expects, so {@link #m} can be
 * handed to {@code glUniformMatrix4fv} with no repacking.
 *
 * <p>Element (row r, column c) lives at {@code m[c * 4 + r]}. Transforms are
 * applied the usual way round: {@code M * v}, with translation in the last
 * column.</p>
 */
public final class Mat4 {

    /** 16 floats, column-major. */
    public final float[] m = new float[16];

    /**
     * Per-instance workspace for {@link #invertInto}. Deliberately not static:
     * a shared buffer would corrupt results the moment two threads inverted a
     * matrix at once, and the editor's viewport and the game loop are separate
     * threads.
     */
    private final float[] scratch = new float[16];

    public Mat4() {
        setIdentity();
    }

    public Mat4(Mat4 other) {
        set(other);
    }

    public Mat4 set(Mat4 other) {
        System.arraycopy(other.m, 0, m, 0, 16);
        return this;
    }

    public Mat4 setIdentity() {
        Arrays.fill(m, 0f);
        m[0] = m[5] = m[10] = m[15] = 1f;
        return this;
    }

    public float get(int row, int col) {
        return m[col * 4 + row];
    }

    public Mat4 setValue(int row, int col, float value) {
        m[col * 4 + row] = value;
        return this;
    }

    /** {@code dst = a * b}. Safe when {@code dst} aliases {@code a} or {@code b}. */
    public static Mat4 multiply(Mat4 a, Mat4 b, Mat4 dst) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            int cb = c * 4;
            for (int r = 0; r < 4; r++) {
                out[cb + r] = a.m[r] * b.m[cb]
                        + a.m[4 + r] * b.m[cb + 1]
                        + a.m[8 + r] * b.m[cb + 2]
                        + a.m[12 + r] * b.m[cb + 3];
            }
        }
        System.arraycopy(out, 0, dst.m, 0, 16);
        return dst;
    }

    /** {@code this = this * other}. */
    public Mat4 multiply(Mat4 other) {
        return multiply(this, other, this);
    }

    /** {@code this = translation * this}? No: post-multiplies, like OpenGL. */
    public Mat4 translate(float tx, float ty, float tz) {
        m[12] += m[0] * tx + m[4] * ty + m[8] * tz;
        m[13] += m[1] * tx + m[5] * ty + m[9] * tz;
        m[14] += m[2] * tx + m[6] * ty + m[10] * tz;
        m[15] += m[3] * tx + m[7] * ty + m[11] * tz;
        return this;
    }

    public Mat4 scale(float sx, float sy, float sz) {
        for (int i = 0; i < 4; i++) {
            m[i] *= sx;
            m[4 + i] *= sy;
            m[8 + i] *= sz;
        }
        return this;
    }

    public Mat4 setTranslation(float tx, float ty, float tz) {
        setIdentity();
        m[12] = tx;
        m[13] = ty;
        m[14] = tz;
        return this;
    }

    public Mat4 setScale(float sx, float sy, float sz) {
        setIdentity();
        m[0] = sx;
        m[5] = sy;
        m[10] = sz;
        return this;
    }

    /** Rotation about an arbitrary axis, in degrees. */
    public Mat4 setRotation(float degrees, float ax, float ay, float az) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-8f) {
            return setIdentity();
        }
        ax /= len;
        ay /= len;
        az /= len;
        double rad = Math.toRadians(degrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float ic = 1f - c;

        setIdentity();
        m[0] = c + ax * ax * ic;
        m[1] = ay * ax * ic + az * s;
        m[2] = az * ax * ic - ay * s;

        m[4] = ax * ay * ic - az * s;
        m[5] = c + ay * ay * ic;
        m[6] = az * ay * ic + ax * s;

        m[8] = ax * az * ic + ay * s;
        m[9] = ay * az * ic - ax * s;
        m[10] = c + az * az * ic;
        return this;
    }

    /**
     * Euler rotation in degrees, applied Z then X then Y — the order most
     * editors present as "yaw, pitch, roll" without gimbal surprises for a
     * camera that mostly turns around Y.
     */
    public Mat4 setRotationEuler(float pitchX, float yawY, float rollZ) {
        double rx = Math.toRadians(pitchX);
        double ry = Math.toRadians(yawY);
        double rz = Math.toRadians(rollZ);
        float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
        float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
        float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);

        setIdentity();
        m[0] = cy * cz + sy * sx * sz;
        m[1] = cx * sz;
        m[2] = -sy * cz + cy * sx * sz;

        m[4] = -cy * sz + sy * sx * cz;
        m[5] = cx * cz;
        m[6] = sy * sz + cy * sx * cz;

        m[8] = sy * cx;
        m[9] = -sx;
        m[10] = cy * cx;
        return this;
    }

    /** Right-handed perspective projection; {@code fovYDeg} is the vertical field of view. */
    public Mat4 setPerspective(float fovYDeg, float aspect, float near, float far) {
        Arrays.fill(m, 0f);
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) * 0.5));
        float safeAspect = Math.abs(aspect) < 1e-6f ? 1f : aspect;
        float range = near - far;
        m[0] = f / safeAspect;
        m[5] = f;
        m[10] = (far + near) / range;
        m[11] = -1f;
        m[14] = 2f * far * near / range;
        return this;
    }

    public Mat4 setOrtho(float left, float right, float bottom, float top, float near, float far) {
        Arrays.fill(m, 0f);
        m[0] = 2f / (right - left);
        m[5] = 2f / (top - bottom);
        m[10] = -2f / (far - near);
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[14] = -(far + near) / (far - near);
        m[15] = 1f;
        return this;
    }

    /** View matrix for a camera at {@code eye} looking at {@code target}. */
    public Mat4 setLookAt(Vec3 eye, Vec3 target, Vec3 up) {
        Vec3 f = new Vec3(target).sub(eye).normalize();
        Vec3 s = Vec3.cross(f, up, new Vec3()).normalize();
        if (s.lengthSquared() < 1e-12f) {
            // eye, target and up are collinear: pick any perpendicular so the
            // camera still produces a usable view instead of NaNs.
            s.set(Math.abs(f.y) > 0.99f ? 1f : 0f, 0f, Math.abs(f.y) > 0.99f ? 0f : 1f);
            Vec3.cross(f, s, s).normalize();
        }
        Vec3 u = Vec3.cross(s, f, new Vec3());

        setIdentity();
        m[0] = s.x;  m[4] = s.y;  m[8] = s.z;
        m[1] = u.x;  m[5] = u.y;  m[9] = u.z;
        m[2] = -f.x; m[6] = -f.y; m[10] = -f.z;
        m[12] = -s.dot(eye);
        m[13] = -u.dot(eye);
        m[14] = f.dot(eye);
        return this;
    }

    /** Transforms a point (w = 1), writing into {@code dst}. */
    public Vec3 transformPoint(Vec3 p, Vec3 dst) {
        float nx = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12];
        float ny = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13];
        float nz = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14];
        float nw = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15];
        if (Math.abs(nw) > 1e-8f && Math.abs(nw - 1f) > 1e-8f) {
            nx /= nw;
            ny /= nw;
            nz /= nw;
        }
        return dst.set(nx, ny, nz);
    }

    /** Transforms a direction (w = 0): translation is ignored. */
    public Vec3 transformDirection(Vec3 d, Vec3 dst) {
        return dst.set(
                m[0] * d.x + m[4] * d.y + m[8] * d.z,
                m[1] * d.x + m[5] * d.y + m[9] * d.z,
                m[2] * d.x + m[6] * d.y + m[10] * d.z);
    }

    public Mat4 transpose() {
        for (int c = 0; c < 4; c++) {
            for (int r = c + 1; r < 4; r++) {
                int a = c * 4 + r;
                int b = r * 4 + c;
                float t = m[a];
                m[a] = m[b];
                m[b] = t;
            }
        }
        return this;
    }

    /**
     * Writes the inverse into {@code dst}. Returns false and leaves {@code dst}
     * untouched for a singular matrix, so a degenerate object transform cannot
     * silently fill the scene with NaN normals.
     */
    public boolean invertInto(Mat4 dst) {
        float[] s = m;
        float[] o = scratch;

        o[0] = s[5] * s[10] * s[15] - s[5] * s[11] * s[14] - s[9] * s[6] * s[15]
                + s[9] * s[7] * s[14] + s[13] * s[6] * s[11] - s[13] * s[7] * s[10];
        o[4] = -s[4] * s[10] * s[15] + s[4] * s[11] * s[14] + s[8] * s[6] * s[15]
                - s[8] * s[7] * s[14] - s[12] * s[6] * s[11] + s[12] * s[7] * s[10];
        o[8] = s[4] * s[9] * s[15] - s[4] * s[11] * s[13] - s[8] * s[5] * s[15]
                + s[8] * s[7] * s[13] + s[12] * s[5] * s[11] - s[12] * s[7] * s[9];
        o[12] = -s[4] * s[9] * s[14] + s[4] * s[10] * s[13] + s[8] * s[5] * s[14]
                - s[8] * s[6] * s[13] - s[12] * s[5] * s[10] + s[12] * s[6] * s[9];

        o[1] = -s[1] * s[10] * s[15] + s[1] * s[11] * s[14] + s[9] * s[2] * s[15]
                - s[9] * s[3] * s[14] - s[13] * s[2] * s[11] + s[13] * s[3] * s[10];
        o[5] = s[0] * s[10] * s[15] - s[0] * s[11] * s[14] - s[8] * s[2] * s[15]
                + s[8] * s[3] * s[14] + s[12] * s[2] * s[11] - s[12] * s[3] * s[10];
        o[9] = -s[0] * s[9] * s[15] + s[0] * s[11] * s[13] + s[8] * s[1] * s[15]
                - s[8] * s[3] * s[13] - s[12] * s[1] * s[11] + s[12] * s[3] * s[9];
        o[13] = s[0] * s[9] * s[14] - s[0] * s[10] * s[13] - s[8] * s[1] * s[14]
                + s[8] * s[2] * s[13] + s[12] * s[1] * s[10] - s[12] * s[2] * s[9];

        o[2] = s[1] * s[6] * s[15] - s[1] * s[7] * s[14] - s[5] * s[2] * s[15]
                + s[5] * s[3] * s[14] + s[13] * s[2] * s[7] - s[13] * s[3] * s[6];
        o[6] = -s[0] * s[6] * s[15] + s[0] * s[7] * s[14] + s[4] * s[2] * s[15]
                - s[4] * s[3] * s[14] - s[12] * s[2] * s[7] + s[12] * s[3] * s[6];
        o[10] = s[0] * s[5] * s[15] - s[0] * s[7] * s[13] - s[4] * s[1] * s[15]
                + s[4] * s[3] * s[13] + s[12] * s[1] * s[7] - s[12] * s[3] * s[5];
        o[14] = -s[0] * s[5] * s[14] + s[0] * s[6] * s[13] + s[4] * s[1] * s[14]
                - s[4] * s[2] * s[13] - s[12] * s[1] * s[6] + s[12] * s[2] * s[5];

        o[3] = -s[1] * s[6] * s[11] + s[1] * s[7] * s[10] + s[5] * s[2] * s[11]
                - s[5] * s[3] * s[10] - s[9] * s[2] * s[7] + s[9] * s[3] * s[6];
        o[7] = s[0] * s[6] * s[11] - s[0] * s[7] * s[10] - s[4] * s[2] * s[11]
                + s[4] * s[3] * s[10] + s[8] * s[2] * s[7] - s[8] * s[3] * s[6];
        o[11] = -s[0] * s[5] * s[11] + s[0] * s[7] * s[9] + s[4] * s[1] * s[11]
                - s[4] * s[3] * s[9] - s[8] * s[1] * s[7] + s[8] * s[3] * s[5];
        o[15] = s[0] * s[5] * s[10] - s[0] * s[6] * s[9] - s[4] * s[1] * s[10]
                + s[4] * s[2] * s[9] + s[8] * s[1] * s[6] - s[8] * s[2] * s[5];

        float det = s[0] * o[0] + s[1] * o[4] + s[2] * o[8] + s[3] * o[12];
        if (Math.abs(det) < 1e-12f) {
            return false;
        }
        float invDet = 1f / det;
        for (int i = 0; i < 16; i++) {
            dst.m[i] = o[i] * invDet;
        }
        return true;
    }

    /**
     * The matrix normals must be transformed by: the inverse transpose of the
     * upper 3x3. Needed whenever an object is scaled non-uniformly, where the
     * model matrix alone would bend normals away from the surface.
     */
    public boolean normalMatrixInto(Mat4 dst) {
        if (!invertInto(dst)) {
            return false;
        }
        dst.transpose();
        dst.m[3] = dst.m[7] = dst.m[11] = 0f;
        dst.m[12] = dst.m[13] = dst.m[14] = 0f;
        dst.m[15] = 1f;
        return true;
    }

    public boolean isFinite() {
        for (float v : m) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Mat4[\n");
        for (int r = 0; r < 4; r++) {
            sb.append("  ");
            for (int c = 0; c < 4; c++) {
                sb.append(String.format(java.util.Locale.US, "%8.3f ", get(r, c)));
            }
            sb.append('\n');
        }
        return sb.append(']').toString();
    }
}
