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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.amdatu.idea.i18n.OsmorcBundle;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Constants;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.amdatu.idea.i18n.OsmorcBundle.message;

public class BndLaunchUtil {

    @NotNull
    static JavaParameters createJavaParameters(@NotNull BndRunConfigurationBase configuration,
                                               @NotNull ProjectLauncher launcher) throws CantRunException {
        Project project = configuration.getProject();

        JavaParameters parameters = new JavaParameters();
        ProgramParametersUtil.configureConfiguration(parameters, configuration);

        String jreHome = configuration.getOptions().isUseAlternativeJre() ?
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
        return c instanceof List ? (List<String>) c : ContainerUtil.newArrayList(c);
    }

    public static String message(Throwable t) {
        String message = t.getMessage();
        return StringUtil.isEmptyOrSpaces(message) ?
                        t.getClass().getSimpleName() :
                        t.getClass().getSimpleName() + ": " + message;
    }

    static void addBootDelegation(aQute.bnd.build.Project project, String packages) throws CantRunException {
        Map<String, String> runProperties = project.getRunProperties();
        if (runProperties != null && runProperties.containsKey(Constants.FRAMEWORK_BOOTDELEGATION) &&
            !runProperties.get(Constants.FRAMEWORK_BOOTDELEGATION).contains(packages)) {

            throw new CantRunException(OsmorcBundle.message("bnd.test.cannot.run",
                    String.format("-runproperties contains '%s' property that does not include '%s'",
                            Constants.FRAMEWORK_BOOTDELEGATION, packages)));
        } else {
            project.setProperty("-runproperties.intellij-bootdelegation",
                String.format("%s=%s", Constants.FRAMEWORK_BOOTDELEGATION, packages));
        }
    }
}