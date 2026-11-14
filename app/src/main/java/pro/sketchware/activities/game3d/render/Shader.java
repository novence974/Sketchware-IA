package pro.sketchware.activities.game3d.render;

import android.opengl.GLES20;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * A compiled and linked GL program, with its uniform and attribute locations
 * cached.
 *
 * <p>{@code glGetUniformLocation} is a driver round trip; looking a name up
 * once per frame per object is a measurable cost on mobile, so every location
 * is resolved on demand and remembered. A name the program does not declare
 * resolves to -1, which every GL setter ignores, so a stale uniform name is
 * harmless rather than fatal.</p>
 */
public final class Shader {

    private static final String TAG = "Game3D.Shader";

    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final Map<String, Integer> attributes = new HashMap<>();
    private boolean released;

    private Shader(int program) {
        this.program = program;
    }

    /** Compiles and links, or returns null after logging why it failed. */
    public static Shader compile(String vertexSource, String fragmentSource) {
        int vs = compileStage(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vs == 0) {
            return null;
        }
        int fs = compileStage(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fs == 0) {
            GLES20.glDeleteShader(vs);
            return null;
        }
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            Log.e(TAG, "glCreateProgram returned 0");
            return null;
        }
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        // The shaders are no longer needed once the program is linked.
        GLES20.glDetachShader(program, vs);
        GLES20.glDetachShader(program, fs);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);

        if (status[0] == 0) {
            Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return null;
        }
        return new Shader(program);
    }

    private static int compileStage(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            Log.e(TAG, "glCreateShader returned 0");
            return 0;
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, (type == GLES20.GL_VERTEX_SHADER ? "vertex" : "fragment")
                    + " compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    public void use() {
        GLES20.glUseProgram(program);
    }

    public int uniform(String name) {
        Integer cached = uniforms.get(name);
        if (cached == null) {
            cached = GLES20.glGetUniformLocation(program, name);
            uniforms.put(name, cached);
        }
        return cached;
    }

    public int attribute(String name) {
        Integer cached = attributes.get(name);
        if (cached == null) {
            cached = GLES20.glGetAttribLocation(program, name);
            attributes.put(name, cached);
        }
        return cached;
    }

    public void setMatrix(String name, float[] columnMajor16) {
        int loc = uniform(name);
        if (loc >= 0) {
            GLES20.glUniformMatrix4fv(loc, 1, false, columnMajor16, 0);
        }
    }

    public void setFloat(String name, float value) {
        int loc = uniform(name);
        if (loc >= 0) {
            GLES20.glUniform1f(loc, value);
        }
    }

    public void setInt(String name, int value) {
        int loc = uniform(name);
        if (loc >= 0) {
            GLES20.glUniform1i(loc, value);
        }
    }

    public void setVec2(String name, float x, float y) {
        int loc = uniform(name);
        if (loc >= 0) {
            GLES20.glUniform2f(loc, x, y);
        }
    }

    public void setVec3(String name, float x, float y, float z) {
        int loc = uniform(name);
        if (loc >= 0) {
            GLES20.glUniform3f(loc, x, y, z);
        }
    }

    public void setVec3Array(String name, float[] xyzTriples, int count) {
        int loc = uniform(name);
        if (loc >= 0 && count > 0) {
            GLES20.glUniform3fv(loc, count, xyzTriples, 0);
        }
    }

    public void setFloatArray(String name, float[] values, int count) {
        int loc = uniform(name);
        if (loc >= 0 && count > 0) {
            GLES20.glUniform1fv(loc, count, values, 0);
        }
    }

    public void release() {
        if (!released && program != 0) {
            GLES20.glDeleteProgram(program);
            released = true;
        }
    }

    public boolean isReleased() {
        return released;
    }
}
