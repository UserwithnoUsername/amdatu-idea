package org.amdatu.idea.templating;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.uiDesigner.core.Spacer;
import org.bndtools.templating.Template;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;
import static org.amdatu.idea.templating.AmdatuIdeaModuleBuilder.KEY_ATTRS;
import static org.amdatu.idea.templating.AmdatuIdeaModuleBuilder.KEY_TEMPLATE;

class AmdatuIdeaModuleTemplateParamsStep extends ModuleWizardStep {

    private List<Consumer<Map<String, List<Object>>>> attrUpdaters = new ArrayList<>();

    private WizardContext myContext;

    AmdatuIdeaModuleTemplateParamsStep(WizardContext context) {
        myContext = context;
    }

    @Override
    public JComponent getComponent() {
        JPanel component = new JPanel();
        component.setLayout(new BoxLayout(component, BoxLayout.PAGE_AXIS));
        component.setPreferredSize(new Dimension(500, 250));

        Template template = getTemplate();
        if (template == null) {
            return component;
        }

        JPanel attrPanel = new JPanel();
        attrPanel.setLayout(new BoxLayout(attrPanel, BoxLayout.PAGE_AXIS));
        try {

            ObjectClassDefinition metadata = template.getMetadata();
            AttributeDefinition[] attributeDefinitions = metadata.getAttributeDefinitions(ObjectClassDefinition.ALL);
            if (attributeDefinitions != null && attributeDefinitions.length > 0) {
                for (AttributeDefinition attributeDefinition : attributeDefinitions) {
                    String[] defaultValueArray = attributeDefinition.getDefaultValue();
                    String defaultValue = null;
                    if (defaultValueArray != null && defaultValueArray.length == 1) {
                        defaultValue = defaultValueArray[0];
                    }

                    JPanel attributePanel = new JPanel();
                    attributePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
                    attributePanel.setMaximumSize(new Dimension(4000, 30));

                    JLabel label = new JLabel(attributeDefinition.getName());
                    label.setPreferredSize(new Dimension(120, 30));

                    JTextField textField = new JTextField(defaultValue) {
                        @Override
                        public Dimension getPreferredSize() {
                            return new Dimension(getParent().getWidth() - 140, 30);
                        }
                    };

                    attrUpdaters.add(map -> map.put(attributeDefinition.getName(),
                                    singletonList(textField.getText().trim())));

                    attributePanel.add(label);
                    attributePanel.add(textField);
                    attrPanel.add(attributePanel);
                }

            }
            int attributeCount = attributeDefinitions != null ? attributeDefinitions.length : 0;
            attrPanel.setPreferredSize(new Dimension(330, attributeCount * 30));
            Spacer spacer = new Spacer();
            spacer.setPreferredSize(new Dimension(1000, 1000));
            attrPanel.add(spacer);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        component.add(attrPanel);

        return component;
    }

    private Template getTemplate() {
        return myContext.getUserData(KEY_TEMPLATE);
    }

    @Override
    public boolean isStepVisible() {
        try {
            Template template = getTemplate();
            return template != null
                            && template.getMetadata() != null
                            && template.getMetadata().getAttributeDefinitions(-1) != null
                            && template.getMetadata().getAttributeDefinitions(-1).length > 0;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void updateDataModel() {
        myContext.putUserData(KEY_ATTRS, getAttributes());
    }

    private Map<String, List<Object>> getAttributes() {
        Map<String, List<Object>> map = new HashMap<>();
        attrUpdaters.forEach(consumer -> consumer.accept(map));
        return map;
    }
}
