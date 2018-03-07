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
package org.amdatu.ide.run;

import static org.amdatu.ide.AmdatuIdeConstants.BND_EXT;
import static org.amdatu.ide.AmdatuIdeConstants.BND_RUN_EXT;

import aQute.bnd.osgi.Constants;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import junit.framework.TestCase;
import org.amdatu.ide.AmdatuIdePlugin;
import org.jetbrains.annotations.NotNull;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.RunWith;

import java.util.Objects;

public abstract class BndRunConfigurationProducer extends RunConfigurationProducer<BndRunConfigurationBase> {

    private static final Logger LOG = Logger.getInstance(BndRunConfigurationProducer.class);

    public static final String BND_BND = "bnd.bnd";

    protected BndRunConfigurationProducer(@NotNull ConfigurationFactory factory) {
        super(factory);
    }

    @Override
    protected boolean setupConfigurationFromContext(BndRunConfigurationBase configuration, ConfigurationContext context, Ref<PsiElement> source) {
        Location location = context.getLocation();
        PsiElement psiLocation = context.getPsiLocation();

        VirtualFile file = location.getVirtualFile();
        if (configuration instanceof BndRunConfigurationBase.Launch && !isTestModule(context.getModule())) {
            if (isBndPropertiesFile(file)) {
                configuration.setName(context.getModule().getName());
                configuration.getOptions().setBndRunFile(file.getPath());

                return true;
            }
        }
        else if (configuration instanceof BndRunConfigurationBase.Test && isTestModule(context.getModule())) {

            if (isBndPropertiesFile(file) && file.getName().equals(BND_BND)) {
                configuration.setName(context.getModule().getName());
                configuration.getOptions().setBndRunFile(file.getPath());
                configuration.getOptions().setModuleName(context.getModule().getName());
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
                configuration.getOptions().setBndRunFile(getPropertiesFilePath(context.getModule()));
                configuration.getOptions().setModuleName(context.getModule().getName());
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
                if (psiAnnotation.getQualifiedName().equals(org.junit.Test.class.getName())) {
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
                if (psiAnnotation.getQualifiedName().equals(RunWith.class.getName())) {
                    return psiClass; // JUnit 4
                }
            }

            PsiClass parent = psiClass;
            while ((parent = parent.getSuperClass()) != null) {
                if (parent.getQualifiedName().equals(Object.class.getName())) {
                    return null;
                }
                if (parent.getQualifiedName().equals(TestCase.class.getName())) {
                    return psiClass; // JUnit 3
                }
            }
        }

        return null;
    }

    @Override
    public boolean isConfigurationFromContext(BndRunConfigurationBase configuration, ConfigurationContext context) {
        if (getConfigurationFactory() == configuration.getFactory()) {
            Location location = context.getLocation();
            if (location != null) {
                VirtualFile file = location.getVirtualFile();
                return file != null && !file.isDirectory() && FileUtil.pathsEqual(file.getPath(), configuration.getOptions().getBndRunFile());
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

    public static boolean isBndPropertiesFile(VirtualFile file) {
        return file != null && !file.isDirectory() &&
                (BND_EXT.equals(file.getExtension()) || BND_RUN_EXT.equals(file.getExtension()));
    }

    public static String getPropertiesFilePath(Module module) {
        Project project = module.getProject();
        String path = project.getBasePath() + "/" + module.getName() + "/" + BND_BND;
        return path;
    }

    public static boolean isTestModule(Module module) {
        AmdatuIdePlugin amdatuIdePlugin = module.getProject().getComponent(AmdatuIdePlugin.class);
        try {
            aQute.bnd.build.Project project = amdatuIdePlugin.getWorkspace().getProject(module.getName());
            return project.get(Constants.TESTCASES) != null;
        } catch (Exception e) {
            LOG.warn("isTestModule check failed for module: " + module.getName(), e);
            return false;
        }
    }
}
