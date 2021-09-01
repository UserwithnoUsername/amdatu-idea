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
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.extensions.PluginId.getId;

public class AmdatuIdeaPluginStartup implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        checkOsmorcNotActive();

        AmdatuIdeaPlugin amdatuIdeaPlugin = project.getService(AmdatuIdeaPlugin.class);
        if (amdatuIdeaPlugin.isBndWorkspace() && !amdatuIdeaPlugin.isInitialized()) {
            amdatuIdeaPlugin.info("Bnd workspace detected, use 'New -> Project from Existing Sources' to import", null);
        }

        // Just get the services to initialize them and have them register listeners
        project.getService(BaseliningErrorService.class);
        project.getService(PackageInfoService.class);
    }

    // Using this plugin with Osmorc enabled leads to unpredictable results let the user decide which one should be active
    private void checkOsmorcNotActive() {
        IdeaPluginDescriptor osmorcPlugin = PluginManagerCore.getPlugin(getId("Osmorc"));
        IdeaPluginDescriptor amdatuPlugin = PluginManagerCore.getPlugin(getId("org.amdatu.idea"));
        if (osmorcPlugin != null && osmorcPlugin.isEnabled()
                && amdatuPlugin != null && amdatuPlugin.isEnabled()) {

            List<PluginId> conflictingPlugins = Arrays.asList(amdatuPlugin.getPluginId(), osmorcPlugin.getPluginId());
            new PluginConflictDialog(conflictingPlugins, false).show();
        }
    }

}
