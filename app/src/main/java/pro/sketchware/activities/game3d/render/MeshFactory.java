package pro.sketchware.activities.game3d.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the primitive meshes the 3D editor offers.
 *
 * <p>Every primitive is centred on the origin, sized in world units, and wound
 * counter-clockwise seen from outside so back-face culling keeps the inside
 * hidden. Normals and UVs are generated, never left for the renderer to guess.</p>
 */
public final class MeshFactory {

    private MeshFactory() {
    }

    /** Axis-aligned box. Each face gets its own 4 vertices so normals stay flat. */
    public static Mesh box(float width, float height, float depth) {
        float x = Math.max(1e-4f, width) * 0.5f;
        float y = Math.max(1e-4f, height) * 0.5f;
        float z = Math.max(1e-4f, depth) * 0.5f;

        float[] positions = {
                // +Z (front)
                -x, -y, z, x, -y, z, x, y, z, -x, y, z,
                // -Z (back)
                x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z,
                // +X (right)
                x, -y, z, x, -y, -z, x, y, -z, x, y, z,
                // -X (left)
                -x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z,
                // +Y (top)
                -x, y, z, x, y, z, x, y, -z, -x, y, -z,
                // -Y (bottom)
                -x, -y, -z, x, -y, -z, x, -y, z, -x, -y, z,
        };
        float[] normals = {
                0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
                0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1,
                1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0,
                -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0,
                0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0,
        };
        float[] uvs = new float[24 * 2];
        for (int face = 0; face < 6; face++) {
            int o = face * 8;
            uvs[o] = 0f;     uvs[o + 1] = 0f;
            uvs[o + 2] = 1f; uvs[o + 3] = 0f;
            uvs[o + 4] = 1f; uvs[o + 5] = 1f;
            uvs[o + 6] = 0f; uvs[o + 7] = 1f;
        }
        int[] indices = new int[6 * 6];
        for (int face = 0; face < 6; face++) {
            int v = face * 4;
            int i = face * 6;
            indices[i] = v;
            indices[i + 1] = v + 1;
            indices[i + 2] = v + 2;
            indices[i + 3] = v;
            indices[i + 4] = v + 2;
            indices[i + 5] = v + 3;
        }
        return new Mesh(positions, normals, uvs, indices);
    }

    public static Mesh cube(float size) {
        return box(size, size, size);
    }

    /** Flat plane on the XZ ground plane, facing +Y, subdivided for lighting. */
    public static Mesh plane(float width, float depth, int segmentsX, int segmentsZ) {
        int sx = Math.max(1, segmentsX);
        int sz = Math.max(1, segmentsZ);
        int cols = sx + 1;
        int rows = sz + 1;

        float[] positions = new float[cols * rows * 3];
        float[] normals = new float[cols * rows * 3];
        float[] uvs = new float[cols * rows * 2];
        int[] indices = new int[sx * sz * 6];

        float halfW = Math.max(1e-4f, width) * 0.5f;
        float halfD = Math.max(1e-4f, depth) * 0.5f;

        int p = 0, n = 0, t = 0;
        for (int r = 0; r < rows; r++) {
            float fz = (float) r / sz;
            for (int c = 0; c < cols; c++) {
                float fx = (float) c / sx;
                positions[p++] = -halfW + fx * halfW * 2f;
                positions[p++] = 0f;
                positions[p++] = -halfD + fz * halfD * 2f;
                normals[n++] = 0f;
                normals[n++] = 1f;
                normals[n++] = 0f;
                uvs[t++] = fx;
                uvs[t++] = fz;
            }
        }
        int i = 0;
        for (int r = 0; r < sz; r++) {
            for (int c = 0; c < sx; c++) {
                int a = r * cols + c;
                int b = a + 1;
                int d = a + cols;
                int e = d + 1;
                // Counter-clockwise seen from +Y.
                indices[i++] = a;
                indices[i++] = d;
                indices[i++] = b;
                indices[i++] = b;
                indices[i++] = d;
                indices[i++] = e;
            }
        }
        return new Mesh(positions, normals, uvs, indices);
    }

