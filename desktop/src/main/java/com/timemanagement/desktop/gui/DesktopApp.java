package com.timemanagement.desktop.gui;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.dataclass.MicrosoftIdentity;
import com.timemanagement.core.dataclass.MicrosoftOAuthSession;
import com.timemanagement.core.dataclass.TimeEntry;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.LocalStorageAccountManager;
import com.timemanagement.core.program.MicrosoftLoginManager;
import com.timemanagement.core.program.ProfileManager;
import com.timemanagement.core.program.TimeEntryManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

public class DesktopApp {
    private static final String INITIAL_LOGIN_PROMPT_COMPLETED_KEY = "initial-login-prompt-completed";

    private final GoogleLoginManager googleLoginManager;
    private final MicrosoftLoginManager microsoftLoginManager;
    private final LocalStorageAccountManager localStorageAccountManager;
    private final ProfileManager profileManager;
    private final TimeEntryManager timeEntryManager;
    private final DesktopOAuthCredentialStore<GoogleOAuthSession> googleCredentialStore;
    private final DesktopOAuthCredentialStore<MicrosoftOAuthSession> microsoftCredentialStore;
    private final Preferences preferences;
    private GoogleAccount currentAccount;

    public DesktopApp(Path dataDir) {
        JsonDataStore dataStore = new JsonDataStore(dataDir);
        this.googleLoginManager = new GoogleLoginManager(dataStore);
        this.microsoftLoginManager = new MicrosoftLoginManager(dataStore);
        this.localStorageAccountManager = new LocalStorageAccountManager(dataStore);
        this.profileManager = new ProfileManager(dataStore);
        this.timeEntryManager = new TimeEntryManager(dataStore);

        Path oauthDirectory = Path.of(System.getProperty("user.home"), ".time-management");
        this.googleCredentialStore = new DesktopOAuthCredentialStore<>(
                oauthDirectory.resolve("google-oauth-session.enc"),
                GoogleOAuthSession.class,
                "Google OAuth"
        );
        this.microsoftCredentialStore = new DesktopOAuthCredentialStore<>(
                oauthDirectory.resolve("microsoft-oauth-session.enc"),
                MicrosoftOAuthSession.class,
                "Microsoft OAuth"
        );
        this.preferences = Preferences.userNodeForPackage(DesktopApp.class);
        this.currentAccount = localStorageAccountManager.useLocalStorage();
    }

    public static void main(String[] args) {
        Path dataDir = Path.of("data");
        DesktopApp app = new DesktopApp(dataDir);

        if (args.length == 2 && "--screenshot".equals(args[0])) {
            app.exportUiScreenshot(Path.of(args[1]));
            return;
        }

        SwingUtilities.invokeLater(app::show);
    }

    public void show() {
        JFrame frame = new JFrame("Time Management - Desktop");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        DesktopView desktopView = createDesktopView();
        frame.setContentPane(desktopView.panel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        maybePromptForRequiredLogin(frame, desktopView);
    }

    public void exportUiScreenshot(Path outputPath) {
        JPanel panel = createDesktopView().panel();
        panel.setSize(panel.getPreferredSize());
        layoutRecursively(panel);

        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        panel.paint(graphics);
        graphics.dispose();
        try {
            File parent = outputPath.toFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            ImageIO.write(image, "png", outputPath.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write screenshot", e);
        }
    }

    private void layoutRecursively(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                nested.doLayout();
                layoutRecursively(nested);
            }
        }
    }

    private DesktopView createDesktopView() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultTreeModel profileTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Profiles"));
        JTree profileSelectorTree = new JTree(profileTreeModel);
        profileSelectorTree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
        profileSelectorTree.setRootVisible(true);
        profileSelectorTree.setShowsRootHandles(true);

        DefaultListModel<String> timeEntryListModel = new DefaultListModel<>();
        JLabel timeEntryStatusLabel = new JLabel("Select the Profiles root or a profile to view entries.");
        JButton addProfileButton = new JButton("Add Profile");
        JTextField profileNameField = new JTextField();
        JTextField profileTypeField = new JTextField();
        JTextField entryDescriptionField = new JTextField();
        JTextField entryDurationField = new JTextField();
        JButton addEntryButton = new JButton("Add Time Entry");

