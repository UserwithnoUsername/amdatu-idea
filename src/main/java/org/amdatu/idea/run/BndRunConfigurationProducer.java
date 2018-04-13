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

import aQute.bnd.osgi.Constants;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import junit.framework.TestCase;
import org.amdatu.idea.AmdatuIdeaConstants;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.RunWith;

import static org.amdatu.idea.AmdatuIdeaConstants.BND_EXT;
import static org.amdatu.idea.AmdatuIdeaConstants.BND_RUN_EXT;

public abstract class BndRunConfigurationProducer extends RunConfigurationProducer<BndRunConfigurationBase> {

    private static final Logger LOG = Logger.getInstance(BndRunConfigurationProducer.class);

    BndRunConfigurationProducer(@NotNull ConfigurationFactory factory) {
        super(factory);
    }

    @Override
    protected boolean setupConfigurationFromContext(BndRunConfigurationBase configuration, ConfigurationContext context, Ref<PsiElement> source) {
        if ((context.getLocation() == null) || (context.getLocation().getVirtualFile() == null)) {
            return false;
        }

        Location<?> location = context.getLocation();
        PsiElement psiLocation = context.getPsiLocation();
        VirtualFile file = location.getVirtualFile();
        Module module = context.getModule();
        String moduleName = module.getName();

        VirtualFile moduleDir = context.getProject().getBaseDir().findChild(moduleName);
        if (moduleDir == null) {
            return false;
        }
        String modulePath = moduleDir.getPath();
        configuration.getOptions().setWorkingDirectory(modulePath);

        if ((configuration instanceof BndRunConfigurationBase.Launch) && !isTestModule(module)) {
            if (isBndPropertiesFile(file)) {
                configuration.setName(moduleName);
                configuration.getOptions().setBndRunFile(file.getPath());
                return true;
            }
        }
        else if (configuration instanceof BndRunConfigurationBase.Test && isTestModule(module)) {
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
            if (location != null) {
                BndRunConfigurationOptions configurationOptions = configuration.getOptions();
                if ((configuration instanceof BndRunConfigurationBase.Test) && configurationOptions.getTest() != null) {
                    PsiClass psiClass = findTestClass(context.getPsiLocation());
                    if (psiClass != null) {
                        String test = psiClass.getQualifiedName();

                        PsiMethod psiMethod = findTestMethod(context.getPsiLocation());
                        if (psiMethod != null) {
                            test = test + ":" + psiMethod.getName();
                        }

                        return configurationOptions.getTest().equals(test);
                    }
                    return false;
                }

                VirtualFile file = location.getVirtualFile();
                return file != null && !file.isDirectory() && FileUtil.pathsEqual(file.getPath(), configurationOptions.getBndRunFile());
            }
        }

        return false;
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
            aQute.bnd.build.Project project = amdatuIdeaPlugin.getWorkspace().getProject(module.getName());
            return project.get(Constants.TESTCASES) != null;
        } catch (Exception e) {
            LOG.warn("isTestModule check failed for module: " + module.getName(), e);
            return false;
        }
    }
}
