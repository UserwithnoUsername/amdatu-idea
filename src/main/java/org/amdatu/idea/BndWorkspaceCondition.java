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


import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;

import java.io.File;

public class BndWorkspaceCondition implements Condition<Project> {
    @Override
    public boolean value(Project project) {

        AmdatuIdeaPlugin amdatuIdeaPlugin = project.getService(AmdatuIdeaPlugin.class);

        String rootDir = project.getBasePath();
        String imlPath = rootDir + File.separator + project.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION;

        return (amdatuIdeaPlugin.isBndWorkspace() && new File(imlPath).isFile());
    }
}
