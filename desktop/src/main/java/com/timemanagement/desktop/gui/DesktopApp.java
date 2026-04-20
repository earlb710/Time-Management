package com.timemanagement.desktop.gui;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.ProfileManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

public class DesktopApp {
    private final GoogleLoginManager loginManager;
    private final ProfileManager profileManager;
    private GoogleAccount currentAccount;

    public DesktopApp(Path dataDir) {
        JsonDataStore dataStore = new JsonDataStore(dataDir);
        this.loginManager = new GoogleLoginManager(dataStore);
        this.profileManager = new ProfileManager(dataStore);
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

        JPanel loginPanel = new JPanel(new GridLayout(3, 2, 6, 6));
        JTextField emailField = new JTextField();
        JTextField nameField = new JTextField();
        JButton loginButton = new JButton("Sign in with Google");
        JLabel accountLabel = new JLabel("Not signed in");

        loginPanel.add(new JLabel("Google email:"));
        loginPanel.add(emailField);
        loginPanel.add(new JLabel("Display name:"));
        loginPanel.add(nameField);
        loginPanel.add(loginButton);
        loginPanel.add(accountLabel);

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

        loginButton.addActionListener(event -> {
            try {
                currentAccount = loginManager.login(emailField.getText(), nameField.getText());
                accountLabel.setText("Signed in as " + currentAccount.getDisplayName());
                refreshProfiles(profileListModel);
                addProfileButton.setEnabled(true);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Login failed", JOptionPane.ERROR_MESSAGE);
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
        panel.setPreferredSize(new Dimension(640, 420));
        return panel;
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
