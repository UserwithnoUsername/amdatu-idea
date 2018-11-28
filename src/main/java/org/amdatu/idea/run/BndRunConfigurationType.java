/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.amdatu.idea.run;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.configurations.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import icons.OsmorcIdeaIcons;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

import static org.amdatu.idea.i18n.OsmorcBundle.message;

public class BndRunConfigurationType extends ConfigurationTypeBase {
    private static final String ID = "osgi.bnd.run";

    @NotNull
    public static BndRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(BndRunConfigurationType.class);
    }

    public BndRunConfigurationType() {
        super(ID, message("bnd.configuration.name"), message("bnd.configuration.description"), OsmorcIdeaIcons.Bnd);
        addFactory(new LaunchFactory(this));
        addFactory(new TestFactory(this));
    }

    private static abstract class FactoryBase extends ConfigurationFactory {
        private final String myName;
        private final Icon myIcon;

        FactoryBase(@NotNull ConfigurationType type, @NotNull String name, @NotNull Icon icon) {
            super(type);
            myName = name;
            myIcon = icon;
        }

        @Override
        public String getName() {
            return myName;
        }

        @Override
        public Icon getIcon() {
            return myIcon;
        }

        @Override
        public boolean isApplicable(@NotNull Project project) {
            return project.getComponent(AmdatuIdeaPlugin.class).isBndWorkspace();
        }
    }

    private static class LaunchFactory extends FactoryBase {
        LaunchFactory(@NotNull ConfigurationType type) {
            super(type, message("bnd.run.configuration.name"), createLayeredIcon(AllIcons.RunConfigurations.Application));
        }

        @NotNull
        @Override
        public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
            return new BndRunConfigurationBase.Launch(project, this, "");
        }
    }

    private static Icon createLayeredIcon(Icon icon) {
        LayeredIcon layeredIcon = new LayeredIcon(2);
        layeredIcon.setIcon(OsmorcIdeaIcons.Bnd, 0);
        layeredIcon.setIcon(IconUtil.scale(icon, null,0.5f), 1, 4);
        return layeredIcon;
    }

    private static class TestFactory extends FactoryBase {
        TestFactory(@NotNull ConfigurationType type) {
            super(type, message("bnd.test.configuration.name"), createLayeredIcon(AllIcons.RunConfigurations.Junit));
        }

        @NotNull
        @Override
        public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
            BndRunConfigurationBase.Test configuration = new BndRunConfigurationBase.Test(project, this, "");
            JavaRunConfigurationExtensionManager.getInstance().extendTemplateConfiguration(configuration);
            return configuration;
        }
    }
}