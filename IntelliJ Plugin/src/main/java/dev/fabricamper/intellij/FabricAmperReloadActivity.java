package dev.fabricamper.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

/** Installs the project-scoped editor listener when an IDE project opens. */
public final class FabricAmperReloadActivity implements ProjectActivity {
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        project.getService(FabricAmperReloadListener.class);
        return Unit.INSTANCE;
    }
}
