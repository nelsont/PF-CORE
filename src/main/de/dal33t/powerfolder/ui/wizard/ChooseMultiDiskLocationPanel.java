/*
 * Copyright 2004 - 2008 Christian Sprajc. All rights reserved.
 *
 * This file is part of PowerFolder.
 *
 * PowerFolder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * PowerFolder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id$
 */
package de.dal33t.powerfolder.ui.wizard;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.debug.FormDebugPanel;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.clientserver.ServerClientEvent;
import de.dal33t.powerfolder.clientserver.ServerClientListener;
import de.dal33t.powerfolder.security.OnlineStorageSubscription;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.SyncProfile;
import static de.dal33t.powerfolder.disk.SyncProfile.AUTOMATIC_SYNCHRONIZATION;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.*;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.action.BaseAction;
import de.dal33t.powerfolder.util.*;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.os.Win32.WinUtils;
import de.dal33t.powerfolder.util.ui.*;
import de.dal33t.powerfolder.util.ui.SwingWorker;
import jwf.WizardPanel;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A generally used wizard panel for choosing a disk location for a folder.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.9 $
 */
public class ChooseMultiDiskLocationPanel extends PFWizardPanel {

    // Some standard user directory names from various OS.
    private static final String USER_DIR_CONTACTS = "Contacts";
    private static final String USER_DIR_DESKTOP = "Desktop";
    private static final String USER_DIR_DOCUMENTS = "Documents";
    // Ubuntu mail client
    private static final String USER_DIR_EVOLUTION = ".evolution";
    private static final String USER_DIR_FAVORITES = "Favorites";
    private static final String USER_DIR_LINKS = "Links";
    private static final String USER_DIR_MUSIC = "Music";
    private static final String USER_DIR_PICTURES = "Pictures";
    private static final String USER_DIR_RECENT_DOCUMENTS = "Recent Documents";
    private static final String USER_DIR_VIDEOS = "Videos";

    // Vista has issues with these, so instantiate separately
    private static String userDirMyDocuments;
    private static String userDirMyMusic;
    private static String userDirMyPictures;
    private static String userDirMyVideos;
    private static String appsDirOutlook;

    private static final String APPS_DIR_FIREFOX = "Mozilla" + File.separator
        + "Firefox";
    private static final String APPS_DIR_SUNBIRD = "Mozilla" + File.separator
        + "Sunbird";
    private static final String APPS_DIR_THUNDERBIRD = "Thunderbird";
    private static final String APPS_DIR_FIREFOX2 = "firefox"; // Linux
    private static final String APPS_DIR_SUNBIRD2 = "sunbird"; // Linux
    private static final String APPS_DIR_THUNDERBIRD2 = "thunderbird"; // Linux

    static {
        if (WinUtils.getInstance() != null) {
            appsDirOutlook = WinUtils.getInstance().getSystemFolderPath(
                WinUtils.CSIDL_LOCAL_SETTINGS_APP_DATA, false)
                + File.separator + "Microsoft" + File.separator + "Outlook";
            userDirMyDocuments = WinUtils.getInstance().getSystemFolderPath(
                WinUtils.CSIDL_PERSONAL, false);
            userDirMyMusic = WinUtils.getInstance().getSystemFolderPath(
                WinUtils.CSIDL_MYMUSIC, false);
            userDirMyPictures = WinUtils.getInstance().getSystemFolderPath(
                WinUtils.CSIDL_MYPICTURES, false);
            userDirMyVideos = WinUtils.getInstance().getSystemFolderPath(
                WinUtils.CSIDL_MYVIDEO, false);
        }
    }

    private WizardPanel next;
    private Map<String, File> userDirectories;
    private JLabel folderSizeLabel;
    private JLabel osWarningLabel;

    private JList customDirectoryList;
    private DefaultListModel customDirectoryListModel;
    private JCheckBox backupByOnlineStorageBox;
    private JCheckBox createDesktopShortcutBox;
    private JCheckBox manualSyncCheckBox;
    private JCheckBox sendInviteAfterCB;
    private Action addAction;
    private Action removeAction;
    private JButton removeButton;
    private String initialDirectory;
    private List<JCheckBox> boxes;
    private ServerClientListener listener;

