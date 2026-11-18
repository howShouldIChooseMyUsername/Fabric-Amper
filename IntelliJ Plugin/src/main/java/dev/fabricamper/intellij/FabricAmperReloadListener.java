package dev.fabricamper.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Reports root manifest editor edits even when Gradle's collected inputs are stale. */
public final class FabricAmperReloadListener implements Disposable, DocumentListener {
    private final Project project;
    private final Set<Document> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;

    public FabricAmperReloadListener(@NotNull Project project) {
        this.project = project;
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(this, this);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        if (disposed || project.isDisposed()) return;
        Document document = event.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null || !file.isInLocalFileSystem() || !isManifestName(file.getName())) return;
        if (!pending.add(document)) return;

        // Wait until the document manager has processed this change. Coalesce bulk
        // edits and ignore document reloads from disk (which are not user edits).
        ApplicationManager.getApplication().invokeLater(() -> {
            pending.remove(document);
            if (disposed || project.isDisposed()) return;
            FileDocumentManager documents = FileDocumentManager.getInstance();
            if (!documents.isDocumentUnsaved(document)) return;
            VirtualFile changed = documents.getFile(document);
            if (changed == null || !changed.isValid() || !changed.isInLocalFileSystem()) return;
            reportChange(changed.getPath());
        });
    }

    private void reportChange(String filePath) {
        ExternalSystemProjectTracker tracker = null;
        for (GradleProjectSettings linked : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
            String root = linked.getExternalProjectPath();
            if (!isRootManifest(root, filePath)) continue;
            if (tracker == null) tracker = ExternalSystemProjectTracker.getInstance(project);
            // Use the exact linked path as the tracker ID, not a rewritten path.
            tracker.markDirtyInternal(new ExternalSystemProjectId(GradleConstants.SYSTEM_ID, root));
        }
    }

    static boolean isRootManifest(String externalProjectPath, String filePath) {
        if (externalProjectPath == null || externalProjectPath.isBlank() || filePath == null) return false;
        try {
            Path root = Path.of(externalProjectPath).toAbsolutePath().normalize();
            Path file = Path.of(filePath).toAbsolutePath().normalize();
            if (file.getFileName() == null || !isManifestName(file.getFileName().toString())
                    || !root.equals(file.getParent())) return false;
            return FabricAmperSettingsCollector.collectRootFiles(externalProjectPath).contains(file);
        } catch (InvalidPathException | SecurityException ignored) {
            return false;
        }
    }

    private static boolean isManifestName(String name) {
        return "mod.yaml".equals(name) || "mod.yml".equals(name);
    }

    @Override
    public void dispose() {
        disposed = true;
        pending.clear();
    }
}
