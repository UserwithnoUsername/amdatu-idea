// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.amdatu.idea.run;

import aQute.bnd.build.ProjectLauncher;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.amdatu.idea.i18n.OsmorcBundle;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BndLaunchUtil {

    private BndLaunchUtil() {
        // prevent instantiation
    }

    private static final Logger LOG = Logger.getInstance(BndLaunchUtil.class);

    @NotNull
    static JavaParameters createJavaParameters(@NotNull BndRunConfigurationBase configuration,
                                               @NotNull ProjectLauncher launcher) throws CantRunException {
        Project project = configuration.getProject();

        JavaParameters parameters = new JavaParameters();
        ProgramParametersUtil.configureConfiguration(parameters, configuration);

        String jreHome = configuration.getOptions().getUseAlternativeJre() ?
                configuration.getOptions().getAlternativeJrePath() :
                null;
        JavaParametersUtil.configureProject(project, parameters, JavaParameters.JDK_ONLY, jreHome);

        parameters.getEnv().putAll(launcher.getRunEnv());
        parameters.getVMParametersList().addAll(asList(launcher.getRunVM()));
        parameters.getClassPath().addAll(asList(launcher.getClasspath()));
        parameters.setMainClass(launcher.getMainTypeName());
        parameters.getProgramParametersList().addAll(asList(launcher.getRunProgramArgs()));

        return parameters;
    }

    private static List<String> asList(Collection<String> c) {
        return c instanceof List ? (List<String>) c : new ArrayList<>(c);
    }

    public static String message(Throwable t) {
        String message = t.getMessage();
        return StringUtil.isEmptyOrSpaces(message) ?
                t.getClass().getSimpleName() :
                t.getClass().getSimpleName() + ": " + message;
    }

    static void addBootDelegation(aQute.bnd.build.Project project, String packages) throws CantRunException {
        Map<String, String> runProperties = project.getRunProperties();
        if (runProperties == null || !runProperties.containsKey(Constants.FRAMEWORK_BOOTDELEGATION)) {
            project.setProperty("-runproperties.intellij-bootdelegation",
                    String.format("%s=%s", Constants.FRAMEWORK_BOOTDELEGATION, packages));
        } else if (!runProperties.get(Constants.FRAMEWORK_BOOTDELEGATION).contains(packages)) {
            throw new CantRunException(OsmorcBundle.message("bnd.test.cannot.run",
                    String.format("-runproperties contains '%s' property that does not include '%s'",
                            Constants.FRAMEWORK_BOOTDELEGATION, packages)));
        }

    }

    public static boolean isTestModule(Module module) {
        if (module == null) {
            return false;
        }

        AmdatuIdeaPlugin amdatuIdeaPlugin = module.getProject().getComponent(AmdatuIdeaPlugin.class);
        try {
            aQute.bnd.build.Project project = amdatuIdeaPlugin.withWorkspace(ws -> ws.getProject(module.getName()));
            return project != null && project.getProperties().containsKey(aQute.bnd.osgi.Constants.TESTCASES);
        } catch (Exception e) {
            LOG.warn("isTestModule check failed for module: " + module.getName(), e);
            return false;
        }
    }
}
