package pro.sketchware.activities.game3d.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;

public class ObjLoaderTest {

    private static final float EPS = 1e-4f;

    private static final String TRIANGLE =
            "# a comment\n"
                    + "v 0 0 0\n"
                    + "v 1 0 0\n"
                    + "v 0 1 0\n"
                    + "f 1 2 3\n";

    @Test
    public void readsAPlainTriangle() throws IOException {
        Mesh mesh = ObjLoader.parse(TRIANGLE);
        assertEquals(3, mesh.vertexCount());
        assertEquals(1, mesh.triangleCount());
        assertEquals(1f, mesh.positions[3], EPS);
        assertEquals(1f, mesh.positions[7], EPS);
    }

    @Test
    public void generatesNormalsWhenTheFileHasNone() throws IOException {
        Mesh mesh = ObjLoader.parse(TRIANGLE);
        // The triangle lies in the XY plane wound counter-clockwise, so its
        // normal is +Z.
        for (int i = 0; i < mesh.normals.length; i += 3) {
            assertEquals(0f, mesh.normals[i], EPS);
            assertEquals(0f, mesh.normals[i + 1], EPS);
            assertEquals(1f, mesh.normals[i + 2], EPS);
        }
    }

    @Test
    public void readsNormalsAndTexcoordsWhenPresent() throws IOException {
        Mesh mesh = ObjLoader.parse(
                "v 0 0 0\nv 1 0 0\nv 0 1 0\n"
                        + "vt 0 0\nvt 1 0\nvt 0 1\n"
                        + "vn 0 0 1\n"
                        + "f 1/1/1 2/2/1 3/3/1\n");
        assertEquals(3, mesh.vertexCount());
        assertEquals(1f, mesh.normals[2], EPS);
        assertEquals(1f, mesh.uvs[2], EPS);
        assertEquals(1f, mesh.uvs[5], EPS);
    }

    @Test
    public void acceptsFacesWithoutTexcoords() throws IOException {
        Mesh mesh = ObjLoader.parse(
                "v 0 0 0\nv 1 0 0\nv 0 1 0\nvn 0 0 1\nf 1//1 2//1 3//1\n");
        assertEquals(3, mesh.vertexCount());
        assertEquals(1f, mesh.normals[2], EPS);
    }

    @Test
    public void acceptsFacesWithTexcoordsButNoNormals() throws IOException {
        Mesh mesh = ObjLoader.parse(
                "v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nf 1/1 2/2 3/3\n");
        assertEquals(3, mesh.vertexCount());
        assertEquals("missing normals must be generated", 1f, mesh.normals[2], EPS);
    }

    @Test
    public void triangulatesAQuadIntoTwoTriangles() throws IOException {
        Mesh mesh = ObjLoader.parse(
                "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3 4\n");
        assertEquals(4, mesh.vertexCount());
        assertEquals(2, mesh.triangleCount());
    }

