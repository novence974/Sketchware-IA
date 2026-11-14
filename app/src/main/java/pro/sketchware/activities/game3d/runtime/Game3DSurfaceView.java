package pro.sketchware.activities.game3d.runtime;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.Nullable;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import pro.sketchware.activities.game3d.model.Game3DScene;
import pro.sketchware.activities.game3d.render.Camera3D;
import pro.sketchware.activities.game3d.render.Mesh;
import pro.sketchware.activities.game3d.render.Renderer3D;

/**
 * The 3D viewport: an OpenGL ES 2.0 surface that draws a scene and lets the
 * user orbit, pan and zoom it.
 *
 * <p>GL work only happens on the GL thread, so the scene reference and camera
 * state are written from the UI thread and read on the GL thread behind a
 * lock. Touch state is accumulated on the UI thread and applied to the camera
 * inside the draw call.</p>
 */
public class Game3DSurfaceView extends GLSurfaceView {

    /** Reported after each frame so the editor can show a statistics line. */
    public interface FrameListener {
        void onFrame(int objects, int triangles, float fps);
    }

    private final Renderer3D renderer = new Renderer3D();
    private final Camera3D camera = new Camera3D();
    private final Object sceneLock = new Object();

    private Game3DScene scene;
    private boolean cullBackFaces = true;

    private float yaw = 35f;
    private float pitch = 25f;
    private float distance = 12f;
    private float targetX = 0f, targetY = 0f, targetZ = 0f;

    private float lastTouchX, lastTouchY;
    private int activePointerId = -1;
    private boolean panning;
    private ScaleGestureDetector pinch;

    @Nullable
    private FrameListener frameListener;

    private long lastFrameNanos;
    private float smoothedFps = 60f;

    public Game3DSurfaceView(Context context) {
        super(context);
        init(context);
    }

    public Game3DSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setEGLContextClientVersion(2);
        // A depth buffer is not guaranteed without asking for one, and
        // without it everything draws in submission order.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(new GlCallbacks());
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (factor > 0.01f) {
                    setDistance(distance / factor);
                }
                return true;
            }
        });
    }

    public void setScene(@Nullable Game3DScene newScene) {
        synchronized (sceneLock) {
            scene = newScene;
            if (newScene != null) {
                yaw = newScene.editorCamYaw;
                pitch = newScene.editorCamPitch;
                distance = Math.max(0.5f, newScene.editorCamDistance);
                targetX = newScene.editorCamTargetX;
                targetY = newScene.editorCamTargetY;
                targetZ = newScene.editorCamTargetZ;
            }
        }
        requestRender();
    }

    /** Stores the current viewpoint back on the scene so it survives a reopen. */
    public void saveCameraToScene() {
        synchronized (sceneLock) {
            if (scene != null) {
                scene.editorCamYaw = yaw;
                scene.editorCamPitch = pitch;
                scene.editorCamDistance = distance;
                scene.editorCamTargetX = targetX;
                scene.editorCamTargetY = targetY;
                scene.editorCamTargetZ = targetZ;
            }
        }
    }

    public void setCullBackFaces(boolean cull) {
        cullBackFaces = cull;
        requestRender();
    }

    public void setFrameListener(@Nullable FrameListener listener) {
        this.frameListener = listener;
    }

    /** Registers imported .obj geometry; safe to call from the UI thread. */
    public void putImportedModel(String asset, Mesh mesh) {
        queueEvent(() -> renderer.putImportedModel(asset, mesh));
        requestRender();
    }

    /** Frames the whole scene, so a newly opened level is never off-screen. */
    public void frameScene() {
        synchronized (sceneLock) {
            if (scene == null || scene.objects.isEmpty()) {
                return;
            }
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            int counted = 0;
            for (var obj : scene.objects) {
                if (obj == null) continue;
                float r = obj.approximateRadius();
                minX = Math.min(minX, obj.x - r);
                minY = Math.min(minY, obj.y - r);
                minZ = Math.min(minZ, obj.z - r);
                maxX = Math.max(maxX, obj.x + r);
                maxY = Math.max(maxY, obj.y + r);
                maxZ = Math.max(maxZ, obj.z + r);
                counted++;
            }
            if (counted == 0) {
                return;
            }
            targetX = (minX + maxX) * 0.5f;
            targetY = (minY + maxY) * 0.5f;
            targetZ = (minZ + maxZ) * 0.5f;
            float span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
            distance = Math.max(2f, span * 1.6f);
        }
        requestRender();
    }

    private void setDistance(float value) {
        distance = Math.max(0.5f, Math.min(5000f, value));
        requestRender();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        pinch.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                panning = false;
            }
            case MotionEvent.ACTION_POINTER_DOWN -> panning = event.getPointerCount() >= 2;
            case MotionEvent.ACTION_MOVE -> {
                if (pinch.isInProgress()) {
                    break;
                }
                int index = event.findPointerIndex(activePointerId);
                if (index < 0) {
                    break;
                }
                float dx = event.getX(index) - lastTouchX;
                float dy = event.getY(index) - lastTouchY;
                lastTouchX = event.getX(index);
                lastTouchY = event.getY(index);
                if (panning) {
                    // Pan in the camera's own plane, scaled by distance so the
                    // scene keeps up with the finger at any zoom.
                    float scale = distance * 0.0015f;
                    double y = Math.toRadians(yaw);
                    float rightX = (float) Math.cos(y);
                    float rightZ = (float) -Math.sin(y);
                    targetX -= rightX * dx * scale;
                    targetZ -= rightZ * dx * scale;
                    targetY += dy * scale;
                } else {
                    yaw -= dx * 0.35f;
                    pitch = Math.max(-89.5f, Math.min(89.5f, pitch + dy * 0.35f));
                }
                requestRender();
            }
            case MotionEvent.ACTION_POINTER_UP -> panning = event.getPointerCount() > 2;
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1;
                panning = false;
                performClick();
            }
            default -> {
            }
        }
        return true;
    }

    private final class GlCallbacks implements GLSurfaceView.Renderer {

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // The context can be recreated at any time (the app going to the
            // background is enough), and every GL handle dies with it, so
            // everything is rebuilt here rather than once at startup.
            renderer.initGl();
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            renderer.setViewportSize(width, height);
            camera.setViewport(width, height);
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            Game3DScene current;
            float cy, cp, cd, tx, ty, tz;
            synchronized (sceneLock) {
                current = scene;
                cy = yaw;
                cp = pitch;
                cd = distance;
                tx = targetX;
                ty = targetY;
                tz = targetZ;
            }
            camera.setTarget(tx, ty, tz).orbit(cy, cp, cd);
            renderer.drawScene(current, camera, cullBackFaces);

            long now = System.nanoTime();
            if (lastFrameNanos != 0L) {
                float dt = (now - lastFrameNanos) / 1_000_000_000f;
                if (dt > 1e-5f) {
                    smoothedFps = smoothedFps * 0.9f + (1f / dt) * 0.1f;
                }
            }
            lastFrameNanos = now;

            FrameListener listener = frameListener;
            if (listener != null) {
                int objects = renderer.lastDrawnObjects();
                int triangles = renderer.lastDrawnTriangles();
                float fps = smoothedFps;
                post(() -> listener.onFrame(objects, triangles, fps));
            }
        }
    }

    @Override
    public void onPause() {
        saveCameraToScene();
        super.onPause();
    }
}
