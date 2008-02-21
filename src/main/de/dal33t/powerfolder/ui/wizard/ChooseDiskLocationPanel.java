/* $Id: ChooseDiskLocationPanel.java,v 1.9 2005/11/20 04:26:09 totmacherr Exp $
 */
package de.dal33t.powerfolder.ui.wizard;

import static de.dal33t.powerfolder.disk.Folder.*;
import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.Sizes;
import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderException;
import de.dal33t.powerfolder.disk.FolderSettings;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.ui.dialog.SyncFolderPanel;
import de.dal33t.powerfolder.util.IdGenerator;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.webservice.WebServiceException;
import jwf.WizardPanel;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A generally used wizard panel for choosing a disk location for a folder
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.9 $
 */
public class ChooseDiskLocationPanel extends PFWizardPanel {

    /** The attribute in wizard context, which will be displayed */
    public static final String PROMPT_TEXT_ATTRIBUTE = "disklocation.prompttext";

    /** The folder info object for the targeted folder */
    public static final String FOLDERINFO_ATTRIBUTE = "disklocation.folderinfo";

    /** The folder info object for the targeted folder */
    public static final String SYNC_PROFILE_ATTRIBUTE = "disklocation.syncprofile";

    /**
     * Determines, if the user should be prompted for sending invitation
     * afterwards
     */
    public static final String SEND_INVIATION_AFTERWARDS = "disklocation.sendinvitations";

    /**
     * Used to hold initial dir and any chooser selection changes.
     */
    private String transientDirectory;

    // Some standard user directory names from various OS.
    private static final String USER_DIR_CONTACTS = "Contacts";
    private static final String USER_DIR_DESKTOP = "Desktop";
    private static final String USER_DIR_DOCUMENTS = "Documents";
    private static final String USER_DIR_EVOLUTION = ".evolution"; // Ubuntu
    // mail
    // client
    private static final String USER_DIR_FAVORITES = "Favorites";
    private static final String USER_DIR_LINKS = "Links";
    private static final String USER_DIR_MUSIC = "Music";
    private static final String USER_DIR_MY_DOCUMENTS = "My Documents";
    private static final String USER_DIR_MY_MUSIC = "My Documents"
        + File.separator + "My Music";
    private static final String USER_DIR_MY_PICTURES = "My Documents"
        + File.separator + "My Pictures";
    private static final String USER_DIR_MY_VIDEOS = "My Documents"
        + File.separator + "My Videos";
    private static final String USER_DIR_PICTURES = "Pictures";
    private static final String USER_DIR_RECENT_DOCUMENTS = "Recent Documents";
    private static final String USER_DIR_VIDEOS = "Videos";

    private static final String APPS_DIR_FIREFOX = "Mozilla" + File.separator
        + "Firefox";
    private static final String APPS_DIR_THUNDERBIRD = "Thunderbird";
    private static final String APPS_DIR_OUTLOOK = "Microsoft" + File.separator
        + "Outlook";
    private static final String APPS_DIR_FIREFOX2 = "firefox"; // Linux
    private static final String APPS_DIR_THUNDERBIRD2 = "thunderbird"; // Linux

    private boolean initalized;
    private final String initialLocation;
    private JComponent locationField;
    private ValueModel locationModel;
    private Folder folder;
    private boolean sendInvitations;
    private Map<String, File> userDirectories = new TreeMap<String, File>();
    private JTextField locationTF;
    private JButton locationButton;
    private JRadioButton customRB;
    private JCheckBox backupByOnlineStorageBox;

    /**
     * Creates a new disk location wizard panel. Name of new folder is
     * automatically generated, folder will be secret
     * 
     * @param controller
     */
    public ChooseDiskLocationPanel(Controller controller) {
        super(controller);
        initialLocation = null;
    }

    /**
     * Creates a new disk location wizard panel. Name of new folder is
     * automatically generated, folder will be secret
     * 
     * @param controller
     * @param initialLocation
     */
    public ChooseDiskLocationPanel(Controller controller, String initialLocation)
    {
        super(controller);
        this.initialLocation = initialLocation;
    }