    /**
     * Creates a new disk location wizard panel. Name of new folder is
     * automatically generated, folder will be secret
     * 
     * @param controller
     * @param next
     *            the next panel after selecting the directory.
     */
    public ChooseMultiDiskLocationPanel(Controller controller, WizardPanel next)
    {
        super(controller);
        Reject.ifNull(next, "Next wizard panel is null");
        this.next = next;
        userDirectories = new TreeMap<String, File>();
    }

    // From WizardPanel *******************************************************

    public WizardPanel next() {
        return next;
    }

    public boolean hasNext() {
        return countSelectedFolders() > 0;
    }

    private int countSelectedFolders() {
        int count = customDirectoryListModel.getSize();

        for (JCheckBox box : boxes) {
            if (box.isSelected()) {
                count++;
            }
        }
        return count;
    }

    public boolean validateNext() {

        List<FolderCreateItem> folderCreateItems = new ArrayList<FolderCreateItem>();

        // Check boxes
        for (String boxName : userDirectories.keySet()) {
            for (JCheckBox box : boxes) {
                if (box.getText().equals(boxName)) {
                    if (box.isSelected()) {
                        FolderCreateItem item = new FolderCreateItem(
                            userDirectories.get(boxName));
                        folderCreateItems.add(item);
                    }
                }
            }
        }

        // Additional folders
        for (int i = 0; i < customDirectoryListModel.size(); i++) {
            String dir = (String) customDirectoryListModel.getElementAt(i);
            File file = new File(dir);
            folderCreateItems.add(new FolderCreateItem(file));
        }

        getWizardContext().setAttribute(FOLDER_CREATE_ITEMS, folderCreateItems);

        getWizardContext().setAttribute(BACKUP_ONLINE_STOARGE,
            backupByOnlineStorageBox.isSelected());
        getWizardContext().setAttribute(CREATE_DESKTOP_SHORTCUT,
            createDesktopShortcutBox.isSelected());

        // Don't allow send after if 2 or more folders.
        getWizardContext().setAttribute(SEND_INVIATION_AFTER_ATTRIBUTE,
            sendInviteAfterCB.isSelected() && countSelectedFolders() <= 1);

        // Change to manual sync if requested.
        if (manualSyncCheckBox.isSelected()) {
            getWizardContext().setAttribute(SYNC_PROFILE_ATTRIBUTE,
                SyncProfile.MANUAL_SYNCHRONIZATION);
        }
        return true;
    }

    protected JPanel buildContent() {

        StringBuilder verticalUserDirectoryLayout = new StringBuilder();
        // Four buttons every row.
        for (int i = 0; i < 1 + userDirectories.size() / 4; i++) {
            verticalUserDirectoryLayout.append("pref, 3dlu, ");
        }

        String verticalLayout = verticalUserDirectoryLayout
            + "9dlu, pref, 3dlu, 40dlu, 3dlu, pref, 12dlu, pref, 3dlu, max(16dlu;pref), 12dlu, pref";
        // info custom add size os w
        // Fixed (60dlu) sizing used so that other components display okay if
        // there is only 1 or two (or even zero) check boxes displayed.
        FormLayout layout = new FormLayout(
            "60dlu, 15dlu, 60dlu, 15dlu, 60dlu, 15dlu, 60dlu, 0:grow",
            verticalLayout);
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();
        int row = 1;
        int col = 1;

        for (String name : userDirectories.keySet()) {
            JCheckBox box = new JCheckBox(name);
            box.setOpaque(false);
            box.addActionListener(new MyActionListener());
            boxes.add(box);
            builder.add(box, cc.xy(col, row));
            if (col == 1) {
                col = 3;
            } else if (col == 3) {
                col = 5;
            } else if (col == 5) {
                col = 7;
            } else {
                row += 2;
                col = 1;
            }
        }
        row += 3;

        builder
            .addLabel(
                Translation
                    .getTranslation("wizard.choose_multi_disk_location.select_additional"),
                cc.xyw(1, row, 6));
        row += 2;

        builder.add(new JScrollPane(customDirectoryList), cc.xyw(1, row, 7));
        row += 2;

        builder.add(new JButton(addAction), cc.xy(1, row));
        builder.add(removeButton, cc.xy(3, row));
        builder.add(folderSizeLabel, cc.xyw(5, row, 3));
        row += 2;

        if (!getController().isLanOnly()
            && PreferencesEntry.USE_ONLINE_STORAGE
                .getValueBoolean(getController()))
        {
            builder.add(backupByOnlineStorageBox, cc.xyw(1, row, 3));
        }
        if (OSUtil.isWindowsSystem()) {
            builder.add(createDesktopShortcutBox, cc.xyw(5, row, 3));
        }
        row += 2;

        // Send Invite
        if (getController().isBackupOnly()) {
            // Cannot invite in backup only mode
            sendInviteAfterCB.setSelected(false);
        } else {
            builder.add(sendInviteAfterCB, cc.xyw(1, row, 3));
        }
        Object object = getWizardContext().getAttribute(SYNC_PROFILE_ATTRIBUTE);
        if (object != null && object.equals(AUTOMATIC_SYNCHRONIZATION)) {
            builder.add(manualSyncCheckBox, cc.xyw(5, row, 3));
        }
        row += 2;

        builder.add(osWarningLabel, cc.xyw(1, row, 6));

        return builder.getPanel();
    }