    @Test
    public void triangulatesAnNgon() throws IOException {
        StringBuilder sb = new StringBuilder();
        int n = 6;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            sb.append("v ").append(Math.cos(a)).append(" ").append(Math.sin(a)).append(" 0\n");
        }
        sb.append("f 1 2 3 4 5 6\n");
        Mesh mesh = ObjLoader.parse(sb.toString());
        assertEquals("a hexagon fans into 4 triangles", 4, mesh.triangleCount());
    }

    @Test
    public void resolvesNegativeIndicesRelativeToTheEnd() throws IOException {
        Mesh mesh = ObjLoader.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nf -3 -2 -1\n");
        assertEquals(3, mesh.vertexCount());
        assertEquals(1f, mesh.positions[3], EPS);
    }

    @Test
    public void reusesAVertexSharedBetweenFaces() throws IOException {
        // Two triangles over four positions sharing an edge: the shared
        // corners must not be duplicated.
        Mesh mesh = ObjLoader.parse(
                "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3\nf 1 3 4\n");
        assertEquals("shared corners must be de-duplicated", 4, mesh.vertexCount());
        assertEquals(2, mesh.triangleCount());
    }

    @Test
    public void splitsAPositionUsedWithDifferentNormals() throws IOException {
        // The same position with two normals is two GL vertices: OBJ indexes
        // them separately but OpenGL cannot.
        Mesh mesh = ObjLoader.parse(
                "v 0 0 0\nv 1 0 0\nv 0 1 0\n"
                        + "vn 0 0 1\nvn 1 0 0\n"
                        + "f 1//1 2//1 3//1\n"
                        + "f 1//2 2//2 3//2\n");
        assertEquals(6, mesh.vertexCount());
        assertEquals(2, mesh.triangleCount());
    }

    @Test
    public void ignoresDirectivesItDoesNotImplement() throws IOException {
        Mesh mesh = ObjLoader.parse(
                "mtllib scene.mtl\no Cube\ng mesh1\nusemtl Steel\ns off\n"
                        + "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");
        assertNotNull(mesh);
        assertEquals(1, mesh.triangleCount());
    }

    @Test
    public void toleratesBlankLinesAndExtraWhitespace() throws IOException {
        Mesh mesh = ObjLoader.parse(
                "\n   \nv   0 0 0\n\nv 1 0 0\n   v 0 1 0   \n\nf  1  2  3 \n\n");
        assertEquals(1, mesh.triangleCount());
    }

    @Test
    public void rejectsAFileWithNoFaces() {
        try {
            ObjLoader.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\n");
            fail("a file with no faces must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no faces"));
        }
    }

    @Test
    public void rejectsAnIndexPastTheEnd() {
        try {
            ObjLoader.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 9\n");
            fail("an out-of-range index must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("out of range"));
        }
    }

    @Test
    public void rejectsNonNumericCoordinates() {
        try {
            ObjLoader.parse("v 0 zero 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");
            fail("a non-numeric coordinate must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not a number"));
        }
    }

    @Test
    public void rejectsAFaceWithTooFewCorners() {
        try {
            ObjLoader.parse("v 0 0 0\nv 1 0 0\nf 1 2\n");
            fail("a two-corner face must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("3 corners"));
        }
    }

    @Test
    public void errorsNameTheOffendingLine() {
        try {
            ObjLoader.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 42\n");
            fail("expected a parse failure");
        } catch (IOException expected) {
            assertTrue("the message must point at line 4: " + expected.getMessage(),
                    expected.getMessage().contains("line 4"));
        }
    }

    @Test
    public void loadsAFullCubeAndEnclosesVolume() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("v -1 -1 -1\nv 1 -1 -1\nv 1 1 -1\nv -1 1 -1\n");
        sb.append("v -1 -1 1\nv 1 -1 1\nv 1 1 1\nv -1 1 1\n");
        sb.append("f 5 6 7 8\n");   // +Z
        sb.append("f 2 1 4 3\n");   // -Z
        sb.append("f 6 2 3 7\n");   // +X
        sb.append("f 1 5 8 4\n");   // -X
        sb.append("f 8 7 3 4\n");   // +Y
        sb.append("f 1 2 6 5\n");   // -Y
        Mesh mesh = ObjLoader.parse(sb.toString());
        assertEquals(12, mesh.triangleCount());

        float volume = 0f;
        for (int t = 0; t < mesh.indices.length; t += 3) {
            int a = mesh.indices[t] * 3, b = mesh.indices[t + 1] * 3, c = mesh.indices[t + 2] * 3;
            float ax = mesh.positions[a], ay = mesh.positions[a + 1], az = mesh.positions[a + 2];
            float bx = mesh.positions[b], by = mesh.positions[b + 1], bz = mesh.positions[b + 2];
            float cx = mesh.positions[c], cy = mesh.positions[c + 1], cz = mesh.positions[c + 2];
            volume += (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6f;
        }
        assertEquals("a 2x2x2 cube encloses 8 units", 8f, volume, 1e-3f);
    }
}
