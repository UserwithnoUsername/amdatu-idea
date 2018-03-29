package org.amdatu.ide;

import aQute.bnd.build.Workspace;

public interface AmdatuIdePlugin {


    boolean isBndWorkspace();

    boolean isWorkspaceInitialized();

    /**
     * Get the bnd workspace for a project
     *
     * @return The bnd {@link Workspace} for the project
     */
    Workspace getWorkspace();

    void refreshWorkspace(boolean refreshExportedContentJars);

    boolean reportErrors(aQute.bnd.build.Project project);

    boolean reportWarnings(aQute.bnd.build.Project project);

    void info(String message);

    void warning(String message);

    void error(String message);
}
