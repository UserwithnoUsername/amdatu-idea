package org.amdatu.idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.util.Arrays;
import java.util.stream.Stream;

public class RunUnitTestsAction extends AbstractRunTestsAction {

    public RunUnitTestsAction() {
        super("Unit test", "com.intellij.execution.junit.testDiscovery.JUnitTestDiscoveryConfigurationProducer");
    }

    private boolean isUnitTestProject(VirtualFile moduleFile) {
        return moduleFile.getName().equals("test")
                && moduleFile.getChildren().length > 0
                && !(moduleFile.getChildren().length == 1 && moduleFile.getChildren()[0].getName().equals(".gitignore"));
    }

    @Override
    void customizeConfiguration(Element element, VirtualFile runConfigurationTarget) {
        String moduleName = getModuleName(runConfigurationTarget);
        // set module name
        Element moduleElement = new Element("module");
        moduleElement.setAttribute("name", moduleName);
        element.addContent(moduleElement);

        // set test object
        Element testObjectElement = new Element("option");
        testObjectElement.setAttribute("name", "TEST_OBJECT");
        testObjectElement.setAttribute("value", "package");
        element.addContent(testObjectElement);
    }

    @Override
    String getModuleName(VirtualFile runConfigurationTarget) {
        return runConfigurationTarget.getParent().getName();
    }

    @Override
    Stream<VirtualFile> getRunConfigurationTargets(Project project) {
        return Arrays.stream(ProjectRootManager.getInstance(project).getContentSourceRoots())
                .filter(root -> isUnitTestProject(root));
    }
}
