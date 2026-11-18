package dev.fabricamper.intellij;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.GradleAutoReloadSettingsCollector;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Supplies additional settings files to the native Gradle auto-reload tracker.
 * This is an internal Gradle IDE extension; the plugin targets IDEA 263 only.
 */
public final class FabricAmperSettingsCollector implements GradleAutoReloadSettingsCollector {
    private static final Pattern SETTINGS_PLUGIN = Pattern.compile(
            "\\bid\\s*\\(\\s*\"dev\\.fabric-amper\\.settings\"\\s*\\)");
    private static final Pattern COMMENTS = Pattern.compile(
            "(?s)/\\*.*?\\*/|(?m)^\\s*//[^\\r\\n]*");

    @Override
    public @NotNull List<Path> collectSettingsFiles(
            @NotNull Project project, @NotNull GradleProjectSettings projectSettings) {
        return collectRootFiles(projectSettings.getExternalProjectPath());
    }

    static List<Path> collectRootFiles(String externalProjectPath) {
        if (externalProjectPath == null || externalProjectPath.isBlank()) {
            return List.of();
        }

        try {
            Path root = Path.of(externalProjectPath).toAbsolutePath().normalize();
            Path settings = root.resolve("settings.gradle.kts");
            if (!Files.isRegularFile(settings)) {
                return List.of();
            }
            String source = COMMENTS.matcher(Files.readString(settings)).replaceAll("");
            if (!SETTINGS_PLUGIN.matcher(source).find()) {
                return List.of();
            }

            // Include both candidates even if one does not currently exist, like
            // Gradle's own optional settings files. This covers creation, removal,
            // and a switch between the two supported filenames. No recursive scan.
            return List.of(root.resolve("mod.yaml"), root.resolve("mod.yml"));
        } catch (IOException | InvalidPathException | SecurityException ignored) {
            // Missing/unreadable projects must not interfere with Gradle imports.
            return List.of();
        }
    }
}
