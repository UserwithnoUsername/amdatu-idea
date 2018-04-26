/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.amdatu.idea;

import com.intellij.diagnostic.errordialog.PluginConflictDialog;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class AmdatuIdeaPluginStartup implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        checkOsmorcNotActive();

        AmdatuIdeaPlugin amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin.class);
        amdatuIdeaPlugin.getWorkspace();
    }

    // Using this plugin with Osmorc enabled leads to unpredictable results let the user decide which one should be active
    private void checkOsmorcNotActive() {
        PluginId osmorcPluginId = PluginId.findId("Osmorc");
        PluginId amdatuPluginId = PluginId.findId("org.amdatu.idea");

        if (osmorcPluginId != null && PluginManager.getPlugin(osmorcPluginId).isEnabled()
                && PluginManager.getPlugin(amdatuPluginId).isEnabled()) {

            new PluginConflictDialog(Arrays.asList(amdatuPluginId, osmorcPluginId), false).show();
        }
    }

}
