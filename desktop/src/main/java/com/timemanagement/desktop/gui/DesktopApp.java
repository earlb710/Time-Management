package com.timemanagement.desktop.gui;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.dataclass.MicrosoftOAuthSession;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.LocalStorageAccountManager;
import com.timemanagement.core.program.MicrosoftLoginManager;
import com.timemanagement.core.program.ProfileManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

public class DesktopApp {
    private static final String LOCAL_STORAGE_CARD = "local-storage";
    private static final String GOOGLE_DRIVE_CARD = "google-drive";
    private static final String MICROSOFT_DRIVE_CARD = "microsoft-drive";
    private static final String INITIAL_LOGIN_PROMPT_COMPLETED_KEY = "initial-login-prompt-completed";

    private final GoogleLoginManager googleLoginManager;
    private final MicrosoftLoginManager microsoftLoginManager;
    private final LocalStorageAccountManager localStorageAccountManager;
    private final ProfileManager profileManager;
    private final DesktopOAuthCredentialStore<GoogleOAuthSession> googleCredentialStore;
    private final DesktopOAuthCredentialStore<MicrosoftOAuthSession> microsoftCredentialStore;
    private final Preferences preferences;
    private Path dataDirectory;
    private GoogleAccount currentAccount;

    public DesktopApp(Path dataDir) {
        this.dataDirectory = dataDir;
        JsonDataStore dataStore = new JsonDataStore(dataDir);
        this.googleLoginManager = new GoogleLoginManager(dataStore);
        this.microsoftLoginManager = new MicrosoftLoginManager(dataStore);
        this.localStorageAccountManager = new LocalStorageAccountManager(dataStore);
        this.profileManager = new ProfileManager(dataStore);

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
        frame.setJMenuBar(createMenuBar(desktopView));
        frame.setContentPane(desktopView.panel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        maybePromptForInitialLogin(frame, desktopView);
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

        JLabel storageStatusLabel = new JLabel();
        storageStatusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Storage mode"),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        CardLayout cardLayout = new CardLayout();
        JPanel pagePanel = new JPanel(cardLayout);

        DefaultListModel<String> profileListModel = new DefaultListModel<>();
        JButton addProfileButton = new JButton("Add Profile");
        JTextField profileNameField = new JTextField();
        JTextField profileTypeField = new JTextField();

        pagePanel.add(createLocalStoragePanel(), LOCAL_STORAGE_CARD);
        pagePanel.add(createGoogleDrivePanel(root, storageStatusLabel, profileListModel, addProfileButton), GOOGLE_DRIVE_CARD);
        pagePanel.add(createMicrosoftDrivePanel(root, storageStatusLabel, profileListModel, addProfileButton), MICROSOFT_DRIVE_CARD);

        JList<String> profileList = new JList<>(profileListModel);
        JPanel profilePanel = createProfilePanel(profileList, profileNameField, profileTypeField, addProfileButton);

        addProfileButton.addActionListener(event -> {
            try {
                ManagedProfile profile = profileManager.createProfile(
                        currentAccount.getAccountId(),
                        profileNameField.getText(),
                        profileTypeField.getText()
                );
                profileListModel.addElement(profile.getProfileName() + " (" + profile.getProfileType() + ")");
                profileNameField.setText("");
                profileTypeField.setText("");
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Profile creation failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pagePanel, profilePanel);
        splitPane.setResizeWeight(0.6);
        splitPane.setBorder(null);

        root.add(storageStatusLabel, BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(1080, 620));

        updateActiveAccount(localStorageAccountManager.useLocalStorage(), storageStatusLabel, profileListModel, addProfileButton);
        cardLayout.show(pagePanel, LOCAL_STORAGE_CARD);

        return new DesktopView(root, cardLayout, pagePanel, storageStatusLabel, profileListModel, addProfileButton);
    }

    private JMenuBar createMenuBar(DesktopView desktopView) {
        JMenuBar menuBar = new JMenuBar();
        JMenu storageMenu = new JMenu("Storage");

        JMenuItem localStorageItem = new JMenuItem("Local Storage");
        localStorageItem.addActionListener(event -> {
            desktopView.cardLayout().show(desktopView.pagePanel(), LOCAL_STORAGE_CARD);
            updateActiveAccount(localStorageAccountManager.useLocalStorage(),
                    desktopView.storageStatusLabel(),
                    desktopView.profileListModel(),
                    desktopView.addProfileButton());
        });

        JMenuItem googleDriveItem = new JMenuItem("Connect Google Drive");
        googleDriveItem.addActionListener(event -> showDesktopCard(
                desktopView,
                GOOGLE_DRIVE_CARD,
                "Continue with Google login to identify the active user."
        ));

        JMenuItem microsoftDriveItem = new JMenuItem("Connect Microsoft Drive");
        microsoftDriveItem.addActionListener(event -> showDesktopCard(
                desktopView,
                MICROSOFT_DRIVE_CARD,
                "Continue with Microsoft login to identify the active user."
        ));

        storageMenu.add(localStorageItem);
        storageMenu.add(googleDriveItem);
        storageMenu.add(microsoftDriveItem);
        menuBar.add(storageMenu);
        return menuBar;
    }

    private JPanel createLocalStoragePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Local Storage"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        JLabel content = new JLabel("""
                <html>
                <h2>Local storage is the default</h2>
                <p>The desktop app stores accounts and profiles in the local <b>data/</b> directory by default.</p>
                <p>Use the <b>Storage</b> menu to optionally connect Google Drive or Microsoft Drive without giving up the local profile workflow.</p>
                 <p>Saved browser sign-in sessions are encrypted locally under <b>~/.time-management/</b>.</p>
                 </html>
                 """);
        panel.add(content, BorderLayout.NORTH);
        panel.add(createLocalStorageDirectoryPanel(), BorderLayout.CENTER);
        return panel;
    }

    private void maybePromptForInitialLogin(JFrame frame, DesktopView desktopView) {
        if (preferences.getBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, false)) {
            return;
        }

        Object[] options = {"Google", "Microsoft"};
        while (true) {
            int choice = JOptionPane.showOptionDialog(
                    frame,
                    "The first time you open the app, choose Google or Microsoft login.",
                    "Choose a login",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) {
                preferences.putBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, true);
                showDesktopCard(desktopView, GOOGLE_DRIVE_CARD, "Continue with Google login to identify the active user.");
                return;
            }
            if (choice == 1) {
                preferences.putBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, true);
                showDesktopCard(desktopView, MICROSOFT_DRIVE_CARD, "Continue with Microsoft login to identify the active user.");
                return;
            }
        }
    }

    private void showDesktopCard(DesktopView desktopView, String card, String statusText) {
        desktopView.cardLayout().show(desktopView.pagePanel(), card);
        desktopView.storageStatusLabel().setText(statusText);
    }

    private JPanel createLocalStorageDirectoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JTextField dataDirectoryField = new JTextField(dataDirectory.toAbsolutePath().normalize().toString());
        dataDirectoryField.setEditable(true);
        dataDirectoryField.addActionListener(event -> applyDataDirectoryFromField(panel, dataDirectoryField));
        dataDirectoryField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyDataDirectoryFromField(panel, dataDirectoryField);
            }
        });

        JButton selectFolderButton = new JButton("Select Folder");
        selectFolderButton.addActionListener(event -> selectDesktopDataDirectory(panel, dataDirectoryField));

        JPanel directoryPanel = new JPanel(new BorderLayout(6, 6));
        directoryPanel.setBorder(BorderFactory.createTitledBorder("Data directory"));
        directoryPanel.add(dataDirectoryField, BorderLayout.CENTER);
        directoryPanel.add(selectFolderButton, BorderLayout.EAST);

        panel.add(directoryPanel, BorderLayout.NORTH);
        return panel;
    }

    private void selectDesktopDataDirectory(Component parent, JTextField dataDirectoryField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select data directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        File current = dataDirectory.toAbsolutePath().normalize().toFile();
        if (current.isDirectory()) {
            chooser.setCurrentDirectory(current);
            chooser.setSelectedFile(current);
        }
        if (chooser.showDialog(parent, "Select Folder") == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                Path chosen = selected.toPath().toAbsolutePath().normalize();
                try {
                    Files.createDirectories(chosen);
                    dataDirectory = chosen;
                    dataDirectoryField.setText(chosen.toString());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(parent, ex.getMessage(), "Select folder failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void applyDataDirectoryFromField(Component parent, JTextField dataDirectoryField) {
        String text = dataDirectoryField.getText();
        if (text == null || text.isBlank()) {
            dataDirectoryField.setText(dataDirectory.toAbsolutePath().normalize().toString());
            return;
        }
        try {
            Path typed = Path.of(text.trim()).toAbsolutePath().normalize();
            Files.createDirectories(typed);
            dataDirectory = typed;
            dataDirectoryField.setText(typed.toString());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Select folder failed", JOptionPane.ERROR_MESSAGE);
            dataDirectoryField.setText(dataDirectory.toAbsolutePath().normalize().toString());
        }
    }

    private JPanel createGoogleDrivePanel(JPanel root,
                                          JLabel storageStatusLabel,
                                          DefaultListModel<String> profileListModel,
                                          JButton addProfileButton) {
        JTextField clientIdField = new JTextField(valueOrEmpty(DesktopOAuthClientConfig.defaultGoogleClientId()));
        JPasswordField passphraseField = new JPasswordField();
        JLabel connectionStatusLabel = new JLabel("Google Drive is optional. Enter a client id below to connect it.");

        JButton connectButton = new JButton("Connect Google Drive");
        JButton restoreButton = new JButton("Use Saved Google Session");
        JButton forgetButton = new JButton("Forget Saved Google Session");

        connectButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                GoogleOAuthSession session = new DesktopGoogleOAuthService(
                        DesktopOAuthClientConfig.loadGoogle(clientIdField.getText()),
                        googleCredentialStore
                ).signIn(passphrase);
                GoogleAccount account = googleLoginManager.login(session.getIdentity());
                connectionStatusLabel.setText("Connected as " + account.getDisplayName() + ".");
                updateActiveAccount(account, storageStatusLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Google Drive connection failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        restoreButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                GoogleOAuthSession session = new DesktopGoogleOAuthService(
                        DesktopOAuthClientConfig.loadGoogle(clientIdField.getText()),
                        googleCredentialStore
                ).restoreSession(passphrase).orElseThrow(() -> new IllegalStateException("No saved Google session was found."));
                GoogleAccount account = googleLoginManager.login(session.getIdentity());
                connectionStatusLabel.setText("Restored Google Drive connection for " + account.getDisplayName() + ".");
                updateActiveAccount(account, storageStatusLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Google Drive restore failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        forgetButton.addActionListener(event -> {
            try {
                googleCredentialStore.clear();
                connectionStatusLabel.setText("Saved Google Drive session removed.");
                if (currentAccount != null && "google".equals(currentAccount.getProvider())) {
                    updateActiveAccount(localStorageAccountManager.useLocalStorage(), storageStatusLabel, profileListModel, addProfileButton);
                }
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Google Drive disconnect failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        return createOAuthSetupPanel(
                "Connect Google Drive",
                """
                        <html>
                        <ol>
                          <li>Create a <b>Desktop app</b> OAuth client in Google Cloud.</li>
                          <li>Paste the client id below, or keep using <b>TIME_MANAGEMENT_GOOGLE_CLIENT_ID</b>.</li>
                          <li>Choose a credential passphrase. It encrypts the saved local session file.</li>
                          <li>Click <b>Connect Google Drive</b>. The app opens your browser and listens on a loopback callback.</li>
                          <li>Approve access so the app can use <b>drive.file</b> plus your basic profile scopes.</li>
                        </ol>
                        <p>Requested scopes: %s</p>
                        </html>
                        """.formatted(joinScopes(DesktopOAuthClientConfig.googleDefaultScopes())),
                "Google OAuth client id:",
                clientIdField,
                passphraseField,
                connectionStatusLabel,
                List.of(connectButton, restoreButton, forgetButton)
        );
    }

    private JPanel createMicrosoftDrivePanel(JPanel root,
                                             JLabel storageStatusLabel,
                                             DefaultListModel<String> profileListModel,
                                             JButton addProfileButton) {
        JTextField clientIdField = new JTextField(valueOrEmpty(DesktopOAuthClientConfig.defaultMicrosoftClientId()));
        JPasswordField passphraseField = new JPasswordField();
        JLabel connectionStatusLabel = new JLabel("Microsoft Drive is optional. Enter a client id below to connect it.");

        JButton connectButton = new JButton("Connect Microsoft Drive");
        JButton restoreButton = new JButton("Use Saved Microsoft Session");
        JButton forgetButton = new JButton("Forget Saved Microsoft Session");

        connectButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                MicrosoftOAuthSession session = new DesktopMicrosoftOAuthService(
                        DesktopOAuthClientConfig.loadMicrosoft(clientIdField.getText()),
                        microsoftCredentialStore
                ).signIn(passphrase);
                GoogleAccount account = microsoftLoginManager.login(session.getIdentity());
                connectionStatusLabel.setText("Connected as " + account.getDisplayName() + ".");
                updateActiveAccount(account, storageStatusLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Microsoft Drive connection failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        restoreButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                MicrosoftOAuthSession session = new DesktopMicrosoftOAuthService(
                        DesktopOAuthClientConfig.loadMicrosoft(clientIdField.getText()),
                        microsoftCredentialStore
                ).restoreSession(passphrase).orElseThrow(() -> new IllegalStateException("No saved Microsoft session was found."));
                GoogleAccount account = microsoftLoginManager.login(session.getIdentity());
                connectionStatusLabel.setText("Restored Microsoft Drive connection for " + account.getDisplayName() + ".");
                updateActiveAccount(account, storageStatusLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Microsoft Drive restore failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        forgetButton.addActionListener(event -> {
            try {
                microsoftCredentialStore.clear();
                connectionStatusLabel.setText("Saved Microsoft Drive session removed.");
                if (currentAccount != null && "microsoft".equals(currentAccount.getProvider())) {
                    updateActiveAccount(localStorageAccountManager.useLocalStorage(), storageStatusLabel, profileListModel, addProfileButton);
                }
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(root, ex.getMessage(), "Microsoft Drive disconnect failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        return createOAuthSetupPanel(
                "Connect Microsoft Drive",
                """
                        <html>
                        <ol>
                          <li>Create a public client/app registration in Microsoft Entra ID / Azure.</li>
                          <li>Paste the application client id below, or keep using <b>TIME_MANAGEMENT_MICROSOFT_CLIENT_ID</b>.</li>
                          <li>Choose a credential passphrase to encrypt the saved local session file.</li>
                          <li>Click <b>Connect Microsoft Drive</b>. The app opens your browser and waits for the loopback callback.</li>
                          <li>Approve the requested OneDrive and profile permissions.</li>
                        </ol>
                        <p>Requested scopes: %s</p>
                        </html>
                        """.formatted(joinScopes(DesktopOAuthClientConfig.microsoftDefaultScopes())),
                "Microsoft OAuth client id:",
                clientIdField,
                passphraseField,
                connectionStatusLabel,
                List.of(connectButton, restoreButton, forgetButton)
        );
    }

    private JPanel createOAuthSetupPanel(String title,
                                         String instructions,
                                         String clientIdLabel,
                                         JTextField clientIdField,
                                         JPasswordField passphraseField,
                                         JLabel connectionStatusLabel,
                                         List<JButton> actionButtons) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
        form.add(new JLabel(instructions));

        JPanel clientIdPanel = new JPanel(new BorderLayout(6, 6));
        clientIdPanel.add(new JLabel(clientIdLabel), BorderLayout.NORTH);
        clientIdPanel.add(clientIdField, BorderLayout.CENTER);
        form.add(clientIdPanel);

        JPanel passphrasePanel = new JPanel(new BorderLayout(6, 6));
        passphrasePanel.add(new JLabel("Credential passphrase:"), BorderLayout.NORTH);
        passphrasePanel.add(passphraseField, BorderLayout.CENTER);
        passphrasePanel.add(new JLabel("<html><span style='font-size:9px;color:#666666;'>Used to encrypt and unlock saved sign-in sessions on this computer.</span></html>"), BorderLayout.SOUTH);
        form.add(passphrasePanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (JButton button : actionButtons) {
            buttonPanel.add(button);
        }
        form.add(buttonPanel);
        form.add(connectionStatusLabel);

        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    private JPanel createProfilePanel(JList<String> profileList,
                                      JTextField profileNameField,
                                      JTextField profileTypeField,
                                      JButton addProfileButton) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Profiles"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JPanel profileInput = new JPanel(new GridLayout(0, 1, 0, 8));
        profileInput.add(new JLabel("Profiles are always stored locally for the currently selected storage mode."));
        profileInput.add(new JLabel("Profile name:"));
        profileInput.add(profileNameField);
        profileInput.add(new JLabel("Type (person/service):"));
        profileInput.add(profileTypeField);
        profileInput.add(addProfileButton);
        profileInput.add(new JLabel("Multiple profiles per storage account are supported."));

        panel.add(new JScrollPane(profileList), BorderLayout.CENTER);
        panel.add(profileInput, BorderLayout.SOUTH);
        return panel;
    }

    private char[] readPassphrase(JPasswordField passphraseField) {
        char[] passphrase = passphraseField.getPassword();
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Enter a credential passphrase before continuing.");
        }
        return passphrase;
    }

    private void updateActiveAccount(GoogleAccount account,
                                     JLabel storageStatusLabel,
                                     DefaultListModel<String> profileListModel,
                                     JButton addProfileButton) {
        currentAccount = account;
        String provider = account.getProvider() == null ? "" : account.getProvider().trim().toLowerCase();
        if ("google".equals(provider)) {
            storageStatusLabel.setText("Connected to Google Drive as " + account.getDisplayName() + ". Local profiles stay available.");
        } else if ("microsoft".equals(provider)) {
            storageStatusLabel.setText("Connected to Microsoft Drive as " + account.getDisplayName() + ". Local profiles stay available.");
        } else {
            storageStatusLabel.setText("Using local storage on this device. Open the Storage menu to connect Google Drive or Microsoft Drive.");
        }
        refreshProfiles(profileListModel);
        addProfileButton.setEnabled(true);
    }

    private void refreshProfiles(DefaultListModel<String> profileListModel) {
        profileListModel.clear();
        if (currentAccount == null) {
            return;
        }
        for (ManagedProfile profile : profileManager.listProfiles(currentAccount.getAccountId())) {
            profileListModel.addElement(profile.getProfileName() + " (" + profile.getProfileType() + ")");
        }
    }

    private String joinScopes(List<String> scopes) {
        return String.join(", ", scopes);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record DesktopView(JPanel panel,
                               CardLayout cardLayout,
                               JPanel pagePanel,
                               JLabel storageStatusLabel,
                               DefaultListModel<String> profileListModel,
                               JButton addProfileButton) {
    }
}
