package org.amdatu.ide;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

public class AmdatuIdePluginStartup implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        AmdatuIdePlugin amdatuIdePlugin = project.getComponent(AmdatuIdePlugin.class);
        if (amdatuIdePlugin.getWorkspace() != null) {
            amdatuIdePlugin.reImportProjects();
        }
    }

}
