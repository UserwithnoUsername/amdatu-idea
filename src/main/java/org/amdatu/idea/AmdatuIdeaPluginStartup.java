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

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.amdatu.idea.toolwindow.BundleInfoToolWindow;
import org.jetbrains.annotations.NotNull;

import com.intellij.diagnostic.errordialog.PluginConflictDialog;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

import aQute.bnd.build.Workspace;
import static com.intellij.openapi.extensions.PluginId.getId;

public class AmdatuIdeaPluginStartup implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        checkOsmorcNotActive();

        AmdatuIdeaPlugin amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin.class);

        String rootDir = project.getBasePath();
        String imlPath = rootDir + File.separator + project.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION;

        if (amdatuIdeaPlugin.isBndWorkspace() && new File(imlPath).isFile()) {
            Workspace workspace = amdatuIdeaPlugin.getWorkspace();
            new BundleInfoToolWindow(project, workspace);
        } else {
            // TODO: Import action link
            amdatuIdeaPlugin.getNotificationService().info("Bnd workspace detected, use 'Import Project' to import");
        }
    }

    // Using this plugin with Osmorc enabled leads to unpredictable results let the user decide which one should be active
    private void checkOsmorcNotActive() {
        IdeaPluginDescriptor osmorcPlugin = PluginManager.getPlugin(getId("Osmorc"));
        IdeaPluginDescriptor amdatuPlugin = PluginManager.getPlugin(getId("org.amdatu.idea"));
        if (osmorcPlugin != null && osmorcPlugin.isEnabled()
                && amdatuPlugin != null && amdatuPlugin.isEnabled()) {

            List<PluginId> conflictingPlugins = Arrays.asList(amdatuPlugin.getPluginId(), osmorcPlugin.getPluginId());
            new PluginConflictDialog(conflictingPlugins, false).show();
        }
    }

}
