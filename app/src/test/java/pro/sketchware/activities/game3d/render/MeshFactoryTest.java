package pro.sketchware.activities.game3d.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pro.sketchware.activities.game3d.math.Vec3;

public class MeshFactoryTest {

    private static final float EPS = 1e-3f;

    /** Signed volume via the divergence theorem; positive means outward winding. */
    private static float signedVolume(Mesh mesh) {
        float total = 0f;
        for (int t = 0; t < mesh.indices.length; t += 3) {
            int a = mesh.indices[t] * 3;
            int b = mesh.indices[t + 1] * 3;
            int c = mesh.indices[t + 2] * 3;
            float ax = mesh.positions[a], ay = mesh.positions[a + 1], az = mesh.positions[a + 2];
            float bx = mesh.positions[b], by = mesh.positions[b + 1], bz = mesh.positions[b + 2];
            float cx = mesh.positions[c], cy = mesh.positions[c + 1], cz = mesh.positions[c + 2];
            total += (ax * (by * cz - bz * cy)
                    - ay * (bx * cz - bz * cx)
                    + az * (bx * cy - by * cx)) / 6f;
        }
        return total;
    }

    /** True when no triangle has (near) zero area. */
    private static boolean hasDegenerateTriangle(Mesh mesh) {
        for (int t = 0; t < mesh.indices.length; t += 3) {
            int a = mesh.indices[t] * 3;
            int b = mesh.indices[t + 1] * 3;
            int c = mesh.indices[t + 2] * 3;
            float e1x = mesh.positions[b] - mesh.positions[a];
            float e1y = mesh.positions[b + 1] - mesh.positions[a + 1];
            float e1z = mesh.positions[b + 2] - mesh.positions[a + 2];
            float e2x = mesh.positions[c] - mesh.positions[a];
            float e2y = mesh.positions[c + 1] - mesh.positions[a + 1];
            float e2z = mesh.positions[c + 2] - mesh.positions[a + 2];
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            if (nx * nx + ny * ny + nz * nz < 1e-12f) {
                return true;
            }
        }
        return false;
    }

    private static void assertNormalsAreUnitLength(Mesh mesh) {
        for (int i = 0; i < mesh.normals.length; i += 3) {
            float len = (float) Math.sqrt(
                    mesh.normals[i] * mesh.normals[i]
                            + mesh.normals[i + 1] * mesh.normals[i + 1]
                            + mesh.normals[i + 2] * mesh.normals[i + 2]);
            assertEquals("normal " + (i / 3) + " must be unit length", 1f, len, EPS);
        }
    }

    /**
     * For a convex solid centred on the origin, every normal must point away
     * from the centre — the check that catches an inside-out mesh.
     */
    private static void assertNormalsFaceOutward(Mesh mesh) {
        for (int v = 0; v < mesh.vertexCount(); v++) {
            int p = v * 3;
            float dot = mesh.positions[p] * mesh.normals[p]
                    + mesh.positions[p + 1] * mesh.normals[p + 1]
                    + mesh.positions[p + 2] * mesh.normals[p + 2];
            assertTrue("vertex " + v + " has an inward normal (dot=" + dot + ")", dot > -EPS);
        }
    }

    @Test
    public void boxHasTwelveTrianglesAndFlatFaceNormals() {
        Mesh box = MeshFactory.box(2f, 4f, 6f);
        assertEquals(24, box.vertexCount());
        assertEquals(12, box.triangleCount());
        assertNormalsAreUnitLength(box);
        assertFalse(hasDegenerateTriangle(box));

        Vec3 min = box.boundsMin(new Vec3());
        Vec3 max = box.boundsMax(new Vec3());
        assertEquals(-1f, min.x, EPS);
        assertEquals(-2f, min.y, EPS);
        assertEquals(-3f, min.z, EPS);
        assertEquals(1f, max.x, EPS);
        assertEquals(2f, max.y, EPS);
        assertEquals(3f, max.z, EPS);
    }

    @Test
    public void boxIsWoundOutwardAndEnclosesItsVolume() {
        Mesh box = MeshFactory.box(2f, 2f, 2f);
        assertEquals("a 2x2x2 box encloses 8 cubic units", 8f, signedVolume(box), EPS);
        assertNormalsFaceOutward(box);
    }

    @Test
    public void sphereApproximatesItsAnalyticVolumeAndFacesOutward() {
        Mesh sphere = MeshFactory.sphere(1f, 48, 24);
        float expected = (float) (4.0 / 3.0 * Math.PI);
        float volume = signedVolume(sphere);

        // Positive means the faces are wound outward — the check that catches
        // an inside-out sphere. The magnitude is a touch under the analytic
        // value because the tessellation is inscribed in the true surface.
        assertTrue("the sphere must be wound outward, got " + volume, volume > 0f);
        assertEquals("sphere volume", expected, volume, 0.05f);
        assertNormalsAreUnitLength(sphere);
        assertNormalsFaceOutward(sphere);
    }

