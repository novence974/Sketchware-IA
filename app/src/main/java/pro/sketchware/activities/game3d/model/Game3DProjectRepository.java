package pro.sketchware.activities.game3d.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import pro.sketchware.workspace.WorkspacePathManager;

/**
 * Persistence for {@link Game3DProject}, alongside the 2D engine's own store.
 *
 * <p>Every read path falls back to a usable project rather than throwing: a
 * truncated or hand-edited file must not leave the editor with nothing to
 * open. The write is done through a temporary file and renamed, so a crash
 * mid-save cannot leave a half-written project behind.</p>
 */
public final class Game3DProjectRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "game3d_project.json";

    private Game3DProjectRepository() {
    }

    public static File getGame3DDir(String scId) {
        return new File(WorkspacePathManager.b(safeScId(scId)), "game3d");
    }

    public static File getProjectFile(String scId) {
        return new File(getGame3DDir(scId), FILE_NAME);
    }

    public static Game3DProject loadOrCreate(String scId) {
        String safeId = safeScId(scId);
        File file = getProjectFile(safeId);
        if (file.exists() && file.isFile() && file.length() > 0) {
            try {
                String json = readUtf8File(file);
                if (json != null && !json.trim().isEmpty()) {
                    Game3DProject parsed = fromJson(json, safeId);
                    if (parsed != null) {
                        return parsed;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through and start fresh rather than refusing to open.
            }
        }
        Game3DProject created = Game3DProject.createDefault(safeId);
        save(safeId, created);
        return created;
    }

    public static boolean exists(String scId) {
        File file = getProjectFile(safeScId(scId));
        return file.exists() && file.isFile() && file.length() > 0;
    }

    public static void save(String scId, Game3DProject project) {
        if (project == null) {
            return;
        }
        String safeId = safeScId(scId);
        project.ensureInitialized(safeId);
        project.scId = safeId;

        File dir = getGame3DDir(safeId);
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        writeUtf8FileAtomically(getProjectFile(safeId), toJson(project));
    }

    public static String toJson(Game3DProject project) {
        if (project == null) {
            return "{}";
        }
        return GSON.toJson(project);
    }

    public static Game3DProject fromJson(String json, String scId) {
        String safeId = safeScId(scId);
        if (json == null || json.trim().isEmpty()) {
            return Game3DProject.createDefault(safeId);
        }
        try {
            Game3DProject parsed = GSON.fromJson(json, Game3DProject.class);
            if (parsed == null) {
                return Game3DProject.createDefault(safeId);
            }
            parsed.ensureInitialized(safeId);
            return parsed;
        } catch (Throwable ignored) {
            return Game3DProject.createDefault(safeId);
        }
    }

    /** Deletes the stored project; the next open starts from the default. */
    public static boolean delete(String scId) {
        File file = getProjectFile(safeScId(scId));
        return !file.exists() || file.delete();
    }

    private static String safeScId(String scId) {
        return scId == null ? "" : scId.trim();
    }

    private static String readUtf8File(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(64, file.length()));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * Writes beside the target and renames over it. A direct write that is
     * interrupted leaves a truncated file, and the project would open empty
     * on the next launch.
     */
    private static void writeUtf8FileAtomically(File file, String content) {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (Throwable failed) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            // Some filesystems refuse a rename onto an existing file.
            if (file.delete() && temp.renameTo(file)) {
                return;
            }
            temp.delete();
        }
    }
}