        JPanel mainPanel = createMainScreenPanel(
                profileSelectorTree,
                profileNameField,
                profileTypeField,
                addProfileButton,
                timeEntryStatusLabel,
                timeEntryListModel,
                entryDescriptionField,
                entryDurationField,
                addEntryButton
        );

        profileSelectorTree.addTreeSelectionListener(event ->
                refreshTimeEntriesForSelection(profileSelectorTree, timeEntryStatusLabel, timeEntryListModel));

        addProfileButton.addActionListener(event -> {
            try {
                ManagedProfile profile = profileManager.createProfile(
                        currentAccount.getAccountId(),
                        profileNameField.getText(),
                        profileTypeField.getText()
                );
                profileNameField.setText("");
                profileTypeField.setText("");
                refreshProfiles(profileTreeModel, profileSelectorTree, profile.getProfileId(), timeEntryStatusLabel, timeEntryListModel);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Profile creation failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        addEntryButton.addActionListener(event -> {
            ProfileTreeNode selectedProfile = selectedProfileNode(profileSelectorTree);
            if (selectedProfile == null) {
                JOptionPane.showMessageDialog(root, "Select a profile in the tree first.", "No profile selected", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                int durationMinutes = Integer.parseInt(entryDurationField.getText().trim());
                timeEntryManager.createEntry(selectedProfile.profileId(), entryDescriptionField.getText(), durationMinutes);
                entryDescriptionField.setText("");
                entryDurationField.setText("");
                refreshTimeEntriesForSelection(profileSelectorTree, timeEntryStatusLabel, timeEntryListModel);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(root, "Duration must be a whole number of minutes.", "Entry creation failed", JOptionPane.ERROR_MESSAGE);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Entry creation failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        root.add(mainPanel, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(1080, 620));

        updateActiveAccount(localStorageAccountManager.useLocalStorage(),
                profileTreeModel,
                profileSelectorTree,
                timeEntryStatusLabel,
                timeEntryListModel,
                addProfileButton,
                addEntryButton);

        return new DesktopView(
                root,
                profileTreeModel,
                profileSelectorTree,
                timeEntryStatusLabel,
                timeEntryListModel,
                addProfileButton,
                addEntryButton
        );
    }

    private void maybePromptForRequiredLogin(JFrame frame, DesktopView desktopView) {
        if (!requiresStartupLogin()) {
            return;
        }
        if (!promptForStartupLogin(frame, desktopView)) {
            frame.dispose();
        }
    }

    private boolean promptForStartupLogin(JFrame frame, DesktopView desktopView) {
        Object[] options = {"Google", "Microsoft", "Cancel"};
        while (true) {
            int choice = JOptionPane.showOptionDialog(
                    frame,
                    "Choose the provider you want to use to sign in.",
                    "Sign-in required",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == JOptionPane.CLOSED_OPTION || choice == 2) {
                return false;
            }
            boolean success = choice == 0
                    ? promptForGoogleWebLogin(frame, desktopView)
                    : promptForMicrosoftEmailLogin(frame, desktopView);
            if (success) {
                return true;
            }
        }
    }

    private boolean promptForGoogleWebLogin(JFrame frame, DesktopView desktopView) {
        try {
            GoogleIdentity identity = new DesktopGoogleOAuthService(
                    DesktopOAuthClientConfig.loadGoogleLogin(null),
                    googleCredentialStore
            ).signInForLogin();
            GoogleAccount account = googleLoginManager.login(identity);
            updateActiveAccount(account,
                    desktopView.profileTreeModel(),
                    desktopView.profileSelectorTree(),
                    desktopView.timeEntryStatusLabel(),
                    desktopView.timeEntryListModel(),
                    desktopView.addProfileButton(),
                    desktopView.addEntryButton());
            return true;
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Google sign-in failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean promptForMicrosoftEmailLogin(JFrame frame, DesktopView desktopView) {
        while (true) {
            String email = promptForLoginEmail(frame, "Microsoft sign-in", "Enter the Microsoft email address you want to use for login.");
            if (email == null) {
                return false;
            }
            try {
                GoogleAccount account = microsoftLoginManager.login(new MicrosoftIdentity(email, email, email));
                updateActiveAccount(account,
                        desktopView.profileTreeModel(),
                        desktopView.profileSelectorTree(),
                        desktopView.timeEntryStatusLabel(),
                        desktopView.timeEntryListModel(),
                        desktopView.addProfileButton(),
                        desktopView.addEntryButton());
                return true;
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "Microsoft sign-in failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String promptForLoginEmail(Component parent, String title, String message) {
        while (true) {
            String email = JOptionPane.showInputDialog(parent, message, title, JOptionPane.PLAIN_MESSAGE);
            if (email == null) {
                return null;
            }
            String normalized = normalizeLoginEmail(email);
            if (normalized != null) {
                return normalized;
            }
            JOptionPane.showMessageDialog(parent, "Enter a valid email address.", title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createMainScreenPanel(JTree profileSelectorTree,
                                         JTextField profileNameField,
                                         JTextField profileTypeField,
                                         JButton addProfileButton,
                                         JLabel timeEntryStatusLabel,
                                         DefaultListModel<String> timeEntryListModel,
                                         JTextField entryDescriptionField,
                                         JTextField entryDurationField,
                                         JButton addEntryButton) {
        JPanel leftSelectorPanel = new JPanel(new BorderLayout(8, 8));
        leftSelectorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Profile Selector"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JPanel profileInput = new JPanel(new GridLayout(0, 1, 0, 8));
        profileInput.add(new JLabel("Profiles are the root selectors in this tree."));
        profileInput.add(new JLabel("Profile name:"));
        profileInput.add(profileNameField);
        profileInput.add(new JLabel("Type (person/service):"));
        profileInput.add(profileTypeField);
        profileInput.add(addProfileButton);

        leftSelectorPanel.add(new JScrollPane(profileSelectorTree), BorderLayout.CENTER);
        leftSelectorPanel.add(profileInput, BorderLayout.SOUTH);

        JPanel rightDetailsPanel = new JPanel(new BorderLayout(8, 8));
        rightDetailsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Details"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JList<String> entriesList = new JList<>(timeEntryListModel);
        JPanel entryInput = new JPanel(new GridLayout(0, 1, 0, 8));
        entryInput.add(new JLabel("Description:"));
        entryInput.add(entryDescriptionField);
        entryInput.add(new JLabel("Duration (minutes):"));
        entryInput.add(entryDurationField);
        entryInput.add(addEntryButton);

        rightDetailsPanel.add(timeEntryStatusLabel, BorderLayout.NORTH);
        rightDetailsPanel.add(new JScrollPane(entriesList), BorderLayout.CENTER);
        rightDetailsPanel.add(entryInput, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSelectorPanel, rightDetailsPanel);
        splitPane.setResizeWeight(0.35);
        splitPane.setBorder(null);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private void updateActiveAccount(GoogleAccount account,
                                     DefaultTreeModel profileTreeModel,
                                     JTree profileSelectorTree,
                                     JLabel timeEntryStatusLabel,
                                     DefaultListModel<String> timeEntryListModel,
                                     JButton addProfileButton,
                                     JButton addEntryButton) {
        currentAccount = account;
        preferences.putBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, !isLoginRequired(account));
        refreshProfiles(profileTreeModel, profileSelectorTree, null, timeEntryStatusLabel, timeEntryListModel);
        addProfileButton.setEnabled(true);
        addEntryButton.setEnabled(true);
    }

    private void refreshProfiles(DefaultTreeModel profileTreeModel,
                                 JTree profileSelectorTree,
                                 String selectedProfileId,
                                 JLabel timeEntryStatusLabel,
                                 DefaultListModel<String> timeEntryListModel) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Profiles");
        if (currentAccount == null) {
            profileTreeModel.setRoot(root);
            profileSelectorTree.setSelectionPath(new TreePath(root.getPath()));
            refreshTimeEntriesForSelection(profileSelectorTree, timeEntryStatusLabel, timeEntryListModel);
            return;
        }
        for (ManagedProfile profile : profileManager.listProfiles(currentAccount.getAccountId())) {
            ProfileTreeNode profileData = new ProfileTreeNode(
                    profile.getProfileId(),
                    profile.getProfileName(),
                    profile.getProfileType()
            );
            root.add(new DefaultMutableTreeNode(profileData));
        }

        profileTreeModel.setRoot(root);

        if (selectedProfileId != null) {
            for (int i = 0; i < root.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
                Object value = child.getUserObject();
                if (value instanceof ProfileTreeNode profileNode && profileNode.profileId().equals(selectedProfileId)) {
                    profileSelectorTree.setSelectionPath(new TreePath(child.getPath()));
                    refreshTimeEntriesForSelection(profileSelectorTree, timeEntryStatusLabel, timeEntryListModel);
                    return;
                }
            }
        }

        profileSelectorTree.setSelectionPath(new TreePath(root.getPath()));
        refreshTimeEntriesForSelection(profileSelectorTree, timeEntryStatusLabel, timeEntryListModel);
    }

    private void refreshTimeEntriesForSelection(JTree profileSelectorTree,
                                                JLabel timeEntryStatusLabel,
                                                DefaultListModel<String> timeEntryListModel) {
        timeEntryListModel.clear();
        if (currentAccount == null) {
            timeEntryStatusLabel.setText("No account is currently active.");
            return;
        }

        ProfileTreeNode selectedProfile = selectedProfileNode(profileSelectorTree);
        if (selectedProfile != null) {
            timeEntryStatusLabel.setText("Time entries for: " + selectedProfile.label());
            for (TimeEntry entry : timeEntryManager.listEntries(selectedProfile.profileId())) {
                timeEntryListModel.addElement(formatEntryLine(entry));
            }
            return;
        }

        timeEntryStatusLabel.setText("All time management entries");
        List<TimeEntry> allEntries = new ArrayList<>();
        for (ManagedProfile profile : profileManager.listProfiles(currentAccount.getAccountId())) {
            allEntries.addAll(timeEntryManager.listEntries(profile.getProfileId()));
        }
        allEntries.sort(Comparator.comparing(TimeEntry::getStartedAt).reversed());
        for (TimeEntry entry : allEntries) {
            timeEntryListModel.addElement(formatEntryLine(entry));
        }
    }

    private ProfileTreeNode selectedProfileNode(JTree profileSelectorTree) {
        Object selected = profileSelectorTree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode selectedNode)) {
            return null;
        }
        Object value = selectedNode.getUserObject();
        return value instanceof ProfileTreeNode profileNode ? profileNode : null;
    }

    private String formatEntryLine(TimeEntry entry) {
        String startedAt = entry.getStartedAt() == null
                ? "(no start time)"
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(entry.getStartedAt());
        return startedAt + " - " + entry.getDescription() + " (" + entry.getDurationMinutes() + "m)";
    }

    private boolean requiresStartupLogin() {
        return isLoginRequired(currentAccount);
    }

    private boolean isLoginRequired(GoogleAccount account) {
        if (account == null) {
            return true;
        }
        String provider = account.getProvider();
        return provider == null || provider.isBlank() || LocalStorageAccountManager.PROVIDER.equalsIgnoreCase(provider);
    }

    private String normalizeLoginEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase();
        int atIndex = email.indexOf('@');
        return atIndex > 0 && atIndex < email.length() - 1 ? email : null;
    }

    private record DesktopView(JPanel panel,
                               DefaultTreeModel profileTreeModel,
                               JTree profileSelectorTree,
                               JLabel timeEntryStatusLabel,
                               DefaultListModel<String> timeEntryListModel,
                               JButton addProfileButton,
                               JButton addEntryButton) {
    }

    private record ProfileTreeNode(String profileId, String profileName, String profileType) {
        String label() {
            return profileName + " (" + profileType + ")";
        }

        @Override
        public String toString() {
            return label();
        }
    }
}
