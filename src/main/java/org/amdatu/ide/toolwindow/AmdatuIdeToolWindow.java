package org.amdatu.ide.toolwindow;

import aQute.bnd.build.Workspace;
import aQute.bnd.service.RepositoryPlugin;
import aQute.bnd.version.Version;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.tree.AbstractTreeModel;
import org.amdatu.ide.AmdatuIdePlugin;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

public class AmdatuIdeToolWindow {

    private static final Logger LOG = Logger.getInstance(AmdatuIdeToolWindow.class);

    private JTree repoTree;
    private JTextField searchRepo;
    private JPanel contentPane;
    private JButton search;

    private String myFilter;

    public AmdatuIdeToolWindow(@NotNull Project project) {

        AmdatuIdePlugin amdatuIdePlugin = project.getComponent(AmdatuIdePlugin.class);
        Workspace workspace = amdatuIdePlugin.getWorkspace();

        repoTree.setRootVisible(false);
        try {
            RepoTreeModel repoTreeModel = new RepoTreeModel(workspace);
            repoTree.setModel(repoTreeModel);

            searchRepo.addKeyListener(new KeyAdapter() {

                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        repoTreeModel.filter(searchRepo.getText());
                        repoTree.updateUI();
                    }

                }
            });

            search.addActionListener(e -> {
                repoTreeModel.filter(searchRepo.getText());
                repoTree.updateUI();
            });
        }
        catch (Exception e) {
            LOG.error("Failed to initialize Amdatu IDE toolwindow", e);
        }

    }

    public JPanel getContentPane() {
        return contentPane;
    }

    private class RepositoryPluginNode {
        private RepositoryPlugin myRepositoryPlugin;

        private List<BsnNode> myBsnNodes;

        public RepositoryPluginNode(RepositoryPlugin myRepositoryPlugin) {
            this.myRepositoryPlugin = myRepositoryPlugin;
        }

        public List<BsnNode> getBsnNodes() {
            if (myBsnNodes == null) {
                try {
                    String filter = null;
                    if (myFilter != null && !"".equals(myFilter.trim())) {
                        filter = myFilter;
                        if (!filter.endsWith("*")) {
                            filter += "*";
                        }
                    }
                    myBsnNodes = myRepositoryPlugin
                                    .list(filter)
                                    .stream()
                                    .sorted()
                                    .map(bsn -> new BsnNode(this, bsn))
                                    .collect(Collectors.toList());

                }
                catch (Exception e) {
                    LOG.error("Failed list bundles from repo: " + myRepositoryPlugin.getName(), e);
                    myBsnNodes = Collections.emptyList();
                }
            }
            return myBsnNodes;
        }

        @Override
        public String toString() {
            return myRepositoryPlugin.getName();
        }
    }

    private class BsnNode {
        private RepositoryPluginNode myRepositoryPluginNode;
        private String myBsn;
        private List<Version> myVersions;

        public BsnNode(RepositoryPluginNode repositoryPluginNode, String bsn) {
            myRepositoryPluginNode = repositoryPluginNode;
            myBsn = bsn;
        }

        public List<Version> versions() {
            if (myVersions == null) {
                try {
                    SortedSet<Version> versions = myRepositoryPluginNode.myRepositoryPlugin.versions(myBsn);
                    myVersions = new ArrayList<>(versions);
                }
                catch (Exception e) {
                    LOG.error("Failed list bundles versions for: " + myBsn + " from repo: " +
                                    myRepositoryPluginNode.myRepositoryPlugin.getName(), e);
                    myVersions = Collections.emptyList();
                }
            }
            return myVersions;
        }

        @Override
        public String toString() {
            return myBsn;
        }
    }

    class RepoTreeModel extends AbstractTreeModel {

        private final DefaultMutableTreeNode root;
        private final List<RepositoryPluginNode> repositories;
        private Workspace myWorkspace;

        public RepoTreeModel(Workspace workspace) throws Exception {
            myWorkspace = workspace;
            root = new DefaultMutableTreeNode(0);

            repositories = workspace.getRepositories().stream()
                            .map(RepositoryPluginNode::new)
                            .collect(Collectors.toList());
        }

        @Override
        public Object getRoot() {
            return root;
        }

        @Override
        public Object getChild(Object parent, int index) {
            if (parent instanceof RepositoryPluginNode) {
                return ((RepositoryPluginNode) parent).getBsnNodes().get(index);
            }
            else if (parent instanceof BsnNode) {
                return ((BsnNode) parent).versions().get(index);
            }
            return repositories.get(index);
        }

        @Override
        public int getChildCount(Object parent) {
            if (parent instanceof RepositoryPluginNode) {
                return ((RepositoryPluginNode) parent).getBsnNodes().size();
            }
            else if (parent instanceof BsnNode) {
                return ((BsnNode) parent).versions().size();
            }
            return repositories.size();
        }

        @Override
        public boolean isLeaf(Object node) {
            return node instanceof Version;
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {

        }

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            if (parent instanceof RepositoryPluginNode) {
                return ((RepositoryPluginNode) parent).getBsnNodes().indexOf(child);
            }
            else if (parent instanceof BsnNode) {
                return ((BsnNode) parent).versions().indexOf(child);
            }
            return repositories.indexOf(child);
        }

        public void filter(String filter) {
            myFilter = filter;
            this.repositories.clear();
            try {
                this.repositories.addAll(myWorkspace.getRepositories().stream()
                                .map(RepositoryPluginNode::new)
                                .collect(Collectors.toList()));
            }
            catch (Exception e) {
                LOG.error("Failed to apply filter: " + filter, e);
            }
        }
    }

}