    // Application logic ******************************************************

    /**
     * Method called when pressed ok
     */
    private boolean createFolder() {
        if (StringUtils.isBlank((String) locationModel.getValue())) {
            // Abort
            return false;
        }

        File localBase = new File((String) locationModel.getValue());
        FolderInfo foInfo = (FolderInfo) getWizardContext().getAttribute(
            FOLDERINFO_ATTRIBUTE);
        SyncProfile syncProfile = (SyncProfile) getWizardContext()
            .getAttribute(SYNC_PROFILE_ATTRIBUTE);

        if (syncProfile == null) {
            throw new IllegalArgumentException(
                "Synchronisation profile not set !");
        }

        if (foInfo == null) {
            // Create new folder info
            String name = getController().getMySelf().getNick() + '-'
                + localBase.getName();

            String folderId = '[' + IdGenerator.makeId() + ']';
            boolean secrect = true;

            foInfo = new FolderInfo(name, folderId, secrect);
        }
        boolean useRecycleBin = ConfigurationEntry.USE_RECYCLE_BIN
            .getValueBoolean(getController());

        Boolean sendInvs = (Boolean) getWizardContext().getAttribute(
            SEND_INVIATION_AFTERWARDS);
        sendInvitations = sendInvs == null || sendInvs;

        // Set attribute
        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, foInfo);

