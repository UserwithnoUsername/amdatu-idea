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
package org.amdatu.ide.run;

import com.intellij.execution.configurations.LocatableRunConfigurationOptions;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BndRunConfigurationOptions extends LocatableRunConfigurationOptions {
    private String bndRunFile;
    private boolean useAlternativeJre = false;
    private String alternativeJrePath;

    private String moduleName;

    private String test;
    private String myProgramParameters;
    private String myWorkingDirectory;
    private Map<String, String> myEnvs = ContainerUtil.newHashMap();
    private boolean myPassParentEnvs;

    public String getBndRunFile() {
        return bndRunFile;
    }

    public void setBndRunFile(String bndRunFile) {
        this.bndRunFile = bndRunFile;
    }

    public boolean isUseAlternativeJre() {
        return useAlternativeJre;
    }

    public void setUseAlternativeJre(boolean useAlternativeJre) {
        this.useAlternativeJre = useAlternativeJre;
    }

    public String getAlternativeJrePath() {
        return alternativeJrePath;
    }

    public void setAlternativeJrePath(String alternativeJrePath) {
        this.alternativeJrePath = alternativeJrePath;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String tests) {
        this.test = tests;
    }

    public Project getProject() {
        return null;
    }

    public void setProgramParameters(@Nullable String programParameters) {
        myProgramParameters = programParameters;
    }

    @Nullable
    public String getProgramParameters() {
        return myProgramParameters;
    }

    public void setWorkingDirectory(@Nullable String workingDirectory) {
        myWorkingDirectory = workingDirectory;
    }

    @Nullable
    public String getWorkingDirectory() {
        return myWorkingDirectory;
    }

    public void setEnvs(@NotNull Map<String, String> envs) {
        myEnvs.clear();
        myEnvs.putAll(envs);
    }

    @NotNull
    public Map<String, String> getEnvs() {
        return myEnvs;
    }

    public void setPassParentEnvs(boolean passParentEnvs) {
        myPassParentEnvs = passParentEnvs;
    }

    public boolean isPassParentEnvs() {
        return myPassParentEnvs;
    }
}