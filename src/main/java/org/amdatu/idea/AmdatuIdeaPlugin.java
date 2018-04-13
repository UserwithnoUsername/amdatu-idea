package org.amdatu.idea;

import aQute.bnd.build.Workspace;

public interface AmdatuIdeaPlugin {

    boolean isBndWorkspace();

    /**
     * Get the bnd workspace for a project
     *
     * @return The bnd {@link Workspace} for the project
     */
    Workspace getWorkspace();


    void refreshWorkspace(boolean refreshExportedContentJars);

    PackageInfoService getPackageInfoSevice();

    AmdatuIdeaNotificationService getNotificationService();

}
