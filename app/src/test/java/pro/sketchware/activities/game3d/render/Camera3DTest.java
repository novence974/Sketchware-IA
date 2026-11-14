package pro.sketchware.activities.game3d.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pro.sketchware.activities.game3d.math.Mat4;
import pro.sketchware.activities.game3d.math.Vec3;

public class Camera3DTest {

    private static final float EPS = 1e-3f;

    @Test
    public void viewPutsTheTargetDownNegativeZ() {
        Camera3D cam = new Camera3D().setPosition(0f, 0f, 5f).setTarget(0f, 0f, 0f);
        Vec3 inView = cam.view().transformPoint(new Vec3(0f, 0f, 0f), new Vec3());
        assertEquals(0f, inView.x, EPS);
        assertEquals(0f, inView.y, EPS);
        assertEquals(-5f, inView.z, EPS);
    }

    @Test
    public void orbitKeepsTheRequestedDistanceFromTheTarget() {
        Camera3D cam = new Camera3D().setTarget(1f, 2f, 3f);
        for (float yaw = 0f; yaw < 360f; yaw += 37f) {
            for (float pitch = -80f; pitch <= 80f; pitch += 40f) {
                cam.orbit(yaw, pitch, 7.5f);
                Vec3 pos = cam.position(new Vec3());
                Vec3 target = cam.target(new Vec3());
                assertEquals("orbit distance at yaw " + yaw + " pitch " + pitch,
                        7.5f, pos.distanceTo(target), EPS);
            }
        }
    }

    @Test
    public void orbitClampsShortOfThePolesSoTheViewStaysDefined() {
        Camera3D cam = new Camera3D().setTarget(0f, 0f, 0f);
        cam.orbit(0f, 90f, 5f);
        assertTrue("straight down must still give a usable view", cam.view().isFinite());
        cam.orbit(0f, -90f, 5f);
        assertTrue("straight up must still give a usable view", cam.view().isFinite());
        cam.orbit(0f, 100000f, 5f);
        assertTrue(cam.view().isFinite());
    }

    @Test
    public void orbitRejectsAZeroDistance() {
        Camera3D cam = new Camera3D().setTarget(0f, 0f, 0f).orbit(45f, 20f, 0f);
        Vec3 pos = cam.position(new Vec3());
        assertTrue("a camera sitting exactly on its target has no view direction",
                pos.length() > 0f);
        assertTrue(cam.view().isFinite());
    }

    @Test
    public void aspectComesFromTheViewportAndSurvivesAZeroHeight() {
        Camera3D cam = new Camera3D();
        cam.setViewport(1920, 1080);
        assertEquals(1920f / 1080f, cam.aspect(), EPS);

        // A surface reports 0 height for a frame while it is being created.
        cam.setViewport(1080, 0);
        assertEquals("a degenerate viewport must fall back to square", 1f, cam.aspect(), EPS);
        assertTrue(cam.projection().isFinite());
    }

    @Test
    public void clipPlanesAreKeptPositiveAndOrdered() {
        Camera3D cam = new Camera3D().setPerspective(60f, 0f, -5f);
        assertTrue("near must stay above zero", cam.near() > 0f);
        assertTrue("far must stay beyond near", cam.far() > cam.near());
        assertTrue(cam.projection().isFinite());
    }

    @Test
    public void perspectiveAndOrthographicBothProduceFiniteMatrices() {
        Camera3D cam = new Camera3D().setViewport(800, 600);
        cam.setPerspective(70f, 0.1f, 200f);
        assertEquals(Camera3D.PROJECTION_PERSPECTIVE, cam.projectionType());
        assertTrue(cam.projection().isFinite());

        cam.setOrthographic(12f, 0.1f, 200f);
        assertEquals(Camera3D.PROJECTION_ORTHOGRAPHIC, cam.projectionType());
        assertTrue(cam.projection().isFinite());
    }

