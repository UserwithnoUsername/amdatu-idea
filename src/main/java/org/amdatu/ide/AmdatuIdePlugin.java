package org.amdatu.ide;

import com.intellij.openapi.project.Project;

import aQute.bnd.build.Workspace;

public interface AmdatuIdePlugin {

    boolean isBndWorkspace();

    /**
     * Get the bnd workspace for a project
     *
     *
     * @return The bnd {@link Workspace} for the project
     */
    Workspace getWorkspace();

    void reImportProjects();
}
