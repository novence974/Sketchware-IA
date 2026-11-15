package pro.sketchware.activities.game3d.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Wavefront {@code .obj} geometry.
 *
 * <p>Supports {@code v}, {@code vn}, {@code vt} and {@code f} with every index
 * form ({@code v}, {@code v/vt}, {@code v//vn}, {@code v/vt/vn}), negative
 * (relative) indices, and faces with more than three corners, which are
 * triangulated as a fan. Materials, groups and smoothing directives are
 * skipped rather than treated as errors — a model that uses them still
 * loads.</p>
 *
 * <p>OBJ indexes positions, normals and texture coordinates separately while
 * OpenGL needs one index per vertex, so each distinct combination becomes its
 * own vertex, de-duplicated through a map.</p>
 */
public final class ObjLoader {

    private ObjLoader() {
    }

    /** Thrown when the file holds no triangles we can draw. */
    public static final class ObjParseException extends IOException {
        public ObjParseException(String message) {
            super(message);
        }
    }

    public static Mesh load(InputStream stream) throws IOException {
        if (stream == null) {
            throw new ObjParseException("no input stream");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return load(reader);
        }
    }

    public static Mesh parse(String text) throws IOException {
        return load(new java.io.StringReader(text == null ? "" : text));
    }

    public static Mesh load(Reader reader) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();

        List<Float> outPositions = new ArrayList<>();
        List<Float> outNormals = new ArrayList<>();
        List<Float> outUvs = new ArrayList<>();
        List<Integer> outIndices = new ArrayList<>();
        Map<String, Integer> vertexCache = new HashMap<>();

        boolean sawNormals = false;
        BufferedReader in = new BufferedReader(reader);
        String line;
        int lineNumber = 0;

        while ((line = in.readLine()) != null) {
            lineNumber++;
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "v" -> {
                    if (parts.length < 4) {
                        throw new ObjParseException("line " + lineNumber + ": v needs 3 numbers");
                    }
                    positions.add(new float[]{
                            parseFloat(parts[1], lineNumber),
                            parseFloat(parts[2], lineNumber),
                            parseFloat(parts[3], lineNumber)});
                }
                case "vn" -> {
                    if (parts.length < 4) {
                        throw new ObjParseException("line " + lineNumber + ": vn needs 3 numbers");
                    }
                    normals.add(new float[]{
                            parseFloat(parts[1], lineNumber),
                            parseFloat(parts[2], lineNumber),
                            parseFloat(parts[3], lineNumber)});
                    sawNormals = true;
                }
                case "vt" -> {
                    if (parts.length < 3) {
                        throw new ObjParseException("line " + lineNumber + ": vt needs 2 numbers");
                    }
                    uvs.add(new float[]{
                            parseFloat(parts[1], lineNumber),
                            parseFloat(parts[2], lineNumber)});
                }
                case "f" -> {
                    if (parts.length < 4) {
                        throw new ObjParseException("line " + lineNumber + ": a face needs 3 corners");
                    }
                    int[] corner = new int[parts.length - 1];
                    for (int i = 1; i < parts.length; i++) {
                        corner[i - 1] = resolveVertex(parts[i], lineNumber,
                                positions, normals, uvs,
                                outPositions, outNormals, outUvs, vertexCache);
                    }
                    // Fan triangulation: correct for the convex faces OBJ
                    // exporters emit, and it keeps the original winding.
                    for (int i = 1; i + 1 < corner.length; i++) {
                        outIndices.add(corner[0]);
                        outIndices.add(corner[i]);
                        outIndices.add(corner[i + 1]);
                    }
                }
                default -> {
                    // mtllib, usemtl, o, g, s and anything else: ignored on
                    // purpose so an unsupported directive never fails a load.
                }
            }
        }

        if (outIndices.isEmpty()) {
            throw new ObjParseException("the file contains no faces");
        }

        float[] pos = toFloatArray(outPositions);
        float[] nrm = sawNormals ? toFloatArray(outNormals) : null;
        float[] uv = toFloatArray(outUvs);
        int[] idx = new int[outIndices.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = outIndices.get(i);
        }
        Mesh mesh = new Mesh(pos, nrm, uv, idx);
        if (!sawNormals) {
            mesh.recalculateNormals();
        }
        return mesh;
    }

    private static int resolveVertex(String token, int lineNumber,
                                     List<float[]> positions, List<float[]> normals, List<float[]> uvs,
                                     List<Float> outPositions, List<Float> outNormals, List<Float> outUvs,
                                     Map<String, Integer> cache) throws IOException {
        Integer cached = cache.get(token);
        if (cached != null) {
            return cached;
        }
        String[] bits = token.split("/", -1);
        int pi = resolveIndex(bits[0], positions.size(), lineNumber, "vertex");
        int ti = bits.length > 1 && !bits[1].isEmpty()
                ? resolveIndex(bits[1], uvs.size(), lineNumber, "texcoord") : -1;
        int ni = bits.length > 2 && !bits[2].isEmpty()
                ? resolveIndex(bits[2], normals.size(), lineNumber, "normal") : -1;

        float[] p = positions.get(pi);
        outPositions.add(p[0]);
        outPositions.add(p[1]);
        outPositions.add(p[2]);

        if (ni >= 0) {
            float[] n = normals.get(ni);
            outNormals.add(n[0]);
            outNormals.add(n[1]);
            outNormals.add(n[2]);
        } else {
            outNormals.add(0f);
            outNormals.add(0f);
            outNormals.add(0f);
        }

        if (ti >= 0) {
            float[] t = uvs.get(ti);
            outUvs.add(t[0]);
            outUvs.add(t[1]);
        } else {
            outUvs.add(0f);
            outUvs.add(0f);
        }

        int index = (outPositions.size() / 3) - 1;
        cache.put(token, index);
        return index;
    }

    /** OBJ indices are 1-based; a negative one counts back from the end. */
    private static int resolveIndex(String raw, int count, int lineNumber, String what) throws IOException {
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException bad) {
            throw new ObjParseException("line " + lineNumber + ": bad " + what + " index '" + raw + "'");
        }
        int resolved = value < 0 ? count + value : value - 1;
        if (resolved < 0 || resolved >= count) {
            throw new ObjParseException(
                    "line " + lineNumber + ": " + what + " index " + value + " is out of range (have " + count + ")");
        }
        return resolved;
    }

    private static float parseFloat(String raw, int lineNumber) throws IOException {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException bad) {
            throw new ObjParseException("line " + lineNumber + ": '" + raw + "' is not a number");
        }
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
