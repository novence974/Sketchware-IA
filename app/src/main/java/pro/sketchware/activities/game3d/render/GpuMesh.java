package pro.sketchware.activities.game3d.render;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * A {@link Mesh} uploaded to GPU buffers.
 *
 * <p>Indices go up as unsigned shorts whenever they fit, which keeps the draw
 * call OpenGL ES 2.0 compatible; a mesh past 65536 vertices falls back to
 * unsigned ints, which needs ES 3.0 and is reported by {@link #usesIntIndices}
 * so the caller can decide.</p>
 */
public final class GpuMesh {

    private final int[] buffers = new int[4];
    private final int indexCount;
    private final boolean intIndices;
    private boolean released;

    private GpuMesh(int[] handles, int indexCount, boolean intIndices) {
        System.arraycopy(handles, 0, this.buffers, 0, handles.length);
        this.indexCount = indexCount;
        this.intIndices = intIndices;
    }

    public static GpuMesh upload(Mesh mesh) {
        int[] handles = new int[4];
        GLES20.glGenBuffers(4, handles, 0);

        uploadFloats(handles[0], mesh.positions);
        uploadFloats(handles[1], mesh.normals);
        uploadFloats(handles[2], mesh.uvs);

        boolean useInt = !mesh.fitsShortIndices();
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, handles[3]);
        if (useInt) {
            IntBuffer ib = ByteBuffer.allocateDirect(mesh.indices.length * 4)
                    .order(ByteOrder.nativeOrder()).asIntBuffer();
            ib.put(mesh.indices).position(0);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    mesh.indices.length * 4, ib, GLES20.GL_STATIC_DRAW);
        } else {
            ShortBuffer sb = ByteBuffer.allocateDirect(mesh.indices.length * 2)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            for (int index : mesh.indices) {
                sb.put((short) index);
            }
            sb.position(0);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    mesh.indices.length * 2, sb, GLES20.GL_STATIC_DRAW);
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return new GpuMesh(handles, mesh.indices.length, useInt);
    }

    private static void uploadFloats(int handle, float[] data) {
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, handle);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
    }

    /** Binds the vertex attributes a shader declares; -1 locations are skipped. */
    public void bind(int positionLoc, int normalLoc, int uvLoc) {
        bindAttribute(buffers[0], positionLoc, 3);
        bindAttribute(buffers[1], normalLoc, 3);
        bindAttribute(buffers[2], uvLoc, 2);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[3]);
    }

    private static void bindAttribute(int buffer, int location, int components) {
        if (location < 0) {
            return;
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer);
        GLES20.glEnableVertexAttribArray(location);
        GLES20.glVertexAttribPointer(location, components, GLES20.GL_FLOAT, false, 0, 0);
    }

    public void draw() {
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount,
                intIndices ? GLES20.GL_UNSIGNED_INT : GLES20.GL_UNSIGNED_SHORT, 0);
    }

    public void unbind(int positionLoc, int normalLoc, int uvLoc) {
        if (positionLoc >= 0) GLES20.glDisableVertexAttribArray(positionLoc);
        if (normalLoc >= 0) GLES20.glDisableVertexAttribArray(normalLoc);
        if (uvLoc >= 0) GLES20.glDisableVertexAttribArray(uvLoc);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public boolean usesIntIndices() {
        return intIndices;
    }

    public int indexCount() {
        return indexCount;
    }

    public void release() {
        if (!released) {
            GLES20.glDeleteBuffers(4, buffers, 0);
            released = true;
        }
    }
}
