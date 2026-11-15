package pro.sketchware.activities.game3d.render;

/**
 * GLSL ES 1.00 sources for the engine's shading models.
 *
 * <p>Written against OpenGL ES 2.0 rather than 3.0 so the exported game runs
 * on every device the app supports. Each shading model is its own program:
 * branching on a mode uniform inside the fragment shader costs more on the
 * low-end GPUs this has to run on than binding a second program does.</p>
 *
 * <p>The light budget is one ambient term, one directional light and up to
 * {@link #MAX_POINT_LIGHTS} point lights — what a phone can shade per
 * fragment without dropping frames.</p>
 */
public final class ShaderLibrary {

    public static final int MAX_POINT_LIGHTS = 4;

    // Attributes
    public static final String A_POSITION = "aPosition";
    public static final String A_NORMAL = "aNormal";
    public static final String A_UV = "aUv";

    // Uniforms shared by every program
    public static final String U_MVP = "uMvp";
    public static final String U_MODEL = "uModel";
    public static final String U_NORMAL_MATRIX = "uNormalMatrix";
    public static final String U_BASE_COLOR = "uBaseColor";
    public static final String U_OPACITY = "uOpacity";
    public static final String U_EMISSIVE = "uEmissive";
    public static final String U_TEXTURE = "uTexture";
    public static final String U_HAS_TEXTURE = "uHasTexture";
    public static final String U_FOG_COLOR = "uFogColor";
    public static final String U_FOG_RANGE = "uFogRange";
    public static final String U_FOG_ENABLED = "uFogEnabled";
    public static final String U_CAMERA_POS = "uCameraPos";

    // Lighting uniforms (lambert and phong)
    public static final String U_AMBIENT = "uAmbient";
    public static final String U_DIR_LIGHT_DIR = "uDirLightDir";
    public static final String U_DIR_LIGHT_COLOR = "uDirLightColor";
    public static final String U_POINT_COUNT = "uPointCount";
    public static final String U_POINT_POS = "uPointPos";
    public static final String U_POINT_COLOR = "uPointColor";
    public static final String U_POINT_RANGE = "uPointRange";

    // Phong only
    public static final String U_SPECULAR = "uSpecular";
    public static final String U_SHININESS = "uShininess";

    private ShaderLibrary() {
    }

    /** Shared vertex stage: world position and normal for the fragment stage. */
    public static final String VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat4 uNormalMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aUv;
            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vUv;
            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vWorldPos = world.xyz;
                vNormal = (uNormalMatrix * vec4(aNormal, 0.0)).xyz;
                vUv = aUv;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
            """;

    private static final String FRAG_HEADER = """
            precision mediump float;
            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vUv;
            uniform vec3 uBaseColor;
            uniform float uOpacity;
            uniform float uEmissive;
            uniform sampler2D uTexture;
            uniform float uHasTexture;
            uniform vec3 uFogColor;
            uniform vec2 uFogRange;
            uniform float uFogEnabled;
            vec3 albedo() {
                vec3 c = uBaseColor;
                if (uHasTexture > 0.5) {
                    c *= texture2D(uTexture, vUv).rgb;
                }
                return c;
            }
            vec3 applyFog(vec3 color, float viewDistance) {
                if (uFogEnabled < 0.5) {
                    return color;
                }
                float f = clamp((viewDistance - uFogRange.x) / max(0.001, uFogRange.y - uFogRange.x), 0.0, 1.0);
                return mix(color, uFogColor, f);
            }
            """;

    private static final String FRAG_LIGHT_UNIFORMS = """
            uniform vec3 uCameraPos;
            uniform vec3 uAmbient;
            uniform vec3 uDirLightDir;
            uniform vec3 uDirLightColor;
            uniform int uPointCount;
            uniform vec3 uPointPos[4];
            uniform vec3 uPointColor[4];
            uniform float uPointRange[4];
            """;

    public static final String FRAGMENT_UNLIT = FRAG_HEADER + """
            uniform vec3 uCameraPos;
            void main() {
                vec3 color = albedo();
                float dist = length(uCameraPos - vWorldPos);
                gl_FragColor = vec4(applyFog(color, dist), uOpacity);
            }
            """;

    public static final String FRAGMENT_LAMBERT = FRAG_HEADER + FRAG_LIGHT_UNIFORMS + """
            void main() {
                vec3 n = normalize(vNormal);
                vec3 base = albedo();
                vec3 lit = uAmbient;
                lit += uDirLightColor * max(dot(n, -normalize(uDirLightDir)), 0.0);
                for (int i = 0; i < 4; i++) {
                    if (i >= uPointCount) { break; }
                    vec3 toLight = uPointPos[i] - vWorldPos;
                    float d = length(toLight);
                    float atten = clamp(1.0 - d / max(0.001, uPointRange[i]), 0.0, 1.0);
                    atten *= atten;
                    lit += uPointColor[i] * max(dot(n, normalize(toLight)), 0.0) * atten;
                }
                vec3 color = base * lit + base * uEmissive;
                float dist = length(uCameraPos - vWorldPos);
                gl_FragColor = vec4(applyFog(color, dist), uOpacity);
            }
            """;

    public static final String FRAGMENT_PHONG = FRAG_HEADER + FRAG_LIGHT_UNIFORMS + """
            uniform float uSpecular;
            uniform float uShininess;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 viewDir = normalize(uCameraPos - vWorldPos);
                vec3 base = albedo();
                vec3 lit = uAmbient;
                vec3 spec = vec3(0.0);