    /** UV sphere: {@code segments} around, {@code rings} from pole to pole. */
    public static Mesh sphere(float radius, int segments, int rings) {
        int seg = Math.max(3, segments);
        int rng = Math.max(2, rings);
        float r = Math.max(1e-4f, radius);

        int cols = seg + 1;
        int rows = rng + 1;
        float[] positions = new float[cols * rows * 3];
        float[] normals = new float[cols * rows * 3];
        float[] uvs = new float[cols * rows * 2];

        int p = 0, n = 0, t = 0;
        for (int row = 0; row < rows; row++) {
            double phi = Math.PI * row / rng;          // 0 at +Y pole
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);
            for (int col = 0; col < cols; col++) {
                double theta = 2.0 * Math.PI * col / seg;
                float nx = (float) (sinPhi * Math.cos(theta));
                float ny = cosPhi;
                float nz = (float) (sinPhi * Math.sin(theta));
                positions[p++] = nx * r;
                positions[p++] = ny * r;
                positions[p++] = nz * r;
                normals[n++] = nx;
                normals[n++] = ny;
                normals[n++] = nz;
                uvs[t++] = (float) col / seg;
                uvs[t++] = (float) row / rng;
            }
        }

        List<Integer> idx = new ArrayList<>(seg * rng * 6);
        for (int row = 0; row < rng; row++) {
            for (int col = 0; col < seg; col++) {
                int a = row * cols + col;
                int b = a + 1;
                int c = a + cols;
                int d = c + 1;
                // The pole rows collapse to a point: skip the degenerate half
                // so the mesh carries no zero-area triangles.
                if (row != 0) {
                    idx.add(a); idx.add(b); idx.add(c);
                }
                if (row != rng - 1) {
                    idx.add(b); idx.add(d); idx.add(c);
                }
            }
        }
        return new Mesh(positions, normals, uvs, toIntArray(idx));
    }

    /** Cylinder along Y with flat caps. */
    public static Mesh cylinder(float radius, float height, int segments) {
        return cone(radius, radius, height, segments);
    }

    /** Cone along Y; {@code topRadius} 0 gives a point, equal radii a cylinder. */
    public static Mesh cone(float bottomRadius, float topRadius, float height, int segments) {
        int seg = Math.max(3, segments);
        float rb = Math.max(0f, bottomRadius);
        float rt = Math.max(0f, topRadius);
        float h = Math.max(1e-4f, height);
        float halfH = h * 0.5f;

        List<Float> pos = new ArrayList<>();
        List<Float> nrm = new ArrayList<>();
        List<Float> uv = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        // Slope of the side, so the normals lean correctly on a cone.
        float slope = (rb - rt) / h;
        float sideNormalY = slope / (float) Math.sqrt(1f + slope * slope);
        float sideNormalXZ = 1f / (float) Math.sqrt(1f + slope * slope);

        int ringStart = pos.size() / 3;
        for (int row = 0; row < 2; row++) {
            float y = row == 0 ? -halfH : halfH;
            float rr = row == 0 ? rb : rt;
            for (int col = 0; col <= seg; col++) {
                double theta = 2.0 * Math.PI * col / seg;
                float cx = (float) Math.cos(theta);
                float cz = (float) Math.sin(theta);
                pos.add(cx * rr); pos.add(y); pos.add(cz * rr);
                nrm.add(cx * sideNormalXZ); nrm.add(sideNormalY); nrm.add(cz * sideNormalXZ);
                uv.add((float) col / seg); uv.add(row == 0 ? 0f : 1f);
            }
        }
        int cols = seg + 1;
        for (int col = 0; col < seg; col++) {
            int a = ringStart + col;
            int b = a + 1;
            int c = a + cols;
            int d = c + 1;
            if (rb > 1e-6f) {
                idx.add(a); idx.add(c); idx.add(b);
            }
            if (rt > 1e-6f) {
                idx.add(b); idx.add(c); idx.add(d);
            }
        }

        if (rb > 1e-6f) {
            appendCap(pos, nrm, uv, idx, seg, rb, -halfH, false);
        }
        if (rt > 1e-6f) {
            appendCap(pos, nrm, uv, idx, seg, rt, halfH, true);
        }
        return new Mesh(toFloatArray(pos), toFloatArray(nrm), toFloatArray(uv), toIntArray(idx));
    }

    private static void appendCap(List<Float> pos, List<Float> nrm, List<Float> uv,
                                  List<Integer> idx, int seg, float radius, float y, boolean up) {
        int centre = pos.size() / 3;
        float ny = up ? 1f : -1f;
        pos.add(0f); pos.add(y); pos.add(0f);
        nrm.add(0f); nrm.add(ny); nrm.add(0f);
        uv.add(0.5f); uv.add(0.5f);

        for (int col = 0; col <= seg; col++) {
            double theta = 2.0 * Math.PI * col / seg;
            float cx = (float) Math.cos(theta);
            float cz = (float) Math.sin(theta);
            pos.add(cx * radius); pos.add(y); pos.add(cz * radius);
            nrm.add(0f); nrm.add(ny); nrm.add(0f);
            uv.add(cx * 0.5f + 0.5f); uv.add(cz * 0.5f + 0.5f);
        }
        for (int col = 0; col < seg; col++) {
            int a = centre + 1 + col;
            int b = centre + 1 + col + 1;
            if (up) {
                idx.add(centre); idx.add(b); idx.add(a);
            } else {
                idx.add(centre); idx.add(a); idx.add(b);
            }
        }
    }

    /** Torus in the XZ plane. */
    public static Mesh torus(float majorRadius, float minorRadius, int majorSegments, int minorSegments) {
        int ms = Math.max(3, majorSegments);
        int ns = Math.max(3, minorSegments);
        float R = Math.max(1e-4f, majorRadius);
        float r = Math.max(1e-4f, minorRadius);

        int cols = ms + 1;
        int rows = ns + 1;
        float[] positions = new float[cols * rows * 3];
        float[] normals = new float[cols * rows * 3];
        float[] uvs = new float[cols * rows * 2];

        int p = 0, n = 0, t = 0;
        for (int i = 0; i < rows; i++) {
            double v = 2.0 * Math.PI * i / ns;
            float cv = (float) Math.cos(v);
            float sv = (float) Math.sin(v);
            for (int j = 0; j < cols; j++) {
                double u = 2.0 * Math.PI * j / ms;
                float cu = (float) Math.cos(u);
                float su = (float) Math.sin(u);

                positions[p++] = (R + r * cv) * cu;
                positions[p++] = r * sv;
                positions[p++] = (R + r * cv) * su;

                normals[n++] = cv * cu;
                normals[n++] = sv;
                normals[n++] = cv * su;

                uvs[t++] = (float) j / ms;
                uvs[t++] = (float) i / ns;
            }
        }
        int[] indices = new int[ms * ns * 6];
        int k = 0;
        for (int i = 0; i < ns; i++) {
            for (int j = 0; j < ms; j++) {
                int a = i * cols + j;
                int b = a + 1;
                int c = a + cols;
                int d = c + 1;
                indices[k++] = a; indices[k++] = c; indices[k++] = b;
                indices[k++] = b; indices[k++] = c; indices[k++] = d;
            }
        }
        return new Mesh(positions, normals, uvs, indices);
    }

    /** Quad facing +Z, used for sprites and billboards. */
    public static Mesh quad(float width, float height) {
        float x = Math.max(1e-4f, width) * 0.5f;
        float y = Math.max(1e-4f, height) * 0.5f;
        return new Mesh(
                new float[]{-x, -y, 0, x, -y, 0, x, y, 0, -x, y, 0},
                new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1},
                new float[]{0, 0, 1, 0, 1, 1, 0, 1},
                new int[]{0, 1, 2, 0, 2, 3});
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
