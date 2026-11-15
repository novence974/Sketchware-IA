package pro.sketchware.activities.game3d.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.activities.game3d.model.Game3DObjectBean;
import pro.sketchware.activities.game3d.model.Game3DProject;
import pro.sketchware.activities.game3d.model.Game3DProjectRepository;
import pro.sketchware.activities.game3d.model.Game3DScene;
import pro.sketchware.activities.game3d.render.Material;
import pro.sketchware.activities.game3d.runtime.Game3DSurfaceView;
import pro.sketchware.databinding.ActivityGame3dEditorBinding;

/**
 * The 3D scene editor: a viewport plus a panel for adding objects, walking
 * the hierarchy and editing the selected object.
 *
 * <p>Undo keeps whole-scene JSON snapshots rather than per-field deltas. A
 * scene is small (kilobytes) and a snapshot cannot drift out of step with the
 * model the way a hand-written inverse operation can.</p>
 */
public class Game3DEditorActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private static final int TAB_OBJECTS = 0;
    private static final int TAB_HIERARCHY = 1;
    private static final int TAB_PROPERTIES = 2;
    private static final int UNDO_LIMIT = 40;

    private ActivityGame3dEditorBinding binding;
    private Game3DSurfaceView viewport;

    private String scId = "";
    private Game3DProject project;
    private Game3DScene scene;
    private Game3DObjectBean selected;

    private int activeTab = TAB_OBJECTS;
    private boolean dirty;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    private int colorOnSurface;
    private int colorOnSurfaceVariant;

    public static Intent intentFor(Context context, String scId) {
        Intent intent = new Intent(context, Game3DEditorActivity.class);
        intent.putExtra(EXTRA_SC_ID, scId);
        return intent;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityGame3dEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        colorOnSurface = MaterialColors.getColor(this, R.attr.colorOnSurface, 0xFFE2E8F0);
        colorOnSurfaceVariant = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant, 0xFF94A3B8);

        scId = getIntent() != null ? getIntent().getStringExtra(EXTRA_SC_ID) : "";
        if (scId == null) scId = "";

        project = Game3DProjectRepository.loadOrCreate(scId);
        scene = project.startScene();

        setUpViewport();
        setUpToolbar();
        setUpTabs();
        rebuildPanel();
        updateTitle();
    }

    private void setUpViewport() {
        viewport = new Game3DSurfaceView(this);
        binding.viewportContainer.addView(viewport, 0, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        viewport.setScene(scene);
        viewport.setCullBackFaces(project.backFaceCulling);
        viewport.setFrameListener((objects, triangles, fps) ->
                binding.stats.setText(String.format(Locale.US, "%d obj · %d tri · %.0f fps",
                        objects, triangles, fps)));
        binding.btnFrameScene.setOnClickListener(v -> viewport.frameScene());
    }

    private void setUpToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> confirmExit());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
        MenuItem culling = binding.toolbar.getMenu().findItem(R.id.action_toggle_culling);
        if (culling != null) {
            culling.setChecked(project.backFaceCulling);
        }
    }

    private void setUpTabs() {
        TabLayout tabs = binding.panelTabs;
        tabs.addTab(tabs.newTab().setText(R.string.game3d_tab_objects));
        tabs.addTab(tabs.newTab().setText(R.string.game3d_tab_hierarchy));
        tabs.addTab(tabs.newTab().setText(R.string.game3d_tab_properties));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                activeTab = tab.getPosition();
                rebuildPanel();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private boolean onMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_undo) {
            undo();
            return true;
        }
        if (id == R.id.action_redo) {
            redo();
            return true;
        }
        if (id == R.id.action_save) {
            save(true);
            return true;
        }
        if (id == R.id.action_scenes) {
            showScenePicker();
            return true;
        }
        if (id == R.id.action_scene_settings) {
            showSceneSettings();
            return true;
        }
        if (id == R.id.action_toggle_culling) {
            item.setChecked(!item.isChecked());
            project.backFaceCulling = item.isChecked();
            viewport.setCullBackFaces(project.backFaceCulling);
            dirty = true;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Undo
    // ------------------------------------------------------------------

    private void pushUndo() {
        undoStack.push(Game3DProjectRepository.toJson(project));
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
        dirty = true;
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            toast(R.string.game3d_nothing_to_undo);
            return;
        }
        redoStack.push(Game3DProjectRepository.toJson(project));
        restore(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            toast(R.string.game3d_nothing_to_redo);
            return;
        }
        undoStack.push(Game3DProjectRepository.toJson(project));
        restore(redoStack.pop());
    }

    private void restore(String json) {
        String keepSceneId = scene != null ? scene.id : null;
        String keepSelectedId = selected != null ? selected.id : null;
        project = Game3DProjectRepository.fromJson(json, scId);
        scene = keepSceneId != null ? project.findScene(keepSceneId) : null;
        if (scene == null) {
            scene = project.startScene();
        }
        selected = keepSelectedId != null && scene != null ? scene.findById(keepSelectedId) : null;
        viewport.setScene(scene);
        rebuildPanel();
        updateTitle();
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Panel
    // ------------------------------------------------------------------

    private void rebuildPanel() {
        LinearLayout content = binding.panelContent;
        content.removeAllViews();
        switch (activeTab) {
            case TAB_HIERARCHY -> buildHierarchy(content);
            case TAB_PROPERTIES -> buildProperties(content);
            default -> buildPalette(content);
        }
    }

    private void buildPalette(LinearLayout content) {
        addSectionHeader(content, getString(R.string.game3d_section_primitives));
        String[][] primitives = {
                {Game3DObjectBean.MESH_CUBE, getString(R.string.game3d_mesh_cube)},
                {Game3DObjectBean.MESH_SPHERE, getString(R.string.game3d_mesh_sphere)},
                {Game3DObjectBean.MESH_PLANE, getString(R.string.game3d_mesh_plane)},
                {Game3DObjectBean.MESH_CYLINDER, getString(R.string.game3d_mesh_cylinder)},
                {Game3DObjectBean.MESH_CONE, getString(R.string.game3d_mesh_cone)},
                {Game3DObjectBean.MESH_TORUS, getString(R.string.game3d_mesh_torus)},
                {Game3DObjectBean.MESH_QUAD, getString(R.string.game3d_mesh_quad)},
        };
        for (String[] entry : primitives) {
            addPanelButton(content, entry[1], v -> addObject(
                    Game3DObjectBean.createDefault(entry[0], nextIndex())));
        }

        addSectionHeader(content, getString(R.string.game3d_section_scene_objects));
        addPanelButton(content, getString(R.string.game3d_add_directional_light),
                v -> addObject(Game3DObjectBean.createLight(0, nextIndex())));
        addPanelButton(content, getString(R.string.game3d_add_point_light),
                v -> addObject(Game3DObjectBean.createLight(1, nextIndex())));
        addPanelButton(content, getString(R.string.game3d_add_camera),
                v -> addObject(Game3DObjectBean.createCamera(nextIndex())));
        addPanelButton(content, getString(R.string.game3d_add_empty),
                v -> addObject(Game3DObjectBean.createDefault(Game3DObjectBean.MESH_NONE, nextIndex())));
    }

    private void buildHierarchy(LinearLayout content) {
        if (scene == null || scene.objects.isEmpty()) {
            addHint(content, getString(R.string.game3d_hierarchy_empty));
            return;
        }
        addSectionHeader(content, getString(R.string.game3d_tab_hierarchy)
                + " (" + scene.objects.size() + ")");
        List<Game3DObjectBean> snapshot = new ArrayList<>(scene.objects);
        for (Game3DObjectBean obj : snapshot) {
            if (obj == null) continue;
            String label = obj.name + "  ·  " + kindLabel(obj);
            MaterialButton row = addPanelButton(content, label, v -> {
                selected = obj;
                binding.panelTabs.selectTab(binding.panelTabs.getTabAt(TAB_PROPERTIES));
            });
            if (obj == selected) {
                row.setTypeface(null, Typeface.BOLD);
            }
            if (!obj.visible) {
                row.setAlpha(0.5f);
            }
        }
    }

    private String kindLabel(Game3DObjectBean obj) {
        return switch (obj.kind) {
            case Game3DObjectBean.KIND_LIGHT ->
                    getString(obj.lightType == 0
                            ? R.string.game3d_add_directional_light
                            : R.string.game3d_add_point_light);
            case Game3DObjectBean.KIND_CAMERA -> getString(R.string.game3d_add_camera);
            case Game3DObjectBean.KIND_EMPTY -> getString(R.string.game3d_add_empty);
            default -> Game3DObjectBean.displayNameForMesh(obj.meshType);
        };
    }

    private void buildProperties(LinearLayout content) {
        if (selected == null) {
            addHint(content, getString(R.string.game3d_no_selection));
            return;
        }
        addSectionHeader(content, selected.name);

        addPanelButton(content, getString(R.string.game3d_prop_name) + ": " + selected.name,
                v -> promptText(getString(R.string.game3d_prop_name), selected.name, value -> {
                    if (!value.trim().isEmpty()) {
                        pushUndo();
                        selected.name = value.trim();
                        afterChange();
                    }
                }));

        addPanelButton(content, String.format(Locale.US, "%s: %.2f, %.2f, %.2f",
                        getString(R.string.game3d_prop_position), selected.x, selected.y, selected.z),
                v -> promptVector(getString(R.string.game3d_prop_position),
                        selected.x, selected.y, selected.z, (a, b, c) -> {
                            pushUndo();
                            selected.x = a;
                            selected.y = b;
                            selected.z = c;
                            afterChange();
                        }));

        addPanelButton(content, String.format(Locale.US, "%s: %.0f°, %.0f°, %.0f°",
                        getString(R.string.game3d_prop_rotation),
                        selected.rotationX, selected.rotationY, selected.rotationZ),
                v -> promptVector(getString(R.string.game3d_prop_rotation),
                        selected.rotationX, selected.rotationY, selected.rotationZ, (a, b, c) -> {
                            pushUndo();
                            selected.rotationX = a;
                            selected.rotationY = b;
                            selected.rotationZ = c;
                            afterChange();
                        }));

        addPanelButton(content, String.format(Locale.US, "%s: %.2f, %.2f, %.2f",
                        getString(R.string.game3d_prop_scale), selected.scaleX, selected.scaleY, selected.scaleZ),
                v -> promptVector(getString(R.string.game3d_prop_scale),
                        selected.scaleX, selected.scaleY, selected.scaleZ, (a, b, c) -> {
                            pushUndo();
                            // A zero scale hides the object and makes its
                            // normal matrix singular, so it is refused here.
                            selected.scaleX = a == 0f ? 1f : a;
                            selected.scaleY = b == 0f ? 1f : b;
                            selected.scaleZ = c == 0f ? 1f : c;
                            afterChange();
                        }));

        if (Game3DObjectBean.KIND_MESH.equals(selected.kind)) {
            addPanelButton(content, getString(R.string.game3d_prop_color)
                            + String.format(Locale.US, ": #%06X", selected.color & 0xFFFFFF),
                    v -> promptColor());
            addPanelButton(content, getString(R.string.game3d_prop_shading) + ": " + shadingLabel(),
                    v -> promptShading());
            addPanelButton(content, String.format(Locale.US, "%s: %.2f",
                            getString(R.string.game3d_prop_opacity), selected.opacity),
                    v -> promptFloat(getString(R.string.game3d_prop_opacity), selected.opacity, value -> {
                        pushUndo();
                        selected.opacity = Math.max(0.05f, Math.min(1f, value));
                        afterChange();
                    }));
            addToggle(content, getString(R.string.game3d_prop_double_sided), selected.doubleSided, v -> {
                pushUndo();
                selected.doubleSided = !selected.doubleSided;
                afterChange();
            });
        }

        if (Game3DObjectBean.KIND_LIGHT.equals(selected.kind)) {
            addPanelButton(content, String.format(Locale.US, "%s: %.2f",
                            getString(R.string.game3d_prop_intensity), selected.lightIntensity),
                    v -> promptFloat(getString(R.string.game3d_prop_intensity), selected.lightIntensity, value -> {
                        pushUndo();
                        selected.lightIntensity = Math.max(0f, value);
                        afterChange();
                    }));
            if (selected.lightType == 1) {
                addPanelButton(content, String.format(Locale.US, "%s: %.1f",
                                getString(R.string.game3d_prop_range), selected.lightRange),
                        v -> promptFloat(getString(R.string.game3d_prop_range), selected.lightRange, value -> {
                            pushUndo();
                            selected.lightRange = Math.max(0.1f, value);
                            afterChange();
                        }));
            }
            addPanelButton(content, getString(R.string.game3d_prop_color)
                    + String.format(Locale.US, ": #%06X", selected.color & 0xFFFFFF), v -> promptColor());
        }

        addToggle(content, getString(R.string.game3d_prop_visible), selected.visible, v -> {
            pushUndo();
            selected.visible = !selected.visible;
            afterChange();
        });

        addPanelButton(content, getString(R.string.game3d_action_duplicate), v -> duplicateSelected());
        MaterialButton delete = addPanelButton(content, getString(R.string.game3d_action_delete),
                v -> confirmDeleteSelected());
        delete.setTextColor(MaterialColors.getColor(this, R.attr.colorError, 0xFFEF4444));
    }

    private String shadingLabel() {
        return switch (selected.shading) {
            case Material.SHADING_UNLIT -> getString(R.string.game3d_shading_unlit);
            case Material.SHADING_PHONG -> getString(R.string.game3d_shading_phong);
            default -> getString(R.string.game3d_shading_lambert);
        };
    }

    // ------------------------------------------------------------------
    // Edits
    // ------------------------------------------------------------------

    private int nextIndex() {
        return scene == null ? 1 : scene.objects.size() + 1;
    }

    private void addObject(Game3DObjectBean obj) {
        if (scene == null) {
            return;
        }
        pushUndo();
        scene.objects.add(obj);
        selected = obj;
        afterChange();
        binding.panelTabs.selectTab(binding.panelTabs.getTabAt(TAB_PROPERTIES));
    }

    private void duplicateSelected() {
        if (scene == null || selected == null) {
            return;
        }
        pushUndo();
        Game3DObjectBean copy = selected.copy();
        copy.id = "obj3d_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        copy.name = selected.name + "_copy";
        copy.x += 1.5f;
        scene.objects.add(copy);
        selected = copy;
        afterChange();
    }

    private void confirmDeleteSelected() {
        if (selected == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game3d_action_delete)
                .setMessage(getString(R.string.game3d_delete_confirm, selected.name))
                .setPositiveButton(R.string.common_word_delete, (d, w) -> {
                    pushUndo();
                    scene.objects.remove(selected);
                    // Anything parented to it would point at a missing object.
                    for (Game3DObjectBean obj : scene.objects) {
                        if (obj != null && selected.id.equals(obj.parentId)) {
                            obj.parentId = "";
                        }
                    }
                    selected = null;
                    afterChange();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void afterChange() {
        dirty = true;
        viewport.setScene(scene);
        viewport.requestRender();
        rebuildPanel();
        updateTitle();
    }

    // ------------------------------------------------------------------
    // Scenes
    // ------------------------------------------------------------------

    private void showScenePicker() {
        List<CharSequence> labels = new ArrayList<>();
        for (Game3DScene s : project.scenes) {
            labels.add(s.name);
        }
        labels.add(getString(R.string.game3d_new_scene));
        int checked = project.scenes.indexOf(scene);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game3d_menu_scenes)
                .setSingleChoiceItems(labels.toArray(new CharSequence[0]), checked, (d, which) -> {
                    d.dismiss();
                    if (which == project.scenes.size()) {
                        pushUndo();
                        Game3DScene created = Game3DScene.createStarter(
                                getString(R.string.game3d_scene_default_name, project.scenes.size() + 1));
                        project.scenes.add(created);
                        scene = created;
                    } else {
                        viewport.saveCameraToScene();
                        scene = project.scenes.get(which);
                    }
                    selected = null;
                    viewport.setScene(scene);
                    rebuildPanel();
                    updateTitle();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showSceneSettings() {
        if (scene == null) {
            return;
        }
        LinearLayout box = dialogContainer();
        EditText etName = styledEdit(scene.name, getString(R.string.game3d_prop_name), InputType.TYPE_CLASS_TEXT);
        EditText etAmbient = styledEdit(String.valueOf(scene.ambientIntensity),
                getString(R.string.game3d_scene_ambient), numericInput());
        EditText etGravity = styledEdit(String.valueOf(scene.gravityY),
                getString(R.string.game3d_scene_gravity), signedNumericInput());
        box.addView(labelled(getString(R.string.game3d_prop_name), etName));
        box.addView(labelled(getString(R.string.game3d_scene_ambient), etAmbient));
        box.addView(labelled(getString(R.string.game3d_scene_gravity), etGravity));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game3d_menu_scene_settings)
                .setView(box)
                .setPositiveButton(R.string.common_word_apply, (d, w) -> {
                    pushUndo();
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) scene.name = name;
                    scene.ambientIntensity = Math.max(0f, parseFloat(etAmbient, scene.ambientIntensity));
                    scene.gravityY = parseFloat(etGravity, scene.gravityY);
                    scene.ensureInitialized();
                    afterChange();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    private void save(boolean announce) {
        viewport.saveCameraToScene();
        Game3DProjectRepository.save(scId, project);
        dirty = false;
        if (announce) {
            toast(R.string.game3d_saved);
        }
        updateTitle();
    }

    private void confirmExit() {
        if (!dirty) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.design_quit_title_exit_projet)
                .setMessage(R.string.design_quit_message_confirm_save)
                .setPositiveButton(R.string.design_quit_button_save_and_exit, (d, w) -> {
                    save(false);
                    finish();
                })
                .setNeutralButton(R.string.common_word_cancel, null)
                .setNegativeButton(R.string.common_word_exit, (d, w) -> finish())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (!dirty) {
            // Let the framework handle back so predictive back keeps working.
            super.onBackPressed();
            return;
        }
        confirmExit();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (viewport != null) {
            viewport.onPause();
        }
        // The editor can be killed from the background, so never leave an
        // unsaved scene behind.
        if (dirty) {
            save(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewport != null) {
            viewport.onResume();
        }
    }

    private void updateTitle() {
        String name = project != null ? project.name : "3D";
        binding.toolbar.setTitle(dirty ? name + " *" : name);
        binding.toolbar.setSubtitle(scene != null ? scene.name : "");
    }

    // ------------------------------------------------------------------
    // Small view helpers
    // ------------------------------------------------------------------

    private void addSectionHeader(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(colorOnSurfaceVariant);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(4), dp(12), dp(4), dp(4));
        parent.addView(tv);
    }

    private void addHint(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(colorOnSurfaceVariant);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(32), dp(16), dp(32));
        parent.addView(tv);
    }

    private MaterialButton addPanelButton(LinearLayout parent, String text, View.OnClickListener click) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        parent.addView(button, lp);
        return button;
    }

    private void addToggle(LinearLayout parent, String label, boolean on, View.OnClickListener click) {
        addPanelButton(parent, label + ": " + getString(on
                ? R.string.game3d_value_on : R.string.game3d_value_off), click);
    }

    private LinearLayout dialogContainer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(0));
        return box;
    }

    private EditText styledEdit(String value, String hint, int inputType) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setTextColor(colorOnSurface);
        return et;
    }

    private View labelled(String label, View field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(colorOnSurfaceVariant);
        row.addView(tv);
        row.addView(field);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private static int numericInput() {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    }

    private static int signedNumericInput() {
        return numericInput() | InputType.TYPE_NUMBER_FLAG_SIGNED;
    }

    private interface TextConsumer {
        void accept(String value);
    }

    private interface FloatConsumer {
        void accept(float value);
    }

    private interface VectorConsumer {
        void accept(float x, float y, float z);
    }

    private void promptText(String title, String initial, TextConsumer onApply) {
        LinearLayout box = dialogContainer();
        EditText et = styledEdit(initial, title, InputType.TYPE_CLASS_TEXT);
        box.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.common_word_apply,
                        (d, w) -> onApply.accept(et.getText().toString()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void promptFloat(String title, float initial, FloatConsumer onApply) {
        LinearLayout box = dialogContainer();
        EditText et = styledEdit(String.valueOf(initial), title, signedNumericInput());
        box.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.common_word_apply,
                        (d, w) -> onApply.accept(parseFloat(et, initial)))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void promptVector(String title, float x, float y, float z, VectorConsumer onApply) {
        LinearLayout box = dialogContainer();
        EditText ex = styledEdit(String.valueOf(x), "X", signedNumericInput());
        EditText ey = styledEdit(String.valueOf(y), "Y", signedNumericInput());
        EditText ez = styledEdit(String.valueOf(z), "Z", signedNumericInput());
        box.addView(labelled("X", ex));
        box.addView(labelled("Y", ey));
        box.addView(labelled("Z", ez));
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.common_word_apply, (d, w) -> onApply.accept(
                        parseFloat(ex, x), parseFloat(ey, y), parseFloat(ez, z)))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void promptColor() {
        LinearLayout box = dialogContainer();
        EditText et = styledEdit(String.format(Locale.US, "#%06X", selected.color & 0xFFFFFF),
                "#RRGGBB", InputType.TYPE_CLASS_TEXT);
        box.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game3d_prop_color)
                .setView(box)
                .setPositiveButton(R.string.common_word_apply, (d, w) -> {
                    Integer parsed = parseHexColor(et.getText().toString());
                    if (parsed == null) {
                        toast(R.string.game3d_invalid_color);
                        return;
                    }
                    pushUndo();
                    selected.color = parsed;
                    afterChange();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void promptShading() {
        CharSequence[] labels = {
                getString(R.string.game3d_shading_unlit),
                getString(R.string.game3d_shading_lambert),
                getString(R.string.game3d_shading_phong)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game3d_prop_shading)
                .setSingleChoiceItems(labels, selected.shading, (d, which) -> {
                    d.dismiss();
                    pushUndo();
                    selected.shading = which;
                    afterChange();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    /** Accepts #RRGGBB, RRGGBB and #AARRGGBB; null when it is not a colour. */
    @Nullable
    static Integer parseHexColor(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        try {
            long value = Long.parseLong(hex, 16);
            return hex.length() == 6 ? (int) (0xFF000000L | value) : (int) value;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static float parseFloat(EditText field, float fallback) {
        try {
            return Float.parseFloat(field.getText().toString().trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
