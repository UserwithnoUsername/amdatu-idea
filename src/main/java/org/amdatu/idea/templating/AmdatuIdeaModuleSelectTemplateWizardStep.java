package org.amdatu.idea.templating;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.SdkSettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.tree.AbstractTreeModel;
import eu.maxschuster.dataurl.DataUrl;
import eu.maxschuster.dataurl.DataUrlSerializer;
import org.apache.commons.io.IOUtils;
import org.bndtools.templating.Template;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class AmdatuIdeaModuleSelectTemplateWizardStep extends SdkSettingsStep {

    private static final Logger LOG = Logger.getInstance(AmdatuIdeaModuleSelectTemplateWizardStep.class);

    private final LinkedHashMap<String, List<Template>> myTemplateMap;
    private Tree myTree;
    private WizardContext myWizardContext;
    private Template mySelectedTemplate;
    private JCheckBox myOnyLatestCheckBox;

    public AmdatuIdeaModuleSelectTemplateWizardStep(WizardContext context, AmdatuIdeaModuleBuilder moduleBuilder) {
        super(context, moduleBuilder, moduleBuilder::isSuitableSdkType, null);
        myWizardContext = context;
        RepoTemplateLoader templateLoader = new RepoTemplateLoader();

        String templateType;
        if (context.isCreatingNewProject()) {
            templateType = "workspace";
        }
        else {
            templateType = "project";
        }

        myTemplateMap = templateLoader.findTemplates(context.getProject(), templateType).stream()
                        .sorted(Comparator.comparing(Template::getCategory))
                        .collect(Collectors.groupingBy(Template::getCategory, LinkedHashMap::new, Collectors.toList()));
        myTemplateMap.values().forEach(list -> list
                        .sort(Comparator.comparing(Template::getName).thenComparing(Template::getVersion)));

    }

    @Override
    public JComponent getComponent() {
        JPanel component = new JPanel();
        component.setLayout(new BoxLayout(component, BoxLayout.PAGE_AXIS));
        component.setPreferredSize(new Dimension(500, 250));

        component.add(super.getComponent());

        myOnyLatestCheckBox = new JCheckBox("Only show latest version", null, true);

        myTree = new Tree();
        myTree.setRootVisible(false);
        TemplateTreeModel treeModel = new TemplateTreeModel();
        myTree.setModel(treeModel);
        myTree.setCellRenderer(new TemplateTreeCellRenderer());

        myTree.setSelectionRow(0);
        for (int i = 0; i < myTree.getRowCount(); i++) {
            myTree.expandRow(i);
        }

        component.add(new JBScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED));

        myOnyLatestCheckBox.addActionListener(e -> {
            treeModel.update();
            myTree.updateUI();
        });
        component.add(myOnyLatestCheckBox);

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setPreferredSize(new Dimension(480, 60));
        textPane.setFocusable(false);
        textPane.setFocusTraversalKeysEnabled(false);

        myTree.addTreeSelectionListener(e -> {
            Object selected = e.getPath().getLastPathComponent();
            if (selected instanceof Template) {
                mySelectedTemplate = ((Template) selected);
                try {
                    textPane.setText(IOUtils.toString(mySelectedTemplate.getHelpContent()));
                }
                catch (IOException e1) {
                    textPane.setText("<p>Failed to load description<p>");
                }

            }
            else {
                mySelectedTemplate = null;
                textPane.setText("");
            }
        });

        component.add(textPane);
        return component;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myTree;
    }

    @Override
    public void _init() {

    }

    @Override
    public void _commit(boolean finishChosen) {

    }

    @Override
    public Icon getIcon() {
        return null;
    }

    class TemplateTreeModel extends AbstractTreeModel {

        private final DefaultMutableTreeNode myRoot;
        private LinkedHashMap<String, List<Template>> myFilteredTemplateMap;

        public TemplateTreeModel() {
            update();
            myRoot = new DefaultMutableTreeNode(0);
        }

        @Override
        public Object getRoot() {
            return myRoot;
        }

        @Override
        public Object getChild(Object parent, int index) {
            if (parent == myRoot) {
                ArrayList<String> groups = new ArrayList<>(myFilteredTemplateMap.keySet());
                return groups.get(index);
            }
            else if (parent instanceof String) {
                return myFilteredTemplateMap.get(parent).get(index);
            }
            else {
                throw new RuntimeException("Unknown parent " + parent);
            }
        }

        @Override
        public int getChildCount(Object parent) {
            if (parent == myRoot) {
                return myFilteredTemplateMap.size();
            }
            else if (parent instanceof String) {
                return myFilteredTemplateMap.get(parent).size();
            }
            else {
                return 0;
            }
        }

        @Override
        public boolean isLeaf(Object node) {
            return node != myRoot && node instanceof String && !myFilteredTemplateMap.containsKey(node);
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {

        }

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            if (parent == myRoot && child instanceof String) {
                ArrayList<String> groups = new ArrayList<>(myFilteredTemplateMap.keySet());
                return groups.indexOf(child);
            }
            else if (parent instanceof String && child instanceof Template) {
                return myFilteredTemplateMap.get(parent).indexOf(child);
            }
            else {
                throw new RuntimeException("Unknown parent child combination parent: " + parent + " child: " + child);
            }
        }

        public void update() {
            myFilteredTemplateMap = new LinkedHashMap<>();
            if (myOnyLatestCheckBox.isSelected()) {
                for (Map.Entry<String, List<Template>> entry : myTemplateMap.entrySet()) {
                    Map<String, Template> stringTemplateMap = new HashMap<>();
                    for (Template template : entry.getValue()) {
                        if (!stringTemplateMap.containsKey(template.getName())
                                        || stringTemplateMap.get(template.getName()).getVersion()
                                        .compareTo(template.getVersion()) <= 0) {
                            stringTemplateMap.put(template.getName(), template);
                        }
                    }
                    ArrayList<Template> list = new ArrayList<>(stringTemplateMap.values());
                    list.sort(Comparator.comparing(Template::getName));
                    myFilteredTemplateMap.put(entry.getKey(), list);
                }
            }
            else {
                myFilteredTemplateMap = myTemplateMap;
            }
        }
    }

    class TemplateTreeCellRenderer extends ColoredTreeCellRenderer {

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                        boolean leaf, int row, boolean hasFocus) {
            if (value instanceof String) {
                String category = (String) value;

                if (category.contains("/")) {
                    // The bndtools templates have some 'mmm /', 'nnnn /' prefix
                    category = category.substring(category.indexOf("/") + 1);
                }

                append(category, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            }
            else if (value instanceof Template) {
                Template template = (Template) value;

                Icon icon = loadTemplateIcon(template);
                setIcon(icon);

                append(template.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append(" - " + template.getVersion(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                append(" (" + template.getShortDescription() + ")", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
            }
        }

    }

    static Icon loadTemplateIcon(Template template) {
        Icon icon = AllIcons.Nodes.Module;
        URI iconUri = template.getIcon();
        if (iconUri != null) {
            try {
                byte[] data;
                if (iconUri.getScheme().equals("data")) {
                    DataUrl dataUrl = new DataUrlSerializer().unserialize(iconUri.toString());
                    data = dataUrl.getData();
                }
                else {
                    data = IOUtils.toByteArray(iconUri.toURL().openStream());
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                icon = IconUtil.createImageIcon((Image) image);
            }
            catch (IOException e) {
                LOG.warn("Failed to load icon for template: " + template, e);
            }
        }
        return icon;
    }

    @Override
    public void updateDataModel() {
        super.updateDataModel();
        myWizardContext.putUserData(AmdatuIdeaModuleBuilder.KEY_TEMPLATE, mySelectedTemplate);
        myWizardContext.setProjectBuilder(new AmdatuIdeaModuleBuilder(myWizardContext));
    }
}
