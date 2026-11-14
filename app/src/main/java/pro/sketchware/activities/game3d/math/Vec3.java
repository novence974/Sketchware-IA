package pro.sketchware.activities.game3d.math;

/**
 * A mutable 3-component vector.
 *
 * <p>Mutable on purpose: the render loop runs per frame on every object, and
 * allocating fresh vectors there is what makes a mobile 3D engine stutter
 * under the garbage collector. Methods that write into {@code this} are named
 * for the operation ({@link #add}); the {@code ...Into} variants write into a
 * caller-supplied destination so nothing is allocated at all.</p>
 */
public final class Vec3 {

    public float x;
    public float y;
    public float z;

    public Vec3() {
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 other) {
        set(other);
    }

    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 set(Vec3 other) {
        return set(other.x, other.y, other.z);
    }

    public Vec3 add(Vec3 other) {
        x += other.x;
        y += other.y;
        z += other.z;
        return this;
    }

    public Vec3 sub(Vec3 other) {
        x -= other.x;
        y -= other.y;
        z -= other.z;
        return this;
    }

    public Vec3 scale(float s) {
        x *= s;
        y *= s;
        z *= s;
        return this;
    }

    public Vec3 negate() {
        return scale(-1f);
    }

    public float dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /** Right-handed cross product, written into {@code dst}. */
    public static Vec3 cross(Vec3 a, Vec3 b, Vec3 dst) {
        float cx = a.y * b.z - a.z * b.y;
        float cy = a.z * b.x - a.x * b.z;
        float cz = a.x * b.y - a.y * b.x;
        return dst.set(cx, cy, cz);
    }

    public Vec3 cross(Vec3 other) {
        return cross(this, other, this);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public float distanceTo(Vec3 other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float dz = z - other.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Scales to unit length. A zero-length vector is left alone rather than
     * turned into NaN: a degenerate normal should make a triangle look wrong,
     * not poison every matrix it later touches.
     */
    public Vec3 normalize() {
        float lenSq = lengthSquared();
        if (lenSq > 1e-12f) {
            scale(1f / (float) Math.sqrt(lenSq));
        }
        return this;
    }

    /** Linear interpolation: {@code t} 0 gives a, 1 gives b. */
    public static Vec3 lerp(Vec3 a, Vec3 b, float t, Vec3 dst) {
        return dst.set(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }

    public static Vec3 add(Vec3 a, Vec3 b, Vec3 dst) {
        return dst.set(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    public static Vec3 sub(Vec3 a, Vec3 b, Vec3 dst) {
        return dst.set(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    public boolean isFinite() {
        return !(Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)
                || Float.isInfinite(x) || Float.isInfinite(y) || Float.isInfinite(z));
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