                vec3 l = -normalize(uDirLightDir);
                float ndl = max(dot(n, l), 0.0);
                lit += uDirLightColor * ndl;
                if (ndl > 0.0) {
                    vec3 h = normalize(l + viewDir);
                    spec += uDirLightColor * pow(max(dot(n, h), 0.0), uShininess) * uSpecular;
                }

                for (int i = 0; i < 4; i++) {
                    if (i >= uPointCount) { break; }
                    vec3 toLight = uPointPos[i] - vWorldPos;
                    float d = length(toLight);
                    float atten = clamp(1.0 - d / max(0.001, uPointRange[i]), 0.0, 1.0);
                    atten *= atten;
                    vec3 pl = normalize(toLight);
                    float pndl = max(dot(n, pl), 0.0);
                    lit += uPointColor[i] * pndl * atten;
                    if (pndl > 0.0) {
                        vec3 h = normalize(pl + viewDir);
                        spec += uPointColor[i] * pow(max(dot(n, h), 0.0), uShininess) * uSpecular * atten;
                    }
                }

                vec3 color = base * lit + spec + base * uEmissive;
                float dist = length(uCameraPos - vWorldPos);
                gl_FragColor = vec4(applyFog(color, dist), uOpacity);
            }
            """;

    /** Full-screen vertical gradient used for the sky. */
    public static final String SKY_VERTEX = """
            attribute vec2 aPosition;
            varying float vT;
            void main() {
                vT = aPosition.y * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.999, 1.0);
            }
            """;

    public static final String SKY_FRAGMENT = """
            precision mediump float;
            varying float vT;
            uniform vec3 uTopColor;
            uniform vec3 uBottomColor;
            void main() {
                gl_FragColor = vec4(mix(uBottomColor, uTopColor, vT), 1.0);
            }
            """;

    public static String fragmentFor(int shadingModel) {
        return switch (shadingModel) {
            case Material.SHADING_UNLIT -> FRAGMENT_UNLIT;
            case Material.SHADING_PHONG -> FRAGMENT_PHONG;
            default -> FRAGMENT_LAMBERT;
        };
    }

    /** True when the model reads the lighting uniforms at all. */
    public static boolean usesLighting(int shadingModel) {
        return shadingModel != Material.SHADING_UNLIT;
    }
}
