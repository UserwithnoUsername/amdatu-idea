package org.amdatu.ide.templating;

import aQute.bnd.build.Workspace;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Key;
import org.amdatu.ide.AmdatuIdePlugin;
import org.amdatu.ide.imp.BndProjectImporter;
import org.apache.commons.io.IOUtils;
import org.bndtools.templating.Resource;
import org.bndtools.templating.ResourceMap;
import org.bndtools.templating.ResourceType;
import org.bndtools.templating.Template;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

public class AmdatuIdeModuleBuilder extends JavaModuleBuilder {

    public static final Key<Template> KEY_TEMPLATE = Key.create("template");
    public static final Key<Map<String, List<Object>>> KEY_ATTRS = Key.create("attrs");

    private WizardContext myWizardContext;


    public AmdatuIdeModuleBuilder() {
    }

    public AmdatuIdeModuleBuilder(WizardContext wizardContext) {
        myWizardContext = wizardContext;
    }

    @Override
    public ModuleType getModuleType() {
    return AmdatuIdeModuleType.getInstance();
    }

    @Nullable
    @Override
    public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
        return new AmdatuIdeModuleSelectTemplateWizardStep(context, this);
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
        return new ModuleWizardStep[]{new AmdatuIdeModuleTemplateParamsStep(wizardContext)};
    }

    @Nullable
    @Override
    public Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
        Module module = super.commitModule(project, model);

        Template myTemplate = myWizardContext.getUserData(KEY_TEMPLATE);
        if (module != null && myTemplate != null) {
            doGenerate(myTemplate, module);
        }

        if (myWizardContext.isCreatingNewProject()) {
            String rootDir = project.getBasePath();
            assert rootDir != null : project;
            ModuleRootModificationUtil.addContentRoot(module, rootDir);
            ModuleRootModificationUtil.setSdkInherited(module);
        } else {
            Workspace workspace = project.getComponent(AmdatuIdePlugin.class).getWorkspace();
            workspace.refresh();
            try {
                aQute.bnd.build.Project bndProject = workspace.getProject(module.getName());
                new BndProjectImporter(project, workspace, Collections.singletonList(bndProject)).resolve(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return module;
    }

    private void doGenerate(Template template, Module module) {
        Map<String, List<Object>> map = new HashMap<>();
        Map<String, List<Object>> userData = myWizardContext.getUserData(KEY_ATTRS);
        if (userData != null) {
            map.putAll(userData);
        }

        map.put("srcDir", singletonList("src"));
        String name = module.getName();
        map.put("basePackageDir", singletonList(name.replaceAll("\\.", "/")));
        map.put("basePackageName", singletonList(name));

        ResourceMap resourceMap;
        try {
            resourceMap = template.generateOutputs(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process myTemplate " + template.getName(), e);
        }

        File moduleRootDir = new File(getContentEntryPath());

        for (Map.Entry<String, Resource> entry : resourceMap.entries()) {
            String relativePath = entry.getKey();
            Resource resource = entry.getValue();
            ResourceType type = resource.getType();
            switch (type) {
                case Folder:
                    File folder = new File(moduleRootDir, relativePath);
                    if (!folder.exists()) {
                        folder.mkdirs();
                    } else if (!folder.isDirectory()) {
                        throw new RuntimeException("File exists but is not a dir" + folder);
                    }
                    break;
                case File:
                    File file = new File(moduleRootDir, relativePath);
                    if (!file.exists()) {
                        try (InputStream is = new BufferedInputStream(resource.getContent());
                             FileOutputStream outputStream = new FileOutputStream(file)) {

                            IOUtils.copy(is, outputStream);

                        } catch (IOException e) {
                            throw new RuntimeException("Failed to write " + file, e);
                        }
                    } else {
                        // TODO Just overwrite?
                        throw new RuntimeException("File exists." + file);
                    }
                    break;
                default:
                    throw new RuntimeException("Unsupported resource type" + type);
            }
        }
    }
}
