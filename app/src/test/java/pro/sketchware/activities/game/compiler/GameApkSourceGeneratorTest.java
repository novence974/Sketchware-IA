package pro.sketchware.activities.game.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The exported game is emitted as Java source held in text blocks, so the
 * compiler never sees it: a broken template only shows up when a user builds
 * an APK. These checks assert the structure of what we emit.
 */
public class GameApkSourceGeneratorTest {

    private static String source() {
        return GameApkSourceGenerator.buildMainActivitySource("com.example.game", false);
    }

    /** Strips string and char literals and comments before counting braces. */
    private static String stripLiteralsAndComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i += 2;
            } else if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n) {
                    char d = src.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (d == quote) break;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    public void generatedSourceIsStructurallyBalanced() {
        String code = stripLiteralsAndComments(source());
        int depth = 0;
        int lowest = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                lowest = Math.min(lowest, depth);
            }
        }
        assertEquals("braces must balance across every template part", 0, depth);
        assertTrue("a '}' must never precede its '{'", lowest >= 0);

        int parens = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') parens++;
            else if (c == ')') parens--;
        }
        assertEquals("parentheses must balance", 0, parens);
    }

    @Test
    public void generatedSourceCarriesThePackageHeader() {
        assertTrue(source().startsWith("package com.example.game;"));
        assertFalse("the landscape placeholder must be substituted",
                source().contains("__DEFAULT_LANDSCAPE__"));
    }

    @Test
    public void generatedSourceHasNoUnsubstitutedPlaceholders() {
        String src = source();
        int at = src.indexOf("__");
        while (at >= 0) {
            int end = src.indexOf("__", at + 2);
            if (end < 0) break;
            String token = src.substring(at, end + 2);
            // "__next__", "__prev__" and "__scene__" are real runtime tokens.
            assertTrue("unsubstituted template placeholder: " + token,
                    token.equals("__next__") || token.equals("__prev__")
                            || token.equals("__scene__")
                            || token.contains(" ") || token.contains("\n"));
            at = src.indexOf("__", end + 2);
        }
    }

    /**
     * The exported runtime is a hand-maintained mirror of GameRuntimeView, so
     * a field added to the editor is easy to forget here — and the symptom is
     * a property that works in preview and silently does nothing in the APK.
     */
    @Test
    public void generatedRuntimeKnowsTheExpandedObjectFields() {
        String src = source();
        for (String field : new String[]{
                "flipX", "flipY", "pivotX", "pivotY",
                "colliderOffsetX", "colliderOffsetY", "colliderScaleX", "colliderScaleY",
                "collisionLayer", "collisionMask",
                "mass", "linearDamping", "maxSpeed",
                "maxHealth", "health", "invincibleSec",
                "parentId", "customProps"}) {
            assertTrue("the generated ObjectData is missing " + field,
                    src.contains(" " + field + " ="));
            assertTrue("the generated fromJson does not read " + field,
                    src.contains("\"" + field + "\""));
            assertTrue("the generated copy() drops " + field,
                    src.contains("c." + field + " = " + field + ";"));
        }
    }

    @Test
    public void generatedRuntimeUsesTheExpandedFieldsRatherThanJustStoringThem() {
        String src = source();
        assertTrue("collision must honour the layer masks",
                src.contains("collidesWith(other.bean)") || src.contains("collidesWith(b.bean)"));
        assertTrue("collision must resolve against the collider, not the bounds",
                src.contains("colliderLeft()") && src.contains("colliderTop()"));
        assertTrue("drag must be applied", src.contains("linearDamping > 0f"));
        assertTrue("the speed ceiling must be applied", src.contains("maxSpeed > 0f"));
        assertTrue("the pivot must drive the draw transform",
                src.contains("b.pivotX") && src.contains("b.pivotY"));
        assertTrue("flip must drive the draw scale",
                src.contains("b.flipX ? -b.scaleX") && src.contains("b.flipY ? -b.scaleY"));
        assertTrue("hazards must spend health",
                src.contains("\"hazard\".equals(oType)") && src.contains("applyDamage"));
        assertTrue("health must reach the script variables", src.contains("publishHealth"));
    }

    @Test
    public void generatedRuntimeStillContainsItsEntryPoints() {
        String src = source();
        assertTrue(src.contains("public class MainActivity extends Activity"));
        assertTrue(src.contains("protected void onCreate(Bundle savedInstanceState)"));
        assertTrue(src.contains("class GameSurfaceView extends SurfaceView"));
    }
}
