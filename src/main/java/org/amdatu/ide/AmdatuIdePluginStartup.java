package org.amdatu.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class AmdatuIdePluginStartup implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        AmdatuIdePlugin amdatuIdePlugin = project.getComponent(AmdatuIdePlugin.class);
        amdatuIdePlugin.getWorkspace();
    }

}
