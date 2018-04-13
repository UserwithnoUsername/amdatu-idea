package org.amdatu.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class AmdatuIdeaPluginStartup implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        AmdatuIdeaPlugin amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin.class);
        amdatuIdeaPlugin.getWorkspace();
    }

}
