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

import org.amdatu.idea.AmdatuIdeaConstants;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.RunWith;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;

import aQute.bnd.osgi.Constants;
import junit.framework.TestCase;
import static org.amdatu.idea.AmdatuIdeaConstants.BND_EXT;
import static org.amdatu.idea.AmdatuIdeaConstants.BND_RUN_EXT;

public abstract class BndRunConfigurationProducer extends RunConfigurationProducer<BndRunConfigurationBase> {

    private static final Logger LOG = Logger.getInstance(BndRunConfigurationProducer.class);

    BndRunConfigurationProducer(@NotNull ConfigurationFactory factory) {
        super(factory);
    }

    @Override
    protected boolean setupConfigurationFromContext(BndRunConfigurationBase configuration, ConfigurationContext context, Ref<PsiElement> source) {
        if (context.getLocation() == null
                || context.getModule() == null) {
            return false;
        }

        Location<?> location = context.getLocation();
        PsiElement psiLocation = context.getPsiLocation();
        Module module = context.getModule();

        String moduleName = module.getName();
        VirtualFile projectDir = ProjectUtil.guessProjectDir(context.getProject());
        if (projectDir == null) {
            return false;
        }

        VirtualFile moduleDir = projectDir.findChild(moduleName);
        if (moduleDir == null) {
            return false;
        }

        VirtualFile file = location.getVirtualFile();
        if ((file == null || file.isDirectory()) && isTestModule(module)) {
            // For test projects try to find the bnd.bnd file in case there is no file or it's a folder.
            // This can happen for example when the module is right clicked from the project tree.
            // Only for test projects as "normal" projects can't (or should not run from bnd.bnd) but use a bndrun
            file = moduleDir.findChild("bnd.bnd");
        }

        if (file == null || file.isDirectory()) {
            // No file so
            return false;
        }


        String modulePath = moduleDir.getPath();
        configuration.getOptions().setWorkingDirectory(modulePath);
        configuration.getOptions().setPassParentEnvs(true);

        if ((configuration instanceof BndRunConfigurationBase.Launch) && !isTestModule(module)) {
            if (isBndPropertiesFile(file)) {
                String name;
                if (BND_RUN_EXT.equals(file.getExtension())) {
                    name = file.getNameWithoutExtension() + " (" + moduleName + ")";
                } else {
                    name = moduleName;
                }

                configuration.setName(name);
                configuration.getOptions().setBndRunFile(file.getPath());
                return true;
            }
        } else if (configuration instanceof BndRunConfigurationBase.Test && isTestModule(module)) {
            if (isBndPropertiesFile(file) && file.getName().equals(AmdatuIdeaConstants.BND_BND)) {
                configuration.setName(moduleName);
                configuration.getOptions().setBndRunFile(file.getPath());
                configuration.getOptions().setModuleName(moduleName);
                return true;
            }

            PsiClass psiClass = findTestClass(psiLocation);
            if (psiClass != null) {
                String name = psiClass.getName();
                String test = psiClass.getQualifiedName();

                PsiMethod psiMethod = findTestMethod(psiLocation);
                if (psiMethod != null) {
                    name = psiMethod.getName() + " (" + psiClass.getName() + ")";
                    test = test + ":" + psiMethod.getName();
                }
                configuration.setName(name);

                VirtualFile bndPropertiesFile = moduleDir.findChild(AmdatuIdeaConstants.BND_BND);
                String bndPropertiesFilePath = bndPropertiesFile != null ? bndPropertiesFile.getPath() : null;
                configuration.getOptions().setBndRunFile(bndPropertiesFilePath);
                configuration.getOptions().setModuleName(moduleName);
                configuration.getOptions().setTest(test);
                JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static PsiMethod findTestMethod(PsiElement psiLocation) {
        PsiMethod psiMethod;
        if (psiLocation instanceof PsiMethod) {
            psiMethod = (PsiMethod) psiLocation;
        } else {
            psiMethod = (PsiMethod) PsiTreeUtil.findFirstParent(psiLocation, PsiMethod.class::isInstance);
        }

        if (psiMethod != null) {
            if (psiMethod.getName().startsWith("test")) {
                return psiMethod; // JUnit 3
            }

            for (PsiAnnotation psiAnnotation : psiMethod.getAnnotations()) {
                if (org.junit.Test.class.getName().equals(psiAnnotation.getQualifiedName())) {
                    return psiMethod; // JUnit 4
                }
            }
        }

        return null;
    }

    private static PsiClass findTestClass(PsiElement psiLocation) {
        PsiClass psiClass;
        if (psiLocation instanceof PsiClass) {
            psiClass = (PsiClass) psiLocation;
        } else {
            psiClass = (PsiClass) PsiTreeUtil.findFirstParent(psiLocation, PsiClass.class::isInstance);
        }

        if (psiClass != null) {
            for (PsiAnnotation psiAnnotation : psiClass.getAnnotations()) {
                if (RunWith.class.getName().equals(psiAnnotation.getQualifiedName())) {
                    return psiClass; // JUnit 4
                }
            }

            // AMDATUIDEA-13: Junit 4 test classes are not required to have a RunWith annotation
            for (PsiMethod psiMethod : psiClass.getMethods()) {
                for (PsiAnnotation psiAnnotation : psiMethod.getAnnotations()) {
                    if (org.junit.Test.class.getName().equals(psiAnnotation.getQualifiedName())) {
                        return psiClass; // JUnit 4
                    }
                }
            }

            PsiClass parent = psiClass;
            while (parent != null && !Object.class.getName().equals(parent.getQualifiedName())) {
                if (TestCase.class.getName().equals(parent.getQualifiedName())) {
                    return psiClass; // JUnit 3
                }
                parent = parent.getSuperClass();
            }
        }

        return null;
    }

    @Override
    public boolean isConfigurationFromContext(BndRunConfigurationBase configuration, ConfigurationContext context) {
        if (getConfigurationFactory() == configuration.getFactory()) {
            Location<?> location = context.getLocation();
            if (location == null) {
                return false;
            }
            BndRunConfigurationOptions configurationOptions = configuration.getOptions();
            if ((configuration instanceof BndRunConfigurationBase.Test) && configurationOptions.getTest() != null) {
                PsiClass psiClass = findTestClass(context.getPsiLocation());
                if (psiClass == null) {
                    return false;
                }

                String test = psiClass.getQualifiedName();

                PsiMethod psiMethod = findTestMethod(context.getPsiLocation());
                if (psiMethod != null) {
                    test = test + ":" + psiMethod.getName();
                }

                return configurationOptions.getTest().equals(test);
            }

            VirtualFile file = location.getVirtualFile();
            return file != null && !file.isDirectory() && FileUtil.pathsEqual(file.getPath(), configurationOptions.getBndRunFile());
        }

        return false;
    }

    @Override
    public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
        // Replace plain JUnit configurations when there is a bnd BndRunConfigurationBase.Test as the JUnit
        // configuration won't work in that case.
        return (self.getConfiguration() instanceof BndRunConfigurationBase.Test) && other.getConfigurationType().getId().equals("JUnit");
    }

    public static class Launch extends BndRunConfigurationProducer {
        public Launch() {
            super(BndRunConfigurationType.getInstance().getConfigurationFactories()[0]);
        }
    }

    public static class Test extends BndRunConfigurationProducer {
        public Test() {
            super(BndRunConfigurationType.getInstance().getConfigurationFactories()[1]);
        }
    }

    private static boolean isBndPropertiesFile(VirtualFile file) {
        return file != null && !file.isDirectory() &&
                (BND_EXT.equals(file.getExtension()) || BND_RUN_EXT.equals(file.getExtension()));
    }

    private static boolean isTestModule(Module module) {
        if (module == null) {
            return false;
        }

        AmdatuIdeaPlugin amdatuIdeaPlugin = module.getProject().getComponent(AmdatuIdeaPlugin.class);
        try {
            aQute.bnd.build.Project project = amdatuIdeaPlugin.withWorkspace(ws -> ws.getProject(module.getName()));
            return project.getProperties().containsKey(Constants.TESTCASES);
        } catch (Exception e) {
            LOG.warn("isTestModule check failed for module: " + module.getName(), e);
            return false;
        }
    }
}
