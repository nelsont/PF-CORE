/* $Id: NavTreeModel.java,v 1.22 2006/03/29 20:53:25 schaatser Exp $
 */
package de.dal33t.powerfolder.ui.navigation;

import java.awt.EventQueue;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.event.FolderEvent;
import de.dal33t.powerfolder.event.FolderListener;
import de.dal33t.powerfolder.event.FolderMembershipEvent;
import de.dal33t.powerfolder.event.FolderMembershipListener;
import de.dal33t.powerfolder.event.FolderRepositoryEvent;
import de.dal33t.powerfolder.event.FolderRepositoryListener;
import de.dal33t.powerfolder.event.RecycleBinEvent;
import de.dal33t.powerfolder.event.RecycleBinListener;
import de.dal33t.powerfolder.event.TransferManagerEvent;
import de.dal33t.powerfolder.event.TransferManagerListener;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.model.FolderRepositoryModel;
import de.dal33t.powerfolder.util.ui.TreeNodeList;

/**
 * The model for the navigation tree
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.22 $
 */
public class NavTreeModel extends PFUIComponent implements TreeModel {
    private Set<TreeModelListener> listeners;
    private RootNode rootNode;
    private boolean expandedFriends;
    private boolean expandedJoinedFolders;
    private MyFolderListener myFolderListener;

    public NavTreeModel(Controller controller) {
        super(controller);
        listeners = new HashSet<TreeModelListener>();
        FolderRepository repository = getController().getFolderRepository();
        // UI Updating for the repository
        repository
            .addFolderRepositoryListener(new MyFolderRepositoryListener());

        // UI Updating code for single folders
        myFolderListener = new MyFolderListener();
        addListenerToExsistingFolders();

        // Listen on transfer manager
        controller.getTransferManager().addListener(
            new MyTransferManagerListener());

        // Listen on Recycle Bin
        controller.getRecycleBin().addRecycleBinListener(
            new MyRecycleBinListener());

        // Expand when UI gets opened
        Runnable expander = new Runnable() {
            public void run() {
                expandFriendList();
                expandFolderRepository();
            }
        };
        controller.getUIController().invokeLater(expander);
    }

    private void addListenerToExsistingFolders() {
        FolderRepository repo = getController().getFolderRepository();
        Folder[] folders = repo.getFolders();
        for (int i = 0; i < folders.length; i++) {
            folders[i].addFolderListener(myFolderListener);
            folders[i].addMembershipListener(myFolderListener);
        }
    }

    private class MyFolderListener implements FolderListener,
        FolderMembershipListener
    {
        // FolderListener
        public void remoteContentsChanged(FolderEvent folderEvent) {
            updateFolderTreeNode((Folder) folderEvent.getSource());
        }

        public void folderChanged(FolderEvent folderEvent) {
            // log().debug(folderEvent);
            updateFolderTreeNode((Folder) folderEvent.getSource());
        }

        public void statisticsCalculated(FolderEvent folderEvent) {
        }

        public void syncProfileChanged(FolderEvent folderEvent) {
        }

        public void scanResultCommited(FolderEvent folderEvent) {
        }

        // FolderMembershipListener
        public void memberJoined(FolderMembershipEvent folderEvent) {
            updateFolderTreeNode((Folder) folderEvent.getSource());
        }

        public void memberLeft(FolderMembershipEvent folderEvent) {
            updateFolderTreeNode((Folder) folderEvent.getSource());
        }

        /** update Folder treenode for a folder */
        private void updateFolderTreeNode(Folder folder) {
            // Update tree on that folder
            if (logVerbose) {
                log().verbose("Updating files of folder " + folder);
            }
            FolderRepositoryModel folderRepositoryModel = getUIController()
                .getFolderRepositoryModel();
            if (folderRepositoryModel != null) {
                TreeNodeList list = folderRepositoryModel
                    .getMyFoldersTreeNode();

                if (list != null) {
                    Object[] path = new Object[]{getRoot(), list,
                        folder.getTreeNode()};

                    TreeModelEvent te = new TreeModelEvent(this, path);
                    fireTreeStructureChanged(te);
                }
            }
        }

        public boolean fireInEventDispathThread() {
            return false;
        }
    }

