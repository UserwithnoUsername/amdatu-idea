package org.amdatu.idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.util.Arrays;
import java.util.stream.Stream;

public class RunAllIntegrationTestsAction extends AbstractRunTestsAction {

    public RunAllIntegrationTestsAction() {
        super("Integration test", "org.amdatu.idea.run.BndRunConfigurationProducer$Test");
    }

    private boolean isIntegrationTestProject(VirtualFile moduleFile) {
        return moduleFile.getName().endsWith(".test");
    }

    @Override
    String getModuleName(VirtualFile runConfigurationTarget) {
        return runConfigurationTarget.getName();
    }

    @Override
    void customizeConfiguration(Element element, VirtualFile runConfigurationTarget) {
        String moduleName = getModuleName(runConfigurationTarget);
        // set bnd configuration options
        Element bndRunFileOption = new Element("option");
        bndRunFileOption.setAttribute("name", "bndRunFile");
        bndRunFileOption.setAttribute("value", "$PROJECT_DIR$/" + moduleName + "/bnd.bnd");
        element.addContent(bndRunFileOption);

        // set module name
        Element moduleOption = new Element("option");
        moduleOption.setAttribute("name", "moduleName");
        moduleOption.setAttribute("value", moduleName);
        element.addContent(moduleOption);

        // pass parent envs
        Element passParentEnvsOption = new Element("option");
        passParentEnvsOption.setAttribute("name", "passParentEnvs");
        passParentEnvsOption.setAttribute("value", "true");
        element.addContent(passParentEnvsOption);

        // working directory
        Element workingDirectoryOption = new Element("option");
        workingDirectoryOption.setAttribute("name", "workingDirectory");
        workingDirectoryOption.setAttribute("value", "$PROJECT_DIR$/" + moduleName);
        element.addContent(workingDirectoryOption);
    }

    @Override
    Stream<VirtualFile> getRunConfigurationTargets(Project project) {
        return Arrays.stream(ProjectRootManager.getInstance(project).getContentRootsFromAllModules())
                .filter(root -> isIntegrationTestProject(root));
    }
}