    private Component createAddRemoveLine() {
        FormLayout layout = new FormLayout("pref, 3dlu, pref, 0:grow", "pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();
        builder.add(new JButton(addAction), cc.xy(1, 1));
        builder.add(new JButton(removeAction), cc.xy(3, 1));
        JPanel panel = builder.getPanel();
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Initalizes all required components
     */
    protected void initComponents() {

        initialDirectory = System.getProperty("user.home");

        findUserDirectories();

        folderSizeLabel = new JLabel();
        osWarningLabel = new JLabel();
        startFolderSizeCalculator();

        boxes = new ArrayList<JCheckBox>();

        addAction = new MyAddAction(getController());
        removeAction = new MyRemoveAction(getController());
        removeButton = new JButton(removeAction);

        customDirectoryListModel = new DefaultListModel();
        customDirectoryList = new JList(customDirectoryListModel);
        customDirectoryList
            .setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        customDirectoryList
            .addListSelectionListener(new MyListSelectionListener());

        // Online Storage integration
        boolean backupByOS = !getController().isLanOnly()
            && PreferencesEntry.USE_ONLINE_STORAGE
                .getValueBoolean(getController())
            && Boolean.TRUE.equals(getWizardContext().getAttribute(
                BACKUP_ONLINE_STOARGE));
        backupByOnlineStorageBox = new JCheckBox(
            Translation
                .getTranslation("wizard.choose_disk_location.backup_by_online_storage"));
        // Is backup suggested?
        if (backupByOS) {
            // Remember last preference...
            Boolean buos = PreferencesEntry.BACKUP_OS
                .getValueBoolean(getController());
            if (buos == null) {
                // .. or default to if last os client login ok.
                buos = getController().getOSClient().isLoggedIn();
            }
            backupByOnlineStorageBox.setSelected(buos);
        }
        backupByOnlineStorageBox.getModel().addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                PreferencesEntry.BACKUP_OS.setValue(getController(),
                    backupByOnlineStorageBox.isSelected());
                if (backupByOnlineStorageBox.isSelected()) {
                    getController().getUIController().getApplicationModel()
                        .getServerClientModel().checkAndSetupAccount();
                }
                startFolderSizeCalculator();
            }
        });
        backupByOnlineStorageBox.setOpaque(false);

        // Create desktop shortcut
        createDesktopShortcutBox = new JCheckBox(
            Translation
                .getTranslation("wizard.choose_disk_location.create_desktop_shortcut"));

        createDesktopShortcutBox.setOpaque(false);

        // Create manual sync cb
        manualSyncCheckBox = new JCheckBox(Translation
            .getTranslation("wizard.choose_disk_location.maual_sync"));

        manualSyncCheckBox.setOpaque(false);