    @Test
    public void orthographicBoxMatchesTheViewportAspect() {
        Camera3D cam = new Camera3D().setViewport(200, 100).setOrthographic(10f, 0.1f, 100f);
        cam.setPosition(0f, 0f, 10f).setTarget(0f, 0f, 0f);
        // Half-height 5 and aspect 2 means half-width 10.
        Vec3 edge = cam.viewProjection().transformPoint(new Vec3(10f, 5f, 0f), new Vec3());
        assertEquals("right edge maps to +1", 1f, edge.x, EPS);
        assertEquals("top edge maps to +1", 1f, edge.y, EPS);
    }

    @Test
    public void viewProjectionIsProjectionTimesView() {
        Camera3D cam = new Camera3D().setViewport(400, 300)
                .setPerspective(55f, 0.5f, 120f)
                .setPosition(3f, 4f, 5f)
                .setTarget(0f, 1f, 0f);
        Mat4 expected = Mat4.multiply(cam.projection(), cam.view(), new Mat4());
        Mat4 actual = cam.viewProjection();
        for (int i = 0; i < 16; i++) {
            assertEquals("element " + i, expected.m[i], actual.m[i], EPS);
        }
    }

    @Test
    public void matricesRebuildAfterTheCameraMoves() {
        Camera3D cam = new Camera3D().setPosition(0f, 0f, 5f).setTarget(0f, 0f, 0f);
        float before = cam.view().m[14];
        cam.setPosition(0f, 0f, 20f);
        float after = cam.view().m[14];
        assertTrue("a cached view must be invalidated when the camera moves",
                Math.abs(before - after) > 1f);
    }

    @Test
    public void worldToScreenPlacesTheTargetAtTheCentre() {
        Camera3D cam = new Camera3D().setViewport(800, 600)
                .setPerspective(60f, 0.1f, 100f)
                .setPosition(0f, 0f, 10f)
                .setTarget(0f, 0f, 0f);
        Vec3 pixels = new Vec3();
        assertTrue(cam.worldToScreen(new Vec3(0f, 0f, 0f), 800, 600, pixels));
        assertEquals(400f, pixels.x, 0.5f);
        assertEquals(300f, pixels.y, 0.5f);
    }

    @Test
    public void worldToScreenRefusesPointsBehindTheCamera() {
        Camera3D cam = new Camera3D().setViewport(800, 600)
                .setPerspective(60f, 0.1f, 100f)
                .setPosition(0f, 0f, 10f)
                .setTarget(0f, 0f, 0f);
        // Behind the eye: the perspective divide would otherwise mirror it
        // onto the screen as if it were in front.
        assertFalse(cam.worldToScreen(new Vec3(0f, 0f, 20f), 800, 600, new Vec3()));
    }

    @Test
    public void worldToScreenMovesRightForAPointToTheRight() {
        Camera3D cam = new Camera3D().setViewport(800, 600)
                .setPerspective(60f, 0.1f, 100f)
                .setPosition(0f, 0f, 10f)
                .setTarget(0f, 0f, 0f);
        Vec3 centre = new Vec3();
        Vec3 right = new Vec3();
        cam.worldToScreen(new Vec3(0f, 0f, 0f), 800, 600, centre);
        cam.worldToScreen(new Vec3(1f, 0f, 0f), 800, 600, right);
        assertTrue("+X must move right on screen", right.x > centre.x);

        Vec3 up = new Vec3();
        cam.worldToScreen(new Vec3(0f, 1f, 0f), 800, 600, up);
        assertTrue("+Y must move up, which is a smaller pixel y", up.y < centre.y);
    }

    @Test
    public void forwardPointsFromTheEyeToTheTarget() {
        Camera3D cam = new Camera3D().setPosition(0f, 0f, 10f).setTarget(0f, 0f, 0f);
        Vec3 f = cam.forward(new Vec3());
        assertEquals(0f, f.x, EPS);
        assertEquals(0f, f.y, EPS);
        assertEquals(-1f, f.z, EPS);
    }
}