    // Component listener classes *********************************************

    /**
     * Listens on transfermanager and fires change events on tree
     * 
     * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
     */
    private class MyTransferManagerListener implements TransferManagerListener {
        public void downloadRequested(TransferManagerEvent event) {
            updateDownloadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void downloadQueued(TransferManagerEvent event) {
            updateDownloadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void downloadStarted(TransferManagerEvent event) {
            updateDownloadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void downloadAborted(TransferManagerEvent event) {
            updateDownloadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void downloadBroken(TransferManagerEvent event) {
            updateDownloadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void downloadCompleted(TransferManagerEvent event) {
            updateDownloadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void completedDownloadRemoved(TransferManagerEvent event) {
            updateDownloadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void pendingDownloadEnqueud(TransferManagerEvent event) {
            updateUploadsTreeNode();
        }

        public void uploadRequested(TransferManagerEvent event) {
            updateUploadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void uploadStarted(TransferManagerEvent event) {
            updateUploadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void uploadAborted(TransferManagerEvent event) {
            updateUploadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void uploadBroken(TransferManagerEvent event) {
            updateUploadsTreeNode();
            updateFolderTreeNode(event);
        }

        public void uploadCompleted(TransferManagerEvent event) {
            updateUploadsTreeNode();
            updateFolderTreeNode(event);
        }

        public boolean fireInEventDispathThread() {
            return false;
        }

        private void updateDownloadsTreeNode() {
            TreeModelEvent te = new TreeModelEvent(this, new Object[]{
                getRoot(), getRootNode().DOWNLOADS_NODE});
            fireTreeNodesChangedEvent(te);
        }

        private void updateUploadsTreeNode() {
            TreeModelEvent te = new TreeModelEvent(this, new Object[]{
                getRoot(), getRootNode().UPLOADS_NODE});
            fireTreeNodesChangedEvent(te);
        }

        private void updateFolderTreeNode(TransferManagerEvent event) {
            Folder folder = event.getFile().getFolder(
                getController().getFolderRepository());
            if (folder == null) {
                return;
            }
            if (folder.isTransferring()) {
                getUIController().getBlinkManager().addBlinking(folder,
                    Icons.FOLDER);
            } else {
                getUIController().getBlinkManager().removeBlinking(folder);
            }
        }
    }

    /**
     * Listener on folder repository
     * 
     * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
     */
    private class MyFolderRepositoryListener implements
        FolderRepositoryListener
    {
        public void folderRemoved(FolderRepositoryEvent e) {
            Folder folder = e.getFolder();
            folder.removeFolderListener(myFolderListener);
            folder.removeMembershipListener(myFolderListener);

            // Select my folders
            getUIController().getControlQuarter().setSelected(
                getUIController().getFolderRepositoryModel()
                    .getMyFoldersTreeNode());
        }

        public void folderCreated(FolderRepositoryEvent e) {
            expandFolderRepository();
            Folder folder = e.getFolder();
            folder.addFolderListener(myFolderListener);
            folder.addMembershipListener(myFolderListener);

            // Select folder
            getUIController().getControlQuarter().setSelected(folder);
        }

        public void maintenanceStarted(FolderRepositoryEvent e) {
            updateFolderTreeNode(e);
        }

        public void maintenanceFinished(FolderRepositoryEvent e) {
            updateFolderTreeNode(e);
        }

        public boolean fireInEventDispathThread() {
            return false;
        }

        private void updateFolderTreeNode(FolderRepositoryEvent event) {
            Folder folder = event.getFolder();
            if (folder == null) {
                return;
            }
            if (folder.isTransferring() || folder.isScanning()) {
                getUIController().getBlinkManager().addBlinking(folder,
                    Icons.FOLDER);
            } else {
                getUIController().getBlinkManager().removeBlinking(folder);
            }
        }
    }

    private class MyRecycleBinListener implements RecycleBinListener {

        public void fileAdded(RecycleBinEvent e) {
            TreeModelEvent te = new TreeModelEvent(this, new Object[]{
                getRoot(), getRootNode().RECYCLEBIN_NODE});
            fireTreeNodesChangedEvent(te);
        }

        public void fileRemoved(RecycleBinEvent e) {
            TreeModelEvent te = new TreeModelEvent(this, new Object[]{
                getRoot(), getRootNode().RECYCLEBIN_NODE});
            fireTreeNodesChangedEvent(te);
        }

        public void fileUpdated(RecycleBinEvent e) {

        }
    }

    public RootNode getRootNode() {
        if (rootNode == null) {
            // Init lazily
            synchronized (this) {
                if (rootNode == null) {
                    rootNode = new RootNode(getController(), this);
                    // rootNode.initalizeChildren();
                }
            }
        }
        return rootNode;
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    public Object getChild(Object parent, int index) {
        return ((TreeNode) parent).getChildAt(index);
    }

    public int getIndexOfChild(Object parent, Object child) {
        return ((TreeNode) parent).getIndex((TreeNode) child);
    }

    public Object getRoot() {
        return getRootNode();
    }

    public int getChildCount(Object parent) {
        return ((TreeNode) parent).getChildCount();
    }

    public boolean isLeaf(Object node) {
        if (node == this) {
            return true;
        }
        TreeNode treeNode = (TreeNode) node;
        return treeNode.getChildCount() == 0;
    }

    // Listener handling code *************************************************

    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    /**
     * Fires a treeStructureChanged on the nav ui tree
     * 
     * @param e
     */
    public void fireTreeStructureChanged(final TreeModelEvent e) {
        ControlQuarter controlQuarter = getController().getUIController()
            .getControlQuarter();
        if (controlQuarter == null) {
            return;
        }
        final JTree tree = controlQuarter.getTree();
        if (tree == null) {
            return;
        }
        Runnable runner = new Runnable() {
            public void run() {
                // final TreePath path = e.getTreePath();
                // boolean expandedTmp = false;
                // boolean selectionExpandedTmp = false;
                // boolean selectionVisibleTmp = false;
                // if (path != null) {
                // expandedTmp = tree.isExpanded(path);
                // }
                // final TreePath selectedPath = tree.getSelectionPath();
                // if (selectedPath != null) {
                // selectionExpandedTmp = tree.isExpanded(selectedPath);
                // selectionVisibleTmp = tree.isVisible(selectedPath);
                // }
                // final boolean expandedFinal = expandedTmp;
                // final boolean selectionExpandedFinal = selectionExpandedTmp;
                // final boolean selectionVisibleFinal = selectionVisibleTmp;

                // Object[] pathtmp = e.getPath();
                // int count = 0;
                // for (Object tmp : pathtmp) {
                // log().debug(count++ + " " + tmp);
                // }
                for (TreeModelListener listener : listeners) {
                    listener.treeStructureChanged(e);
                }

                // if (expandedFinal) {
                // tree.expandPath(path);
                // }
                // if (selectionExpandedFinal) {
                // tree.expandPath(selectedPath);
                // }
                //
                // if (selectionVisibleFinal) {
                // tree.makeVisible(selectedPath);
                // }
                // TreePath lastExpanded = getController().getUIController()
                // .getControlQuarter().getLastExpandedPath();
                // if (lastExpanded != null) {
                // if (!tree.isExpanded(lastExpanded)) {
                // tree.expandPath(lastExpanded);
                // }
                // }
            }
        };
        if (EventQueue.isDispatchThread()) {
            runner.run();

        } else {
            EventQueue.invokeLater(runner);
        }
    }

    /**
     * Fires a changeevent on the nav ui tree
     * 
     * @param e
     */
    public void fireTreeNodesChangedEvent(final TreeModelEvent e) {
        ControlQuarter controlQuarter = getController().getUIController()
            .getControlQuarter();
        if (controlQuarter == null) {
            return;
        }
        final JTree tree = controlQuarter.getTree();
        if (tree == null) {
            return;
        }
        Runnable runner = new Runnable() {
            public void run() {
                // final TreePath path = e.getTreePath();
                // boolean expandedTmp = false;
                // boolean selectionExpandedTmp = false;
                // boolean selectionVisibleTmp = false;
                // if (path != null) {
                // expandedTmp = tree.isExpanded(path);
                // }
                // final TreePath selectedPath = tree.getSelectionPath();
                // if (selectedPath != null) {
                // selectionExpandedTmp = tree.isExpanded(selectedPath);
                // selectionVisibleTmp = tree.isVisible(selectedPath);
                // }
                // final boolean expandedFinal = expandedTmp;
                // final boolean selectionExpandedFinal = selectionExpandedTmp;
                // final boolean selectionVisibleFinal = selectionVisibleTmp;

                for (TreeModelListener listener : listeners) {
                    listener.treeNodesChanged(e);
                }

                // if (expandedFinal) {
                // tree.expandPath(path);
                // }
                // if (selectionExpandedFinal) {
                // tree.expandPath(selectedPath);
                // }
                // if (selectionVisibleFinal) {
                // tree.makeVisible(selectedPath);
                // }
            }
        };

        if (EventQueue.isDispatchThread()) {
            runner.run();
        } else {
            EventQueue.invokeLater(runner);
        }
    }

    /*
     * Helper code ************************************************************
     */

    /**
     * Expands the friends treenode
     */
    public void expandFriendList() {
        if (!getUIController().isStarted()) {
            return;
        }
        if (expandedFriends) {
            return;
        }
        if (getController().getUIController().getNodeManagerModel()
            .getFriendsTreeNode().getChildCount() > 0)
        {
            log().warn("Expanding friendlist");
            Runnable runner = new Runnable() {
                public void run() {
                    TreePath path = getController().getUIController()
                        .getNodeManagerModel().getFriendsTreeNode().getPathTo();
                    getController().getUIController().getControlQuarter()
                        .getUITree().expandPath(path);
                    expandedFriends = true;
                }
            };
            if (EventQueue.isDispatchThread()) {
                runner.run();
            } else {
                EventQueue.invokeLater(runner);
            }
        }
    }

    /**
     * Expands the not in friendslist treenode. #376
     */
    public void expandNotInFriendsList() {
        if (!getUIController().isStarted()) {
            return;
        }
        final TreeNodeList treeNode = getController().getUIController()
            .getNodeManagerModel().getNotInFriendsTreeNodes();
        if (treeNode.getChildCount() == 1) {
            log().warn("Expanding not friendlist");
            Runnable runner = new Runnable() {
                public void run() {
                    TreePath path = treeNode.getPathTo();
                    getController().getUIController().getControlQuarter()
                        .getUITree().expandPath(path);
                }
            };
            if (EventQueue.isDispatchThread()) {
                runner.run();
            } else {
                EventQueue.invokeLater(runner);
            }
        }
    }

    /**
     * Expands the folder repository, only done once
     */
    private void expandFolderRepository() {
        TreeNodeList myFoldersTreeNode = getUIController()
            .getFolderRepositoryModel().getMyFoldersTreeNode();
        if (myFoldersTreeNode.getChildCount() > 0 && !expandedJoinedFolders) {
            log().verbose("Expanding foined folders on navtree");
            // Expand joined folders
            getController().getUIController().getControlQuarter().getUITree()
                .expandPath(myFoldersTreeNode.getPathTo());
            expandedJoinedFolders = true;
        }
    }
}