package org.amdatu.ide;

import aQute.bnd.build.Workspace;

public interface AmdatuIdePlugin {

    boolean isBndWorkspace();

    /**
     * Get the bnd workspace for a project
     *
     * @return The bnd {@link Workspace} for the project
     */
    Workspace getWorkspace();


    void refreshWorkspace(boolean refreshExportedContentJars);

    PackageInfoService getPackageInfoSevice();

    AmdatuIdeNotificationService getNotificationService();

}
