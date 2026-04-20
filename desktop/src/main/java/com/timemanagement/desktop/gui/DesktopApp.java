package com.timemanagement.desktop.gui;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.dataclass.MicrosoftOAuthSession;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.MicrosoftLoginManager;
import com.timemanagement.core.program.ProfileManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

public class DesktopApp {
    private final GoogleLoginManager googleLoginManager;
    private final MicrosoftLoginManager microsoftLoginManager;
    private final ProfileManager profileManager;
    private final DesktopOAuthCredentialStore<GoogleOAuthSession> googleCredentialStore;
    private final DesktopOAuthCredentialStore<MicrosoftOAuthSession> microsoftCredentialStore;
    private GoogleAccount currentAccount;

    public DesktopApp(Path dataDir) {
        JsonDataStore dataStore = new JsonDataStore(dataDir);
        this.googleLoginManager = new GoogleLoginManager(dataStore);
        this.microsoftLoginManager = new MicrosoftLoginManager(dataStore);
        this.profileManager = new ProfileManager(dataStore);

        Path oauthDirectory = Path.of(System.getProperty("user.home"), ".time-management");
        this.googleCredentialStore = new DesktopOAuthCredentialStore(
                oauthDirectory.resolve("google-oauth-session.enc"),
                GoogleOAuthSession.class,
                "Google OAuth"
        );
        this.microsoftCredentialStore = new DesktopOAuthCredentialStore(
                oauthDirectory.resolve("microsoft-oauth-session.enc"),
                MicrosoftOAuthSession.class,
                "Microsoft OAuth"
        );
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
        frame.setContentPane(createContentPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void exportUiScreenshot(Path outputPath) {
        JPanel panel = createContentPanel();
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

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel loginPanel = new JPanel(new GridLayout(0, 2, 6, 6));
        JPasswordField passphraseField = new JPasswordField();
        JButton googleLoginButton = new JButton("Sign in with Google");
        JButton googleRestoreButton = new JButton("Use Saved Google Session");
        JButton googleClearButton = new JButton("Forget Saved Google Session");
        JButton microsoftLoginButton = new JButton("Sign in with Microsoft");
        JButton microsoftRestoreButton = new JButton("Use Saved Microsoft Session");
        JButton microsoftClearButton = new JButton("Forget Saved Microsoft Session");
        JLabel accountLabel = new JLabel("Not signed in");

        loginPanel.add(new JLabel("Credential passphrase:"));
        loginPanel.add(passphraseField);
        loginPanel.add(googleLoginButton);
        loginPanel.add(googleRestoreButton);
        loginPanel.add(googleClearButton);
        loginPanel.add(microsoftLoginButton);
        loginPanel.add(microsoftRestoreButton);
        loginPanel.add(microsoftClearButton);
        loginPanel.add(accountLabel);
        loginPanel.add(new JLabel("Requires TIME_MANAGEMENT_GOOGLE_CLIENT_ID and/or TIME_MANAGEMENT_MICROSOFT_CLIENT_ID"));

        DefaultListModel<String> profileListModel = new DefaultListModel<>();
        JList<String> profileList = new JList<>(profileListModel);
        JTextField profileNameField = new JTextField();
        JTextField profileTypeField = new JTextField();
        JButton addProfileButton = new JButton("Add Profile");
        addProfileButton.setEnabled(false);

        JPanel profileInput = new JPanel(new GridLayout(3, 2, 6, 6));
        profileInput.add(new JLabel("Profile name:"));
        profileInput.add(profileNameField);
        profileInput.add(new JLabel("Type (person/service):"));
        profileInput.add(profileTypeField);
        profileInput.add(addProfileButton);
        profileInput.add(new JLabel("Multiple profiles per login are supported."));

        googleLoginButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                GoogleOAuthSession session = googleOAuthService().signIn(passphrase);
                currentAccount = googleLoginManager.login(session.getIdentity());
                updateSignedInState(accountLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Google login failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        googleRestoreButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                GoogleOAuthSession session = googleOAuthService().restoreSession(passphrase)
                        .orElseThrow(() -> new IllegalStateException("No saved Google session was found."));
                currentAccount = googleLoginManager.login(session.getIdentity());
                updateSignedInState(accountLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Google restore failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        googleClearButton.addActionListener(event -> {
            try {
                googleCredentialStore.clear();
                clearCurrentAccountIfProvider("google", accountLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Google sign out failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        microsoftLoginButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                MicrosoftOAuthSession session = microsoftOAuthService().signIn(passphrase);
                currentAccount = microsoftLoginManager.login(session.getIdentity());
                updateSignedInState(accountLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Microsoft login failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        microsoftRestoreButton.addActionListener(event -> {
            char[] passphrase = readPassphrase(passphraseField);
            try {
                MicrosoftOAuthSession session = microsoftOAuthService().restoreSession(passphrase)
                        .orElseThrow(() -> new IllegalStateException("No saved Microsoft session was found."));
                currentAccount = microsoftLoginManager.login(session.getIdentity());
                updateSignedInState(accountLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Microsoft restore failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });

        microsoftClearButton.addActionListener(event -> {
            try {
                microsoftCredentialStore.clear();
                clearCurrentAccountIfProvider("microsoft", accountLabel, profileListModel, addProfileButton);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Microsoft sign out failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        addProfileButton.addActionListener(event -> {
            if (currentAccount == null) {
                return;
            }
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
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Profile creation failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(loginPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(profileList), BorderLayout.CENTER);
        panel.add(profileInput, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(740, 460));
        return panel;
    }

    private char[] readPassphrase(JPasswordField passphraseField) {
        char[] passphrase = passphraseField.getPassword();
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Enter a credential passphrase before signing in.");
        }
        return passphrase;
    }

    private void updateSignedInState(JLabel accountLabel, DefaultListModel<String> profileListModel, JButton addProfileButton) {
        String provider = currentAccount.getProvider() == null || currentAccount.getProvider().isBlank()
                ? "account"
                : currentAccount.getProvider();
        accountLabel.setText("Signed in with " + provider + " as " + currentAccount.getDisplayName());
        refreshProfiles(profileListModel);
        addProfileButton.setEnabled(true);
    }

    private void clearCurrentAccountIfProvider(String provider,
                                               JLabel accountLabel,
                                               DefaultListModel<String> profileListModel,
                                               JButton addProfileButton) {
        if (currentAccount != null && provider.equals(currentAccount.getProvider())) {
            currentAccount = null;
            accountLabel.setText("Not signed in");
            profileListModel.clear();
            addProfileButton.setEnabled(false);
        }
    }

    private DesktopGoogleOAuthService googleOAuthService() {
        return new DesktopGoogleOAuthService(DesktopOAuthClientConfig.loadGoogleFromEnvironment(), googleCredentialStore);
    }

    private DesktopMicrosoftOAuthService microsoftOAuthService() {
        return new DesktopMicrosoftOAuthService(DesktopOAuthClientConfig.loadMicrosoftFromEnvironment(), microsoftCredentialStore);
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
}
