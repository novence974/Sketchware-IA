package pro.sketchware.utility;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * App language selection, independent of the device language.
 *
 * <p>Backed by {@link AppCompatDelegate#setApplicationLocales}: on Android 13
 * and newer the system stores the choice (and shows the app under Settings →
 * Languages, driven by {@code res/xml/locales_config.xml}); below that
 * AppCompat persists it itself through the {@code autoStoreLocales} metadata
 * declared on {@code AppLocalesMetadataHolderService} in the manifest. Either
 * way every {@code AppCompatActivity} is recreated with the new locale, so
 * callers do not restart anything by hand.</p>
 */
public final class LocaleManager {

    /** Follow the device language instead of forcing one. */
    public static final String SYSTEM = "";

    /**
     * BCP-47 tags the app ships translations for, in the order they are
     * offered. Keep in step with {@code res/xml/locales_config.xml} and with
     * the {@code values-<tag>} resource folders.
     */
    private static final String[] SUPPORTED_TAGS = {"en", "ar"};

    private LocaleManager() {
    }

    /** The language tags offered to the user, system default excluded. */
    @NonNull
    public static String[] supportedTags() {
        return SUPPORTED_TAGS.clone();
    }

    /** The tag in use, or {@link #SYSTEM} when following the device. */
    @NonNull
    public static String currentTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) {
            return SYSTEM;
        }
        Locale locale = locales.get(0);
        return locale == null ? SYSTEM : locale.getLanguage();
    }

    /**
     * Applies a language. Pass {@link #SYSTEM} to go back to the device one.
     * Activities are recreated by AppCompat, so this is safe to call from a
     * click listener.
     */
    public static void apply(@Nullable String tag) {
        AppCompatDelegate.setApplicationLocales(
                TextUtils.isEmpty(tag)
                        ? LocaleListCompat.getEmptyLocaleList()
                        : LocaleListCompat.forLanguageTags(tag));
    }

    /**
     * The language's name written in that language ("العربية", "English"),
     * which is what a speaker looks for in a language list — a name shown in
     * the language they cannot read is of no use to them.
     */
    @NonNull
    public static String displayName(@NonNull Context context, @Nullable String tag) {
        if (TextUtils.isEmpty(tag)) {
            return context.getString(pro.sketchware.R.string.settings_language_system);
        }
        Locale locale = Locale.forLanguageTag(tag);
        String name = locale.getDisplayName(locale);
        if (TextUtils.isEmpty(name)) {
            return tag;
        }
        // Most locales lower-case their own name; title-case the first letter
        // for scripts that have case at all (Arabic and CJK are unaffected).
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /** Index of {@code tag} in a list that starts with the system entry. */
    public static int selectedIndex(@Nullable String tag) {
        if (TextUtils.isEmpty(tag)) {
            return 0;
        }
        for (int i = 0; i < SUPPORTED_TAGS.length; i++) {
            if (SUPPORTED_TAGS[i].equals(tag)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** The entry at {@code index} in that same list, system entry included. */
    @NonNull
    public static String tagAt(int index) {
        if (index <= 0 || index > SUPPORTED_TAGS.length) {
            return SYSTEM;
        }
        return SUPPORTED_TAGS[index - 1];
    }
}
