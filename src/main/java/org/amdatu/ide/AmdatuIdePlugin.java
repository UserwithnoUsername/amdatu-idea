package org.amdatu.ide;

import aQute.bnd.build.Workspace;
import com.intellij.psi.PsiDirectory;

import java.util.Map;

public interface AmdatuIdePlugin {


    boolean isBndWorkspace();

    boolean isWorkspaceInitialized();

    /**
     * Get the bnd workspace for a project
     *
     * @return The bnd {@link Workspace} for the project
     */
    Workspace getWorkspace();

    Map<PsiDirectory, String> getPackageStateMap();

    void refreshWorkspace(boolean refreshExportedContentJars);

    boolean reportErrors(aQute.bnd.build.Project project);

    boolean reportWarnings(aQute.bnd.build.Project project);

    void info(String message);

    void warning(String message);

    void error(String message);
}