    @Test
    public void finerSphereTessellationConvergesOnTheAnalyticVolume() {
        float exact = (float) (4.0 / 3.0 * Math.PI);
        float coarse = Math.abs(exact - signedVolume(MeshFactory.sphere(1f, 12, 6)));
        float medium = Math.abs(exact - signedVolume(MeshFactory.sphere(1f, 48, 24)));
        float fine = Math.abs(exact - signedVolume(MeshFactory.sphere(1f, 192, 96)));
        assertTrue("error must shrink with tessellation: " + coarse + " -> " + medium,
                medium < coarse);
        assertTrue("error must keep shrinking: " + medium + " -> " + fine, fine < medium);
        assertTrue("a fine sphere should be within 0.1% of exact", fine < exact * 0.001f);
    }

    @Test
    public void sphereCarriesNoDegeneratePoleTriangles() {
        // The naive UV sphere emits zero-area triangles at both poles; they
        // break smooth-normal averaging and waste fill rate.
        assertFalse(hasDegenerateTriangle(MeshFactory.sphere(1f, 16, 8)));
    }

    @Test
    public void sphereClampsSillyTessellation() {
        Mesh degenerate = MeshFactory.sphere(1f, 0, 0);
        assertTrue("segments and rings must be clamped to something drawable",
                degenerate.triangleCount() > 0);
    }

    @Test
    public void cylinderIsClosedAndHasTheRightVolume() {
        Mesh cyl = MeshFactory.cylinder(1f, 2f, 64);
        float expected = (float) (Math.PI * 1f * 1f * 2f);
        assertEquals("a capped cylinder must enclose its volume", expected, signedVolume(cyl), 0.02f);
        assertNormalsAreUnitLength(cyl);
        assertFalse(hasDegenerateTriangle(cyl));
    }

    @Test
    public void coneWithAZeroTopIsStillClosed() {
        Mesh cone = MeshFactory.cone(1f, 0f, 2f, 64);
        float expected = (float) (Math.PI * 1f * 1f * 2f / 3f);
        assertEquals("cone volume", expected, signedVolume(cone), 0.02f);
        assertNormalsAreUnitLength(cone);
        assertFalse("a zero-radius cap must not emit slivers", hasDegenerateTriangle(cone));
    }

    @Test
    public void torusHasNoHolesAndUnitNormals() {
        Mesh torus = MeshFactory.torus(2f, 0.5f, 48, 24);
        float expected = (float) (2 * Math.PI * Math.PI * 2f * 0.5f * 0.5f);
        float volume = signedVolume(torus);
        assertTrue("the torus must be wound outward, got " + volume, volume > 0f);
        assertEquals("torus volume", expected, volume, 0.2f);
        assertNormalsAreUnitLength(torus);
        assertFalse(hasDegenerateTriangle(torus));

        // A closed torus converges on the analytic volume as it is refined.
        float fine = signedVolume(MeshFactory.torus(2f, 0.5f, 256, 128));
        assertEquals("a fine torus is close to exact", expected, fine, expected * 0.001f);
    }

    @Test
    public void planeFacesUpAndIsSubdivided() {
        Mesh plane = MeshFactory.plane(10f, 10f, 4, 4);
        assertEquals(25, plane.vertexCount());
        assertEquals(32, plane.triangleCount());
        for (int i = 0; i < plane.normals.length; i += 3) {
            assertEquals(0f, plane.normals[i], EPS);
            assertEquals(1f, plane.normals[i + 1], EPS);
            assertEquals(0f, plane.normals[i + 2], EPS);
        }
        assertFalse(hasDegenerateTriangle(plane));
    }

    @Test
    public void planeTrianglesFaceUpwards() {
        Mesh plane = MeshFactory.plane(2f, 2f, 1, 1);
        for (int t = 0; t < plane.indices.length; t += 3) {
            int a = plane.indices[t] * 3;
            int b = plane.indices[t + 1] * 3;
            int c = plane.indices[t + 2] * 3;
            float e1x = plane.positions[b] - plane.positions[a];
            float e1z = plane.positions[b + 2] - plane.positions[a + 2];
            float e2x = plane.positions[c] - plane.positions[a];
            float e2z = plane.positions[c + 2] - plane.positions[a + 2];
            float ny = e1z * e2x - e1x * e2z;
            assertTrue("a ground plane triangle must face +Y, got ny=" + ny, ny > 0f);
        }
    }

    @Test
    public void quadFacesForwardWithFullUvCoverage() {
        Mesh quad = MeshFactory.quad(2f, 2f);
        assertEquals(4, quad.vertexCount());
        assertEquals(2, quad.triangleCount());
        assertEquals(0f, quad.uvs[0], EPS);
        assertEquals(1f, quad.uvs[5], EPS);
    }

