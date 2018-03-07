package org.amdatu.ide.templating;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public class AmdatuIdeModuleType extends ModuleType<AmdatuIdeModuleBuilder> {

    public static final String ID = "AMDATU_IDE_MODULE_TYPE";

    public AmdatuIdeModuleType() {
        super(ID);
    }

    public static AmdatuIdeModuleType getInstance() {
        return (AmdatuIdeModuleType) ModuleTypeManager.getInstance().findByID(ID);
    }

    @NotNull
    @Override
    public AmdatuIdeModuleBuilder createModuleBuilder() {
        return new AmdatuIdeModuleBuilder();
    }

    @NotNull
    @Override
    public String getName() {
        return "Amdatu IDE";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Amdatu IDE, create a new project / module based on a Bndtools template";
    }

    @Override
    public Icon getNodeIcon(boolean isOpened) {
        return AllIcons.Nodes.Module;
    }

}
