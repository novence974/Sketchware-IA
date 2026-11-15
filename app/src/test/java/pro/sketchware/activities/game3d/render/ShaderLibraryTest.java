package pro.sketchware.activities.game3d.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GLSL is compiled by the driver at run time, so a typo in a shader shows up
 * as a black screen on a device rather than a build failure. These checks
 * cover what can be established without a GL context: that the sources are
 * structurally whole, and that every uniform and attribute the renderer binds
 * by name actually exists in the shader that is supposed to have it.
 */
public class ShaderLibraryTest {

    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(uniform|attribute|varying)\\s+\\w+\\s+(\\w+)\\s*(\\[\\s*\\d+\\s*\\])?\\s*;");

    private static java.util.Set<String> declaredNames(String source) {
        java.util.Set<String> names = new java.util.HashSet<>();
        Matcher m = DECLARATION.matcher(source);
        while (m.find()) {
            names.add(m.group(2));
        }
        return names;
    }

    private static void assertBalanced(String what, String source) {
        int braces = 0;
        int parens = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') braces++;
            else if (c == '}') braces--;
            else if (c == '(') parens++;
            else if (c == ')') parens--;
            assertTrue(what + ": a closing brace precedes its opener", braces >= 0);
        }
        assertEquals(what + ": unbalanced braces", 0, braces);
        assertEquals(what + ": unbalanced parentheses", 0, parens);
    }

    private static void assertShaderIsWellFormed(String what, String source) {
        assertFalse(what + " must not be empty", source.trim().isEmpty());
        assertTrue(what + " needs a main()", source.contains("void main()"));
        assertBalanced(what, source);
        assertFalse(what + " must not use GLSL ES 3.00 syntax on an ES 2.0 shader",
                source.contains("#version 300") || source.contains(" in ") || source.contains(" out "));
        // Every statement line should end in ; { } or be a directive/comment.
        for (String raw : source.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
            char last = line.charAt(line.length() - 1);
            assertTrue(what + ": suspicious line '" + line + "'",
                    last == ';' || last == '{' || last == '}' || last == ',');
        }
    }

    @Test
    public void everyShaderSourceIsWellFormed() {
        assertShaderIsWellFormed("vertex", ShaderLibrary.VERTEX);
        assertShaderIsWellFormed("unlit", ShaderLibrary.FRAGMENT_UNLIT);
        assertShaderIsWellFormed("lambert", ShaderLibrary.FRAGMENT_LAMBERT);
        assertShaderIsWellFormed("phong", ShaderLibrary.FRAGMENT_PHONG);
        assertShaderIsWellFormed("sky vertex", ShaderLibrary.SKY_VERTEX);
        assertShaderIsWellFormed("sky fragment", ShaderLibrary.SKY_FRAGMENT);
    }

    @Test
    public void fragmentShadersDeclareAPrecision() {
        // Without a default precision an ES 2.0 fragment shader fails to
        // compile on many drivers, and only on a device.
        for (String src : new String[]{
                ShaderLibrary.FRAGMENT_UNLIT, ShaderLibrary.FRAGMENT_LAMBERT,
                ShaderLibrary.FRAGMENT_PHONG, ShaderLibrary.SKY_FRAGMENT}) {
            assertTrue("a fragment shader must set a default float precision",
                    src.contains("precision mediump float;") || src.contains("precision highp float;"));
        }
    }

    @Test
    public void theVertexShaderDeclaresEveryAttributeAndMatrixTheRendererBinds() {
        var declared = declaredNames(ShaderLibrary.VERTEX);
        for (String name : new String[]{
                ShaderLibrary.A_POSITION, ShaderLibrary.A_NORMAL, ShaderLibrary.A_UV,
                ShaderLibrary.U_MVP, ShaderLibrary.U_MODEL, ShaderLibrary.U_NORMAL_MATRIX}) {
            assertTrue("the vertex shader is missing " + name, declared.contains(name));
        }
    }

    @Test
    public void everyFragmentShaderDeclaresTheCommonMaterialUniforms() {
        String[] common = {
                ShaderLibrary.U_BASE_COLOR, ShaderLibrary.U_OPACITY,
                ShaderLibrary.U_TEXTURE, ShaderLibrary.U_HAS_TEXTURE,
                ShaderLibrary.U_FOG_COLOR, ShaderLibrary.U_FOG_RANGE,
                ShaderLibrary.U_FOG_ENABLED, ShaderLibrary.U_CAMERA_POS};
        for (int model : new int[]{
                Material.SHADING_UNLIT, Material.SHADING_LAMBERT, Material.SHADING_PHONG}) {
            var declared = declaredNames(ShaderLibrary.fragmentFor(model));
            for (String name : common) {
                assertTrue("shading model " + model + " is missing " + name, declared.contains(name));
            }
        }
    }

    @Test
    public void litShadersDeclareTheLightingUniforms() {
        String[] lighting = {
                ShaderLibrary.U_AMBIENT, ShaderLibrary.U_DIR_LIGHT_DIR,
                ShaderLibrary.U_DIR_LIGHT_COLOR, ShaderLibrary.U_POINT_COUNT,
                ShaderLibrary.U_POINT_POS, ShaderLibrary.U_POINT_COLOR,
                ShaderLibrary.U_POINT_RANGE};
        for (int model : new int[]{Material.SHADING_LAMBERT, Material.SHADING_PHONG}) {
            var declared = declaredNames(ShaderLibrary.fragmentFor(model));
            for (String name : lighting) {
                assertTrue("shading model " + model + " is missing " + name, declared.contains(name));
            }
            assertTrue(ShaderLibrary.usesLighting(model));
        }
        assertFalse(ShaderLibrary.usesLighting(Material.SHADING_UNLIT));
    }

    @Test
    public void phongDeclaresItsSpecularUniforms() {
        var declared = declaredNames(ShaderLibrary.FRAGMENT_PHONG);
        assertTrue(declared.contains(ShaderLibrary.U_SPECULAR));
        assertTrue(declared.contains(ShaderLibrary.U_SHININESS));
        var lambert = declaredNames(ShaderLibrary.FRAGMENT_LAMBERT);
        assertFalse("lambert must not carry unused specular uniforms",
                lambert.contains(ShaderLibrary.U_SHININESS));
    }

    @Test
    public void varyingsMatchBetweenTheVertexAndFragmentStages() {
        var vertexVaryings = varyings(ShaderLibrary.VERTEX);
        for (int model : new int[]{
                Material.SHADING_UNLIT, Material.SHADING_LAMBERT, Material.SHADING_PHONG}) {
            var fragVaryings = varyings(ShaderLibrary.fragmentFor(model));
            assertTrue("a fragment shader reads varyings the vertex stage never writes: "
                            + fragVaryings + " vs " + vertexVaryings,
                    vertexVaryings.containsAll(fragVaryings));
        }
    }

    private static java.util.Set<String> varyings(String source) {
        java.util.Set<String> names = new java.util.HashSet<>();
        Matcher m = Pattern.compile("\\bvarying\\s+\\w+\\s+(\\w+)\\s*;").matcher(source);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    @Test
    public void pointLightLoopsStayWithinTheDeclaredArraySize() {
        // GLSL ES 1.00 requires a constant loop bound, so the literal in the
        // shader and MAX_POINT_LIGHTS must not drift apart.
        for (int model : new int[]{Material.SHADING_LAMBERT, Material.SHADING_PHONG}) {
            String src = ShaderLibrary.fragmentFor(model);
            assertTrue("the point-light array must be sized " + ShaderLibrary.MAX_POINT_LIGHTS,
                    src.contains("uPointPos[" + ShaderLibrary.MAX_POINT_LIGHTS + "]"));
            assertTrue("the loop bound must match the array size",
                    src.contains("i < " + ShaderLibrary.MAX_POINT_LIGHTS + ";"));
        }
    }

    @Test
    public void fragmentForFallsBackToLambert() {
        assertEquals(ShaderLibrary.FRAGMENT_LAMBERT, ShaderLibrary.fragmentFor(999));
        assertEquals(ShaderLibrary.FRAGMENT_UNLIT, ShaderLibrary.fragmentFor(Material.SHADING_UNLIT));
        assertEquals(ShaderLibrary.FRAGMENT_PHONG, ShaderLibrary.fragmentFor(Material.SHADING_PHONG));
    }
}
