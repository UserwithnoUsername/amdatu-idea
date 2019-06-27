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
import org.amdatu.idea.run.BndLaunchUtil;
import org.jdom.Element;

public class RunIntegrationTestsAction extends AbstractRunTestsAction {

    public RunIntegrationTestsAction() {
        super("integration test", "org.amdatu.idea.run.BndRunConfigurationProducer$Test");
    }

    boolean isTestModule(Module module) {
        return BndLaunchUtil.isTestModule(module);
    }

    @Override
    void customizeConfiguration(Element element, Module module, String programParameters) {
        // set bnd configuration options
        Element bndRunFileOption = new Element(OPTION);
        bndRunFileOption.setAttribute(NAME, "bndRunFile");
        String modulePathName = module.getModuleFile().getParent().getName();
        bndRunFileOption.setAttribute(VALUE, "$PROJECT_DIR$/" + modulePathName + "/bnd.bnd");
        element.addContent(bndRunFileOption);

        // set module name
        Element moduleOption = new Element(OPTION);
        moduleOption.setAttribute(NAME, "moduleName");
        moduleOption.setAttribute(VALUE, module.getName());
        element.addContent(moduleOption);

        // pass parent envs
        Element passParentEnvsOption = new Element(OPTION);
        passParentEnvsOption.setAttribute(NAME, "passParentEnvs");
        passParentEnvsOption.setAttribute(VALUE, "true");
        element.addContent(passParentEnvsOption);

        // working directory
        Element workingDirectoryOption = new Element(OPTION);
        workingDirectoryOption.setAttribute(NAME, "workingDirectory");
        workingDirectoryOption.setAttribute(VALUE, "$PROJECT_DIR$/" + modulePathName);
        element.addContent(workingDirectoryOption);

        if (programParameters != null) {
            Element vmArgumentsElement = new Element(OPTION);
            vmArgumentsElement.setAttribute(NAME, "programParameters");
            vmArgumentsElement.setAttribute(VALUE, programParameters);
            element.addContent(vmArgumentsElement);
        }
    }

}