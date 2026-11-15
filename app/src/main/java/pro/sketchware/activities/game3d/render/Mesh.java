package pro.sketchware.activities.game3d.render;

import pro.sketchware.activities.game3d.math.Vec3;

/**
 * Indexed triangle geometry: interleaved-free arrays of positions, normals and
 * texture coordinates plus a triangle index list.
 *
 * <p>Held as plain arrays rather than GL buffers so geometry can be built,
 * inspected and tested without a GL context — the renderer uploads them once
 * and keeps the handles.</p>
 */
public final class Mesh {

    /** xyz per vertex. */
    public final float[] positions;
    /** xyz per vertex, unit length. */
    public final float[] normals;
    /** uv per vertex. */
    public final float[] uvs;
    /** Three indices per triangle. */
    public final int[] indices;

    private final Vec3 boundsMin = new Vec3();
    private final Vec3 boundsMax = new Vec3();
    private boolean boundsValid;

    public Mesh(float[] positions, float[] normals, float[] uvs, int[] indices) {
        if (positions == null || indices == null) {
            throw new IllegalArgumentException("a mesh needs positions and indices");
        }
        if (positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions must be xyz triples, got " + positions.length);
        }
        if (indices.length % 3 != 0) {
            throw new IllegalArgumentException("indices must be triangles, got " + indices.length);
        }
        int vertexCount = positions.length / 3;
        if (normals != null && normals.length != positions.length) {
            throw new IllegalArgumentException("normals must match the vertex count");
        }
        if (uvs != null && uvs.length != vertexCount * 2) {
            throw new IllegalArgumentException("uvs must be one pair per vertex");
        }
        for (int index : indices) {
            if (index < 0 || index >= vertexCount) {
                throw new IllegalArgumentException(
                        "index " + index + " is outside the " + vertexCount + " vertices");
            }
        }
        this.positions = positions;
        this.normals = normals != null ? normals : new float[positions.length];
        this.uvs = uvs != null ? uvs : new float[vertexCount * 2];
        this.indices = indices;
        if (normals == null) {
            recalculateNormals();
        }
    }

    public int vertexCount() {
        return positions.length / 3;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    /**
     * True when every index fits in an unsigned short, which lets the renderer
     * draw with {@code GL_UNSIGNED_SHORT} and stay OpenGL ES 2.0 compatible.
     */
    public boolean fitsShortIndices() {
        return vertexCount() <= 65536;
    }

    /** Smooth normals, area-weighted by the triangles sharing each vertex. */
    public Mesh recalculateNormals() {
        java.util.Arrays.fill(normals, 0f);
        for (int t = 0; t < indices.length; t += 3) {
            int ia = indices[t] * 3;
            int ib = indices[t + 1] * 3;
            int ic = indices[t + 2] * 3;

            float e1x = positions[ib] - positions[ia];
            float e1y = positions[ib + 1] - positions[ia + 1];
            float e1z = positions[ib + 2] - positions[ia + 2];
            float e2x = positions[ic] - positions[ia];
            float e2y = positions[ic + 1] - positions[ia + 1];
            float e2z = positions[ic + 2] - positions[ia + 2];

            // Unnormalised cross product: its length is twice the triangle
            // area, which is exactly the weight a smooth normal wants.
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;

            normals[ia] += nx; normals[ia + 1] += ny; normals[ia + 2] += nz;
            normals[ib] += nx; normals[ib + 1] += ny; normals[ib + 2] += nz;
            normals[ic] += nx; normals[ic + 1] += ny; normals[ic + 2] += nz;
        }
        for (int i = 0; i < normals.length; i += 3) {
            float x = normals[i], y = normals[i + 1], z = normals[i + 2];
            float lenSq = x * x + y * y + z * z;
            if (lenSq > 1e-12f) {
                float inv = 1f / (float) Math.sqrt(lenSq);
                normals[i] = x * inv;
                normals[i + 1] = y * inv;
                normals[i + 2] = z * inv;
            } else {
                // A vertex no triangle references, or one used only by
                // degenerate triangles. Point it away from the origin, which
                // is right for the closed shapes the factory builds: a fixed
                // "up" would face straight into the surface at the bottom of
                // a sphere and render it black.
                float px = positions[i], py = positions[i + 1], pz = positions[i + 2];
                float plSq = px * px + py * py + pz * pz;
                if (plSq > 1e-12f) {
                    float inv = 1f / (float) Math.sqrt(plSq);
                    normals[i] = px * inv;
                    normals[i + 1] = py * inv;
                    normals[i + 2] = pz * inv;
                } else {
                    normals[i] = 0f;
                    normals[i + 1] = 1f;
                    normals[i + 2] = 0f;
                }
            }
        }
        return this;
    }

    private void computeBounds() {
        if (positions.length == 0) {
            boundsMin.set(0f, 0f, 0f);
            boundsMax.set(0f, 0f, 0f);
            boundsValid = true;
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < positions.length; i += 3) {
            minX = Math.min(minX, positions[i]);
            minY = Math.min(minY, positions[i + 1]);
            minZ = Math.min(minZ, positions[i + 2]);
            maxX = Math.max(maxX, positions[i]);
            maxY = Math.max(maxY, positions[i + 1]);
            maxZ = Math.max(maxZ, positions[i + 2]);
        }
        boundsMin.set(minX, minY, minZ);
        boundsMax.set(maxX, maxY, maxZ);
        boundsValid = true;
    }

    public Vec3 boundsMin(Vec3 dst) {
        if (!boundsValid) computeBounds();
        return dst.set(boundsMin);
    }

    public Vec3 boundsMax(Vec3 dst) {
        if (!boundsValid) computeBounds();
        return dst.set(boundsMax);
    }

    public Vec3 boundsCenter(Vec3 dst) {
        if (!boundsValid) computeBounds();
        return dst.set(
                (boundsMin.x + boundsMax.x) * 0.5f,
                (boundsMin.y + boundsMax.y) * 0.5f,
                (boundsMin.z + boundsMax.z) * 0.5f);
    }

    /** Radius of the sphere around the bounds centre that encloses the mesh. */
    public float boundingRadius() {
        if (!boundsValid) computeBounds();
        float dx = (boundsMax.x - boundsMin.x) * 0.5f;
        float dy = (boundsMax.y - boundsMin.y) * 0.5f;
        float dz = (boundsMax.z - boundsMin.z) * 0.5f;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Scales and shifts the mesh so it fits a unit cube centred on the origin. */
    public Mesh normalizeToUnitCube() {
        computeBounds();
        float sx = boundsMax.x - boundsMin.x;
        float sy = boundsMax.y - boundsMin.y;
        float sz = boundsMax.z - boundsMin.z;
        float largest = Math.max(sx, Math.max(sy, sz));
        if (largest < 1e-8f) {
            return this;
        }
        float cx = (boundsMin.x + boundsMax.x) * 0.5f;
        float cy = (boundsMin.y + boundsMax.y) * 0.5f;
        float cz = (boundsMin.z + boundsMax.z) * 0.5f;
        float scale = 1f / largest;
        for (int i = 0; i < positions.length; i += 3) {
            positions[i] = (positions[i] - cx) * scale;
            positions[i + 1] = (positions[i + 1] - cy) * scale;
            positions[i + 2] = (positions[i + 2] - cz) * scale;
        }
        boundsValid = false;
        return this;
    }
}