    @Test
    public void everyPrimitiveProducesValidUvs() {
        Mesh[] meshes = {
                MeshFactory.box(1f, 1f, 1f),
                MeshFactory.sphere(1f, 12, 6),
                MeshFactory.cylinder(1f, 2f, 12),
                MeshFactory.cone(1f, 0f, 2f, 12),
                MeshFactory.torus(2f, 0.5f, 12, 8),
                MeshFactory.plane(4f, 4f, 2, 2),
                MeshFactory.quad(1f, 1f),
        };
        for (Mesh mesh : meshes) {
            assertEquals("one uv pair per vertex", mesh.vertexCount() * 2, mesh.uvs.length);
            for (float uv : mesh.uvs) {
                assertTrue("uv out of range: " + uv, uv >= -EPS && uv <= 1f + EPS);
            }
            assertTrue("primitives must fit short indices", mesh.fitsShortIndices());
        }
    }

    @Test
    public void recalculateNormalsRecoversTheGeneratedOnes() {
        int segments = 32;
        int rings = 16;
        Mesh sphere = MeshFactory.sphere(1f, segments, rings);
        float[] analytic = sphere.normals.clone();
        sphere.recalculateNormals();

        int cols = segments + 1;
        for (int v = 0; v < sphere.vertexCount(); v++) {
            int row = v / cols;
            // A smooth normal equals the analytic one only where the vertex
            // sits in a full, symmetric ring of quads. The two pole rows are
            // fans of duplicated vertices, and the rows next to them are
            // weighted by those fans, so both are compared loosely below.
            if (row <= 1 || row >= rings - 1) {
                continue;
            }
            // The UV seam duplicates the column at theta = 0 and theta = 2pi:
            // each copy is touched by only the quads on its own side, so its
            // averaged normal leans that way. The analytic normal is the one
            // the renderer uses; this is a property of the seam, not a defect.
            int col = v % cols;
            if (col == 0 || col == segments) {
                continue;
            }
            int i = v * 3;
            for (int k = 0; k < 3; k++) {
                assertEquals("smooth normal differs at vertex " + v + " component " + k,
                        analytic[i + k], sphere.normals[i + k], 0.02f);
            }
        }
    }

    @Test
    public void poleAdjacentNormalsStayCloseToAnalytic() {
        int segments = 32;
        int rings = 16;
        Mesh sphere = MeshFactory.sphere(1f, segments, rings);
        float[] analytic = sphere.normals.clone();
        sphere.recalculateNormals();
        int cols = segments + 1;
        for (int v = 0; v < sphere.vertexCount(); v++) {
            int row = v / cols;
            if (row > 1 && row < rings - 1) {
                continue;
            }
            int i = v * 3;
            float dot = analytic[i] * sphere.normals[i]
                    + analytic[i + 1] * sphere.normals[i + 1]
                    + analytic[i + 2] * sphere.normals[i + 2];
            assertTrue("pole-region normal flipped away from analytic at vertex "
                    + v + " (dot=" + dot + ")", dot > 0.7f);
        }
    }

    @Test
    public void poleNormalsStillPointAwayFromTheCentre() {
        Mesh sphere = MeshFactory.sphere(1f, 32, 16);
        sphere.recalculateNormals();
        // Even at the fan vertices the normal must not fold inward, or the
        // poles would render as black holes.
        assertNormalsFaceOutward(sphere);
        assertNormalsAreUnitLength(sphere);
    }

    @Test
    public void normalizeToUnitCubeCentresAndFits() {
        Mesh box = MeshFactory.box(10f, 4f, 2f);
        box.normalizeToUnitCube();
        Vec3 min = box.boundsMin(new Vec3());
        Vec3 max = box.boundsMax(new Vec3());
        assertEquals(-0.5f, min.x, EPS);
        assertEquals(0.5f, max.x, EPS);
        Vec3 centre = box.boundsCenter(new Vec3());
        assertEquals(0f, centre.x, EPS);
        assertEquals(0f, centre.y, EPS);
        assertEquals(0f, centre.z, EPS);
    }

    @Test
    public void meshRejectsAnIndexOutsideTheVertexList() {
        try {
            new Mesh(new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, null, null, new int[]{0, 1, 7});
            throw new AssertionError("an out-of-range index must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("7"));
        }
    }

    @Test
    public void meshRejectsMismatchedAttributeLengths() {
        try {
            new Mesh(new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, new float[]{0, 1, 0}, null, new int[]{0, 1, 2});
            throw new AssertionError("normals of the wrong length must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("normals"));
        }
    }

    @Test
    public void aVertexOnlyInDegenerateTrianglesGetsAUsableNormal() {
        // Two coincident vertices: the cross product is zero everywhere.
        Mesh flat = new Mesh(
                new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0},
                null, null, new int[]{0, 1, 2});
        for (int i = 0; i < flat.normals.length; i += 3) {
            float len = (float) Math.sqrt(
                    flat.normals[i] * flat.normals[i]
                            + flat.normals[i + 1] * flat.normals[i + 1]
                            + flat.normals[i + 2] * flat.normals[i + 2]);
            assertEquals("a fallback normal must still be unit length", 1f, len, EPS);
        }
    }
}
