package org.amdatu.ide;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.project.Project;

import aQute.bnd.build.Workspace;

public interface AmdatuIdePlugin {

    boolean isBndWorkspace(@NotNull Project project);

    /**
     * Get the bnd workspace for a project
     *
     *
     * @param project The project to get the workspace for
     * @return The bnd {@link Workspace} for the project
     */
    Workspace getWorkspace(@NotNull Project project);

}