        try {
            FolderSettings folderSettings = new FolderSettings(localBase,
                syncProfile, true, useRecycleBin, false);
            folder = getController().getFolderRepository().createFolder(foInfo,
                folderSettings);
            if (OSUtil.isWindowsSystem()) {
                // Add thumbs to ignore pattern on windows systems
                folder.getBlacklist().addPattern(THUMBS_DB);
                if (ConfigurationEntry.USE_PF_ICON
                        .getValueBoolean(getController())) {
                    folder.getBlacklist().addPattern(DESKTOP_INI_FILENAME);
                }
            }
            log().info(
                "Folder '" + foInfo.name
                    + "' created successfully. local copy at "
                    + localBase.getAbsolutePath());
            return true;
        } catch (FolderException ex) {
            log().error("Unable to create new folder " + foInfo, ex);
            ex.show(getController());
            return false;
        }
    }

    // From WizardPanel *******************************************************

    public synchronized void display() {
        if (!initalized) {
            buildUI();
        }
    }

    public boolean hasNext() {
        return locationModel.getValue() != null
            && !StringUtils.isBlank(locationModel.getValue().toString());
    }

    public boolean validateNext(List list) {
        boolean folderCreated = createFolder();

        if (folderCreated) {
            if (backupByOnlineStorageBox.isSelected()
                && getController().getWebServiceClient().isLastLoginOK())
            {
                try {
                    getController().getWebServiceClient().setupFolder(folder);
                } catch (WebServiceException e) {
                    DialogFactory
                        .genericDialog(
                            getController().getUIController().getMainFrame()
                                .getUIComponent(),
                            Translation
                                .getTranslation("foldercreate.dialog.backuperror.title"),
                            Translation
                                .getTranslation("foldercreate.dialog.backuperror.text"),
                                getController().isVerbose(), e);
                    log().error("Unable to backup folder to online storage", e);
                }
            }

            if (SyncProfile.PROJECT_WORK.equals(folder.getSyncProfile())) {
                // Show sync folder panel after created a project folder
                new SyncFolderPanel(getController(), folder).open();
            }
        }
        return folderCreated;
    }

    public WizardPanel next() {
        WizardPanel next;
        if (sendInvitations) {
            next = new SendInvitationsPanel(getController(), true);
        } else {
            next = (WizardPanel) getWizardContext().getAttribute(
                PFWizard.SUCCESS_PANEL);
        }
        return next;
    }

    public boolean canFinish() {
        return false;
    }

    public void finish() {
    }

    // UI building ************************************************************

    /**
     * Builds the ui
     */
    private void buildUI() {
        // init
        initComponents();

        setBorder(Borders.EMPTY_BORDER);

        StringBuilder verticalUserDirectoryLayout = new StringBuilder();
        // Include cutom button in size calculations.
        // Two buttons every row.
        for (int i = 0; i < 1 + userDirectories.size() / 2; i++) {
            verticalUserDirectoryLayout.append("4dlu, pref, ");
        }
        String verticalLayout = "5dlu, pref, 15dlu, pref, "
            + verticalUserDirectoryLayout
            + "15dlu, pref, 4dlu, pref, 15dlu, pref, pref:grow";

        FormLayout layout = new FormLayout(
            "20dlu, pref, 15dlu, left:pref, 15dlu, left:pref:grow",
            verticalLayout);

        PanelBuilder builder = new PanelBuilder(layout, this);
        CellConstraints cc = new CellConstraints();
        int row = 2;

        // Select directory
        builder.add(createTitleLabel(Translation
            .getTranslation("wizard.choosedisklocation.select")), cc.xyw(4,
            row, 3));
        row += 2;

        // Add current wizard pico
        builder.add(new JLabel((Icon) getWizardContext().getAttribute(
            PFWizard.PICTO_ICON)), cc.xywh(2, row, 1, 3,
            CellConstraints.DEFAULT, CellConstraints.TOP));
        row += 2;

        ButtonGroup bg = new ButtonGroup();

        int col = 4;
        for (String name : userDirectories.keySet()) {
            final File file = userDirectories.get(name);
            JRadioButton button = new JRadioButton(name);
            button.setOpaque(false);
            bg.add(button);
            builder.add(button, cc.xy(col, row));
            if (col == 4) {
                col = 6;
            } else {
                row += 2;
                col = 4;
            }

            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doRadio(file.getAbsolutePath());
                }
            });
        }

        // Custom directory.
        customRB = new JRadioButton(Translation
            .getTranslation("user.dir.custom"));
        customRB.setOpaque(false);
        bg.add(customRB);
        builder.add(customRB, cc.xy(col, row));
        customRB.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doRadio(transientDirectory);
            }
        });
        customRB.setSelected(true);
        row += 2;

        String infoText = (String) getWizardContext().getAttribute(
            PROMPT_TEXT_ATTRIBUTE);
        builder.addLabel(infoText, cc.xyw(4, row, 3));
        row += 2;

        builder.add(locationField, cc.xyw(4, row, 3));

        row += 2;
        if (!getController().isLanOnly()) {
            builder.add(backupByOnlineStorageBox, cc.xyw(4, row, 3));
        }

        // initalized
        initalized = true;
    }

    /**
     * Radio button selection.
     * 
     * @param name
     */
    private void doRadio(String name) {
        locationModel.setValue(name);
    }

    /**
     * Initalizes all nessesary components
     */
    private void initComponents() {

        findUserDirectories();

        FolderInfo folderInfo = (FolderInfo) getWizardContext().getAttribute(
            FOLDERINFO_ATTRIBUTE);
        if (folderInfo == null) {
            transientDirectory = ConfigurationEntry.FOLDER_BASEDIR
                .getValue(getController());
        } else {
            Folder folder1 = folderInfo.getFolder(getController());
            if (folder1 == null) {
                transientDirectory = ConfigurationEntry.FOLDER_BASEDIR
                    .getValue(getController());
            } else {
                transientDirectory = folder1.getLocalBase().getAbsolutePath();
            }
        }
        locationModel = new ValueHolder(transientDirectory);

        if (initialLocation != null) {
            locationModel.setValue(initialLocation);
        }

        // Behavior
        locationModel.addValueChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateLocationComponents();
                updateButtons();
            }
        });

        locationField = createLocationField();
        Dimension dims = locationField.getPreferredSize();
        dims.width = Sizes.dialogUnitXAsPixel(147, locationField);
        locationField.setPreferredSize(dims);
        locationField.setBackground(Color.WHITE);

        // Online Storage integration
        backupByOnlineStorageBox = new JCheckBox(Translation
            .getTranslation("foldercreate.dialog.backupbyonlinestorage"));
        backupByOnlineStorageBox.setSelected(false);
        backupByOnlineStorageBox.getModel().addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (backupByOnlineStorageBox.isSelected()) {
                    getController().getUIController()
                        .getWebServiceClientModel().checkAndSetupAccount();
                }
            }
        });
        backupByOnlineStorageBox.setOpaque(false);
    }

    /**
     * Called when the location model changes value. Sets the location text
     * field value and enables the location button.
     */
    private void updateLocationComponents() {
        String value = (String) locationModel.getValue();
        if (value == null) {
            value = transientDirectory;
        }
        locationTF.setText(value);
        locationButton.setEnabled(customRB.isSelected());
    }

    /**
     * Creates a pair of location text field and button.
     * 
     * @param folderInfo
     * @return
     */
    private JComponent createLocationField() {
        FormLayout layout = new FormLayout("100dlu, 4dlu, 15dlu", "pref");

        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        locationTF = new JTextField();
        locationTF.setEditable(false);
        locationTF.setText((String) locationModel.getValue());
        builder.add(locationTF, cc.xy(1, 1));

        locationButton = new JButton("...");
        locationButton.addActionListener(new MyActionListener());
        builder.add(locationButton, cc.xy(3, 1));
        return builder.getPanel();
    }

    /**
     * Find some generic user directories. Not all will be valid for all os, but
     * that is okay.
     */
    private void findUserDirectories() {
        String userHome = System.getProperty("user.home");
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
        addTargetDirectory(userHome, USER_DIR_MY_DOCUMENTS, Translation
            .getTranslation("user.dir.my_documents"), false);
        addTargetDirectory(userHome, USER_DIR_MY_MUSIC, Translation
            .getTranslation("user.dir.my_music"), false);
        addTargetDirectory(userHome, USER_DIR_MY_PICTURES, Translation
            .getTranslation("user.dir.my_pictures"), false);
        addTargetDirectory(userHome, USER_DIR_MY_VIDEOS, Translation
            .getTranslation("user.dir.my_videos"), false);
        addTargetDirectory(userHome, USER_DIR_PICTURES, Translation
            .getTranslation("user.dir.pictures"), false);
        addTargetDirectory(userHome, USER_DIR_RECENT_DOCUMENTS, Translation
            .getTranslation("user.dir.recent_documents"), false);
        addTargetDirectory(userHome, USER_DIR_VIDEOS, Translation
            .getTranslation("user.dir.videos"), false);
        if (OSUtil.isWindowsSystem()) {
            String appData = System.getenv("APPDATA");
            addTargetDirectory(appData, APPS_DIR_FIREFOX, Translation
                .getTranslation("apps.dir.firefox"), false);
            addTargetDirectory(appData, APPS_DIR_THUNDERBIRD, Translation
                .getTranslation("apps.dir.thunderbird"), false);
            addTargetDirectory(appData, APPS_DIR_OUTLOOK, Translation
                .getTranslation("apps.dir.outlook"), false);
        } else if (OSUtil.isLinux()) {
            String appData = "/etc";
            addTargetDirectory(appData, APPS_DIR_FIREFOX2, Translation
                .getTranslation("apps.dir.firefox"), false);
            addTargetDirectory(appData, APPS_DIR_THUNDERBIRD2, Translation
                .getTranslation("apps.dir.thunderbird"), false);
        } else {
            // @todo Anyone know Mac???
        }
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
    private void addTargetDirectory(String root, String subdir,
        String translation, boolean allowHidden)
    {
        File directory = new File(root + File.separator + subdir);
        if (directory.exists() && directory.isDirectory()
            && (allowHidden || !directory.isHidden()))
        {
            userDirectories.put(translation, directory);
        }
    }

    /**
     * Action listener for the location button. Opens a choose dir dialog and
     * sets the location model with the result.
     */
    private class MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String initial = (String) locationModel.getValue();
            String file = DialogFactory.chooseDirectory(getController(),
                initial);
            locationModel.setValue(file);

            // Update this so that if the user clicks other user dirs
            // and then 'Custom', the selected dir will show.
            transientDirectory = file;
        }
    }
}