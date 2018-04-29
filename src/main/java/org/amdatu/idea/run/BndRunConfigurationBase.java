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

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import org.amdatu.idea.i18n.OsmorcBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

public abstract class BndRunConfigurationBase extends LocatableConfigurationBase implements ModuleRunProfile,
                CommonProgramRunConfigurationParameters, PersistentStateComponent<Element> {

    BndRunConfigurationBase(Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    protected Class<BndRunConfigurationOptions> getOptionsClass() {
        return BndRunConfigurationOptions.class;
    }

    @Override
    protected BndRunConfigurationOptions getOptions() {
        return (BndRunConfigurationOptions) super.getOptions();
    }

    @NotNull
    @Override
    public Element getState() {
        Element element = new Element("state");
        super.writeExternal(element);
        return element;
    }

    @NotNull
    @Override
    public SettingsEditorGroup<? extends BndRunConfigurationBase> getConfigurationEditor() {
        SettingsEditorGroup<BndRunConfigurationBase> group = new SettingsEditorGroup<>();
        group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new BndRunConfigurationEditor(getProject()));
        return group;
    }

    @Nullable
    @Override
    public abstract RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException;

    @Override
    public void setProgramParameters(@Nullable String programParameters) {
        getOptions().setProgramParameters(programParameters);
    }

    @Nullable
    @Override
    public String getProgramParameters() {
        return getOptions().getProgramParameters();
    }

    @Override
    public void setWorkingDirectory(@Nullable String workingDirectory) {
        getOptions().setWorkingDirectory(workingDirectory);
    }

    @Nullable
    @Override
    public String getWorkingDirectory() {
        return getOptions().getWorkingDirectory();
    }

    @Override
    public void setEnvs(@NotNull Map<String, String> envs) {
        getOptions().setEnvs(envs);
    }

    @NotNull
    @Override
    public Map<String, String> getEnvs() {
        return getOptions().getEnvs();
    }

    @Override
    public void setPassParentEnvs(boolean passParentEnvs) {
        getOptions().setPassParentEnvs(passParentEnvs);
    }

    @Override
    public boolean isPassParentEnvs() {
        return getOptions().isPassParentEnvs();
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        String file = getOptions().getBndRunFile();
        if (file == null || !new File(file).isFile()) {
            throw new RuntimeConfigurationException(OsmorcBundle.message("bnd.run.configuration.invalid", file));
        }
        if (getOptions().isUseAlternativeJre()) {
            JavaParametersUtil.checkAlternativeJRE(getOptions().getAlternativeJrePath());
        }
    }

    public static class Launch extends BndRunConfigurationBase {
        Launch(Project project, @NotNull ConfigurationFactory factory, String name) {
            super(project, factory, name);
        }

        @Nullable
        @Override
        public BndLaunchState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
            return new BndLaunchState(environment, this);
        }
    }

    public static class Test extends BndRunConfigurationBase implements CommonJavaRunConfigurationParameters {
        public Test(Project project, @NotNull ConfigurationFactory factory, String name) {
            super(project, factory, name);
        }

        @Nullable
        @Override
        public BndTestState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
            return new BndTestState(environment, this);
        }

        @NotNull
        @Override
        public SettingsEditorGroup<? extends BndRunConfigurationBase> getConfigurationEditor() {
            SettingsEditorGroup<? extends BndRunConfigurationBase> configurationEditor = super.getConfigurationEditor();
            JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, configurationEditor);
            return configurationEditor;
        }

        @NotNull
        @Override
        public Element getState() {
            Element state = super.getState();
            JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, state);
            return state;
        }

        @Override
        public void loadState(@NotNull Element element) {
            super.loadState(element);
            JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
        }

        @Override
        public void checkConfiguration() throws RuntimeConfigurationException {
            super.checkConfiguration();
            JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
        }

        @Override
        public void setVMParameters(@Nullable String value) {

        }

        @Override
        public String getVMParameters() {
            return null;
        }

        @Override
        public boolean isAlternativeJrePathEnabled() {
            return false;
        }

        @Override
        public void setAlternativeJrePathEnabled(boolean enabled) {

        }

        @Nullable
        @Override
        public String getAlternativeJrePath() {
            return null;
        }

        @Override
        public void setAlternativeJrePath(@Nullable String path) {

        }

        @Nullable
        @Override
        public String getRunClass() {
            return null;
        }

        @Nullable
        @Override
        public String getPackage() {
            return null;
        }
    }
}