        // Send Invite
        boolean sendInvite = Boolean.TRUE.equals(getWizardContext()
            .getAttribute(SEND_INVIATION_AFTER_ATTRIBUTE));
        sendInviteAfterCB = SimpleComponentFactory.createCheckBox(Translation
            .getTranslation("wizard.choose_disk_location.send_invitation"));
        sendInviteAfterCB.setOpaque(false);
        sendInviteAfterCB.setSelected(sendInvite);

        enableRemoveAction();

        listener = new MyServerClientListener();
    }

    @Override
    protected void afterDisplay() {
        super.afterDisplay();
        if (listener != null) {
            getController().getOSClient().addListener(listener);
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (listener != null) {
            getController().getOSClient().removeListener(listener);
        }
    }

    protected JComponent getPictoComponent() {
        return new JLabel(getContextPicto());
    }

    protected String getTitle() {
        String title = (String) getWizardContext().getAttribute(
            PROMPT_TEXT_ATTRIBUTE);
        if (StringUtils.isNotBlank(title)) {
            return title;
        }
        return Translation
            .getTranslation("wizard.choose_multi_disk_location.title");
    }

    /**
     * Find some generic user directories. Not all will be valid for all os, but
     * that is okay.
     */
    private void findUserDirectories() {
        File userHome = new File(System.getProperty("user.home"));
        addTargetDirectory(userHome, USER_DIR_CONTACTS, Translation
            .getTranslation("user.dir.contacts"), false);
        addTargetDirectory(userHome, USER_DIR_DESKTOP, Translation
            .getTranslation("user.dir.desktop"), false);
        addTargetDirectory(userHome, USER_DIR_DOCUMENTS, Translation
            .getTranslation("user.dir.documents"), false);
        addTargetDirectory(userHome, USER_DIR_EVOLUTION, Translation
            .getTranslation("user.dir.evolution"), true);
        addTargetDirectory(userHome, USER_DIR_FAVORITES, Translation
            .getTranslation("user.dir.favorites"), false);
        addTargetDirectory(userHome, USER_DIR_LINKS, Translation
            .getTranslation("user.dir.links"), false);
        addTargetDirectory(userHome, USER_DIR_MUSIC, Translation
            .getTranslation("user.dir.music"), false);

        // Hidden by Vista.
        if (userDirMyDocuments != null && !OSUtil.isWindowsVistaSystem()) {
            addTargetDirectory(new File(userDirMyDocuments), Translation
                .getTranslation("user.dir.my_documents"), false);
        }
        if (userDirMyMusic != null && !OSUtil.isWindowsVistaSystem()) {
            addTargetDirectory(new File(userDirMyMusic), Translation
                .getTranslation("user.dir.my_music"), false);
        }
        if (userDirMyPictures != null && !OSUtil.isWindowsVistaSystem()) {
            addTargetDirectory(new File(userDirMyPictures), Translation
                .getTranslation("user.dir.my_pictures"), false);
        }
        if (userDirMyVideos != null && !OSUtil.isWindowsVistaSystem()) {
            addTargetDirectory(new File(userDirMyVideos), Translation
                .getTranslation("user.dir.my_videos"), false);
        }

        addTargetDirectory(userHome, USER_DIR_PICTURES, Translation
            .getTranslation("user.dir.pictures"), false);
        addTargetDirectory(userHome, USER_DIR_RECENT_DOCUMENTS, Translation
            .getTranslation("user.dir.recent_documents"), false);
        addTargetDirectory(userHome, USER_DIR_VIDEOS, Translation
            .getTranslation("user.dir.videos"), false);
        if (OSUtil.isWindowsSystem()) {
            String appDataname = System.getenv("APPDATA");
            if (appDataname == null && WinUtils.getInstance() != null) {
                appDataname = WinUtils.getInstance().getSystemFolderPath(
                    WinUtils.CSIDL_APP_DATA, false);
            }
            if (appDataname != null) {
                File appData = new File(appDataname);
                addTargetDirectory(appData, APPS_DIR_FIREFOX, Translation
                    .getTranslation("apps.dir.firefox"), false);
                addTargetDirectory(appData, APPS_DIR_SUNBIRD, Translation
                    .getTranslation("apps.dir.sunbird"), false);
                addTargetDirectory(appData, APPS_DIR_THUNDERBIRD, Translation
                    .getTranslation("apps.dir.thunderbird"), false);
                if (appsDirOutlook != null) {
                    addTargetDirectory(appData, appsDirOutlook, Translation
                        .getTranslation("apps.dir.outlook"), false);
                }
            } else {
                Logger.getAnonymousLogger().severe(
                    "Application data directory not found.");
            }
        } else if (OSUtil.isLinux()) {
            File appData = new File("/etc");
            addTargetDirectory(appData, APPS_DIR_FIREFOX2, Translation
                .getTranslation("apps.dir.firefox"), false);
            addTargetDirectory(appData, APPS_DIR_SUNBIRD2, Translation
                .getTranslation("apps.dir.sunbird"), false);
            addTargetDirectory(appData, APPS_DIR_THUNDERBIRD2, Translation
                .getTranslation("apps.dir.thunderbird"), false);
        } else {
            // @todo Anyone know Mac???
        }
    }

    private void startFolderSizeCalculator() {
        SwingWorker worker = new MySwingWorker();
        worker.start();
    }

    /**
     * Adds a generic user directory if if exists for this os.
     * 
     * @param root
     * @param subdir
     * @param translation
     * @param allowHidden
     *            allow display of hidden dirs
     */
    private void addTargetDirectory(File root, String subdir,
        String translation, boolean allowHidden)
    {
        File directory = joinFile(root, subdir);
        addTargetDirectory(directory, translation, allowHidden);
    }

    private static File joinFile(File root, String subdir) {
        return new File(root + File.separator + subdir);
    }

    /**
     * Adds a generic user directory if if exists for this os.
     * 
     * @param translation
     * @param allowHidden
     *            allow display of hidden dirs
     */
    private void addTargetDirectory(File directory, String translation,
        boolean allowHidden)
    {

        // See if any folders already exists for this directory.
        // No reason to show if already subscribed.
        for (Folder folder1 : getController().getFolderRepository()
            .getFolders())
        {
            if (folder1.getDirectory().getFile().getAbsoluteFile().equals(
                directory))
            {
                return;
            }
        }

        if (directory.exists() && directory.isDirectory()
            && (allowHidden || !directory.isHidden()))
        {
            userDirectories.put(translation, directory);
        }
    }

    private void enableRemoveAction() {
        removeAction.setEnabled(!customDirectoryList.getSelectionModel()
            .isSelectionEmpty());
        removeButton.setVisible(removeAction.isEnabled());
    }

    protected void updateButtons() {
        super.updateButtons();
        if (countSelectedFolders() <= 1) {
            sendInviteAfterCB.setEnabled(true);
            sendInviteAfterCB.setVisible(true);
        } else {
            sendInviteAfterCB.setSelected(false);
            sendInviteAfterCB.setEnabled(false);
            sendInviteAfterCB.setVisible(false);
        }
    }

    private class MyAddAction extends BaseAction {

        MyAddAction(Controller controller) {
            super("action_add_directory", controller);
        }

        public void actionPerformed(ActionEvent e) {
            String dir = DialogFactory.chooseDirectory(getController(),
                initialDirectory);
            if (StringUtils.isBlank(dir)) {
                return;
            }
            File newLocation = new File(dir);
            File localBase = new File(getController().getFolderRepository()
                .getFoldersBasedir());
            if (newLocation.equals(localBase)) {
                DialogFactory
                    .genericDialog(
                        getController(),
                        Translation
                            .getTranslation("wizard.choose_disk_location.local_base.title"),
                        Translation
                            .getTranslation("wizard.choose_disk_location.local_base.text"),
                        GenericDialogType.ERROR);
                return;
            }
            if (new File(dir).exists()) {
                initialDirectory = dir;
                if (!customDirectoryListModel.contains(dir)) {
                    customDirectoryListModel.addElement(dir);
                    updateButtons();
                    startFolderSizeCalculator();
                }
            }
        }
    }

    private class MyRemoveAction extends BaseAction {

        MyRemoveAction(Controller controller) {
            super("action_remove_directory", controller);
        }

        public void actionPerformed(ActionEvent e) {
            customDirectoryListModel.removeRange(customDirectoryList
                .getSelectionModel().getMinSelectionIndex(),
                customDirectoryList.getSelectionModel().getMaxSelectionIndex());
            updateButtons();
            startFolderSizeCalculator();
        }
    }

    private class MyListSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            enableRemoveAction();
        }
    }

    private class MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            updateButtons();
            startFolderSizeCalculator();
        }
    }

    private class MyServerClientListener implements ServerClientListener {

        public void accountUpdated(ServerClientEvent event) {
            startFolderSizeCalculator();
        }

        public void login(ServerClientEvent event) {
            startFolderSizeCalculator();
        }

        public void serverConnected(ServerClientEvent event) {
        }

        public void serverDisconnected(ServerClientEvent event) {
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }

    }

    private class MySwingWorker extends SwingWorker {

        private long totalDirectorySize = 0;
        private boolean valid = true;

        protected void beforeConstruct() {
            folderSizeLabel
                .setText(Translation
                    .getTranslation("wizard.choose_disk_location.calculating_directory_size"));
            folderSizeLabel.setForeground(SystemColor.textText);
        }

        public Object construct() {
            try {
                totalDirectorySize = 0;
                List<File> originalList = new ArrayList<File>();
                for (String boxName : userDirectories.keySet()) {
                    for (JCheckBox box : boxes) {
                        if (box.getText().equals(boxName)) {
                            if (box.isSelected()) {
                                File file = userDirectories.get(boxName);
                                originalList.add(file);
                            }
                        }
                    }
                }
                for (int i = 0; i < customDirectoryListModel.getSize(); i++) {
                    String dir = (String) customDirectoryListModel.elementAt(i);
                    File file = new File(dir);
                    originalList.add(file);
                }

                for (File file : originalList) {
                    totalDirectorySize += FileUtils.calculateDirectorySize(
                        file, 0);
                }

                List<File> finalList = new ArrayList<File>();
                for (String boxName : userDirectories.keySet()) {
                    for (JCheckBox box : boxes) {
                        if (box.getText().equals(boxName)) {
                            if (box.isSelected()) {
                                File file = userDirectories.get(boxName);
                                finalList.add(file);
                            }
                        }
                    }
                }
                for (int i = 0; i < customDirectoryListModel.getSize(); i++) {
                    String dir = (String) customDirectoryListModel.elementAt(i);
                    File file = new File(dir);
                    finalList.add(file);
                }

                // Any selection changes during size calculations?
                if (originalList.size() != finalList.size()
                    || !originalList.containsAll(finalList))
                {
                    valid = false;
                }

            } catch (Exception e) {
                Logger.getAnonymousLogger().log(Level.WARNING, e.toString(), e);
            }
            return null;
        }

        public void finished() {
            if (valid) {
                try {
                    folderSizeLabel.setText(Translation.getTranslation(
                        "wizard.choose_disk_location.total_directory_size",
                        Format.formatBytes(totalDirectorySize)));
                    osWarningLabel.setText("");
                    osWarningLabel.setIcon(null);
                    if (backupByOnlineStorageBox.isSelected()) {
                        ServerClient client = getController().getOSClient();
                        OnlineStorageSubscription storageSubscription = client
                            .getAccount().getOSSubscription();
                        if (client.isConnected()) {
                            long totalStorage = storageSubscription
                                .getStorageSize();
                            long spaceUsed = client.getAccountDetails()
                                .getSpaceUsed();
                            if (spaceUsed + totalDirectorySize > totalStorage) {
                                osWarningLabel
                                    .setText(Translation
                                        .getTranslation("wizard.choose_disk_location.os_over_size"));
                                osWarningLabel.setIcon(Icons
                                    .getIconById(Icons.WARNING));
                            }
                        }
                    }

                } catch (Exception e) {
                    Logger.getAnonymousLogger().log(Level.WARNING,
                        e.toString(), e);
                }
            }
        }
    }

}