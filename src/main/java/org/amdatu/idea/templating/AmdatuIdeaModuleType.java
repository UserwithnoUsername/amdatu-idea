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

package org.amdatu.idea.templating;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public class AmdatuIdeaModuleType extends ModuleType<AmdatuIdeaModuleBuilder> {

    public static final String ID = "AMDATU_IDE_MODULE_TYPE";

    public AmdatuIdeaModuleType() {
        super(ID);
    }

    public static AmdatuIdeaModuleType getInstance() {
        return (AmdatuIdeaModuleType) ModuleTypeManager.getInstance().findByID(ID);
    }

    @NotNull
    @Override
    public AmdatuIdeaModuleBuilder createModuleBuilder() {
        return new AmdatuIdeaModuleBuilder();
    }

    @NotNull
    @Override
    public String getName() {
        return "Amdatu";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Create a new project / module based on a Bndtools template";
    }

    @Override
    public Icon getNodeIcon(boolean isOpened) {
        return AllIcons.Nodes.Module;
    }

}
