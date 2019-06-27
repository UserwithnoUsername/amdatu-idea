/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.amdatu.idea.actions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class RunUnitTestsAction extends AbstractRunTestsAction {

    public RunUnitTestsAction() {
        super("unit test", "com.intellij.execution.junit.testDiscovery.JUnitTestDiscoveryConfigurationProducer");
    }

    boolean isTestModule(Module module) {
        return Arrays.stream(ModuleRootManager.getInstance(module).getContentEntries())
                .flatMap(entry -> Arrays.stream(entry.getSourceFolders()))
                .anyMatch(this::isTestFolder);
    }

    private boolean isTestFolder(SourceFolder folder) {
        return folder.isTestSource() && hasJavaFiles(folder);
    }

    private boolean hasJavaFiles(SourceFolder folder) {
        VirtualFile root = folder.getFile();
        if (root == null) {
            return false;
        }

        JavaFileVisitor javaFileVisitor = new JavaFileVisitor();
        VfsUtilCore.visitChildrenRecursively(root, javaFileVisitor);

        return javaFileVisitor.hasJavaFiles();
    }

    @Override
    void customizeConfiguration(Element element, Module module, String programParameters) {
        String moduleName = module.getName();
        // set module name
        Element moduleElement = new Element(MODULE);
        moduleElement.setAttribute(NAME, moduleName);
        element.addContent(moduleElement);

        // set test object
        Element testObjectElement = new Element(OPTION);
        testObjectElement.setAttribute(NAME, "TEST_OBJECT");
        testObjectElement.setAttribute(VALUE, "package");
        element.addContent(testObjectElement);

        if (programParameters != null) {
            Element vmArgumentsElement = new Element(OPTION);
            vmArgumentsElement.setAttribute(NAME, "VM_PARAMETERS");
            vmArgumentsElement.setAttribute(VALUE, programParameters);
            element.addContent(vmArgumentsElement);
        }
    }

    static class JavaFileVisitor extends VirtualFileVisitor<Boolean> {
        private boolean hasJavaFiles = false;
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
            if (!file.isDirectory() && file.getName().endsWith(".java")) {
                hasJavaFiles = true;
            }
            return !hasJavaFiles;
        }
        boolean hasJavaFiles() {
            return hasJavaFiles;
        }
    }

}
