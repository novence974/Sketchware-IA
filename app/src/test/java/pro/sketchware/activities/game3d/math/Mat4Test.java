package pro.sketchware.activities.game3d.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Mat4Test {

    private static final float EPS = 1e-4f;

    private static void assertVec(String what, float x, float y, float z, Vec3 actual) {
        assertEquals(what + " x", x, actual.x, EPS);
        assertEquals(what + " y", y, actual.y, EPS);
        assertEquals(what + " z", z, actual.z, EPS);
    }

    private static void assertMatEquals(String what, Mat4 expected, Mat4 actual) {
        for (int i = 0; i < 16; i++) {
            assertEquals(what + " [" + i + "]", expected.m[i], actual.m[i], EPS);
        }
    }

    @Test
    public void identityLeavesPointsAlone() {
        Mat4 id = new Mat4();
        Vec3 out = id.transformPoint(new Vec3(3f, -4f, 5f), new Vec3());
        assertVec("identity", 3f, -4f, 5f, out);
    }

    @Test
    public void layoutIsColumnMajorSoOpenGlCanUseItDirectly() {
        Mat4 t = new Mat4().setTranslation(7f, 8f, 9f);
        // In column-major, translation sits in the last column: indices 12-14.
        assertEquals(7f, t.m[12], EPS);
        assertEquals(8f, t.m[13], EPS);
        assertEquals(9f, t.m[14], EPS);
        assertEquals(1f, t.m[15], EPS);
        assertEquals("get(row,col) must agree with the raw layout", 7f, t.get(0, 3), EPS);
    }

    @Test
    public void multiplicationAppliesTheRightOperandFirst() {
        Mat4 translate = new Mat4().setTranslation(10f, 0f, 0f);
        Mat4 scale = new Mat4().setScale(2f, 2f, 2f);

        // translate * scale: scale the point, then move it.
        Mat4 ts = Mat4.multiply(translate, scale, new Mat4());
        assertVec("translate*scale", 12f, 0f, 0f, ts.transformPoint(new Vec3(1f, 0f, 0f), new Vec3()));

        // scale * translate: move it, then scale the whole offset.
        Mat4 st = Mat4.multiply(scale, translate, new Mat4());
        assertVec("scale*translate", 22f, 0f, 0f, st.transformPoint(new Vec3(1f, 0f, 0f), new Vec3()));
    }

    @Test
    public void multiplyIsSafeWhenTheDestinationAliasesAnInput() {
        Mat4 a = new Mat4().setTranslation(1f, 2f, 3f);
        Mat4 b = new Mat4().setScale(2f, 3f, 4f);
        Mat4 expected = Mat4.multiply(a, b, new Mat4());

        Mat4 aliased = new Mat4(a);
        Mat4.multiply(aliased, b, aliased);
        assertMatEquals("dst == a", expected, aliased);

        Mat4 aliasedB = new Mat4(b);
        Mat4.multiply(a, aliasedB, aliasedB);
        assertMatEquals("dst == b", expected, aliasedB);
    }

    @Test
    public void rotationAboutYTurnsXIntoMinusZ() {
        Mat4 r = new Mat4().setRotation(90f, 0f, 1f, 0f);
        assertVec("90 deg about Y", 0f, 0f, -1f, r.transformPoint(new Vec3(1f, 0f, 0f), new Vec3()));
    }

    @Test
    public void rotationPreservesLength() {
        Mat4 r = new Mat4().setRotation(37f, 0.3f, 0.5f, -0.8f);
        Vec3 v = new Vec3(1f, 2f, -3f);
        float before = v.length();
        Vec3 after = r.transformDirection(v, new Vec3());
        assertEquals("a rotation is rigid", before, after.length(), EPS);
    }

    @Test
    public void rotationAboutAZeroAxisIsIdentityRatherThanNaN() {
        Mat4 r = new Mat4().setRotation(45f, 0f, 0f, 0f);
        assertTrue(r.isFinite());
        assertMatEquals("degenerate axis", new Mat4(), r);
    }

    @Test
    public void translateAndScalePostMultiplyLikeOpenGl() {
        Mat4 m = new Mat4();
        m.translate(5f, 0f, 0f);
        m.scale(2f, 2f, 2f);
        // The scale happens in the already-translated frame, so the point is
        // scaled first and then offset.
        assertVec("translate then scale", 7f, 0f, 0f, m.transformPoint(new Vec3(1f, 0f, 0f), new Vec3()));
    }

    @Test
    public void transformDirectionIgnoresTranslation() {
        Mat4 m = new Mat4().setTranslation(100f, 200f, 300f);
        assertVec("direction", 1f, 0f, 0f, m.transformDirection(new Vec3(1f, 0f, 0f), new Vec3()));
    }

    @Test
    public void inverseUndoesTheTransform() {
        Mat4 m = new Mat4().setRotation(31f, 0.2f, 1f, 0.4f);
        m.translate(4f, -2f, 7f);
        m.scale(2f, 3f, 0.5f);

        Mat4 inv = new Mat4();
        assertTrue("a well-formed transform must invert", m.invertInto(inv));

        Mat4 product = Mat4.multiply(m, inv, new Mat4());
        assertMatEquals("m * m^-1 must be identity", new Mat4(), product);
    }

    @Test
    public void inverseRefusesASingularMatrix() {
        Mat4 flat = new Mat4().setScale(1f, 0f, 1f);
        Mat4 dst = new Mat4().setTranslation(9f, 9f, 9f);
        assertFalse("a zero-scale matrix has no inverse", flat.invertInto(dst));
        assertEquals("dst must be left untouched on failure", 9f, dst.m[12], EPS);
    }

    @Test
    public void normalMatrixKeepsNormalsPerpendicularUnderNonUniformScale() {
        // A 45-degree surface scaled only on Y: the model matrix alone would
        // tilt the normal the wrong way.
        Mat4 model = new Mat4().setScale(1f, 4f, 1f);
        Mat4 normalMat = new Mat4();
        assertTrue(model.normalMatrixInto(normalMat));

        Vec3 tangent = new Vec3(1f, 1f, 0f).normalize();
        Vec3 normal = new Vec3(-1f, 1f, 0f).normalize();

        Vec3 tangentWorld = model.transformDirection(tangent, new Vec3()).normalize();
        Vec3 normalWorld = normalMat.transformDirection(normal, new Vec3()).normalize();

        assertEquals("normal must stay perpendicular to the surface",
                0f, tangentWorld.dot(normalWorld), 1e-3f);
    }

    @Test
    public void perspectiveMapsTheNearAndFarPlanesToMinusOneAndOne() {
        Mat4 p = new Mat4().setPerspective(60f, 16f / 9f, 1f, 100f);
        Vec3 onNear = p.transformPoint(new Vec3(0f, 0f, -1f), new Vec3());
        Vec3 onFar = p.transformPoint(new Vec3(0f, 0f, -100f), new Vec3());
        assertEquals("near plane -> -1", -1f, onNear.z, 1e-3f);
        assertEquals("far plane -> +1", 1f, onFar.z, 1e-3f);
    }

    @Test
    public void perspectiveSurvivesAZeroAspectRatio() {
        // A surface reports 0 width for one frame while it is being created.
        Mat4 p = new Mat4().setPerspective(60f, 0f, 0.1f, 100f);
        assertTrue("must not produce infinities", p.isFinite());
    }

    @Test
    public void orthoMapsTheBoxOntoTheUnitCube() {
        Mat4 o = new Mat4().setOrtho(-2f, 2f, -1f, 1f, 0f, 10f);
        assertVec("min corner", -1f, -1f, -1f, o.transformPoint(new Vec3(-2f, -1f, 0f), new Vec3()));
        assertVec("max corner", 1f, 1f, 1f, o.transformPoint(new Vec3(2f, 1f, -10f), new Vec3()));
    }

    @Test
    public void lookAtPlacesTheTargetDownTheNegativeZAxis() {
        Mat4 view = new Mat4().setLookAt(
                new Vec3(0f, 0f, 10f), new Vec3(0f, 0f, 0f), new Vec3(0f, 1f, 0f));
        Vec3 target = view.transformPoint(new Vec3(0f, 0f, 0f), new Vec3());
        assertVec("target in view space", 0f, 0f, -10f, target);

        Vec3 eye = view.transformPoint(new Vec3(0f, 0f, 10f), new Vec3());
        assertVec("the eye sits at the view origin", 0f, 0f, 0f, eye);
    }

    @Test
    public void lookAtHandlesAnUpVectorParallelToTheViewDirection() {
        // Looking straight down with up = +Y is the classic degenerate case.
        Mat4 view = new Mat4().setLookAt(
                new Vec3(0f, 10f, 0f), new Vec3(0f, 0f, 0f), new Vec3(0f, 1f, 0f));
        assertTrue("must not fall apart into NaNs", view.isFinite());
    }

    @Test
    public void transposeIsItsOwnInverse() {
        Mat4 m = new Mat4().setRotation(23f, 1f, 2f, 3f);
        m.translate(1f, 2f, 3f);
        Mat4 original = new Mat4(m);
        m.transpose().transpose();
        assertMatEquals("transposed twice", original, m);
    }
}
