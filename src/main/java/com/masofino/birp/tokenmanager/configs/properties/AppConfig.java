package com.masofino.birp.tokenmanager.configs.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_FILE = "token-manager.properties";
    private static AppConfig instance;
    private final Properties properties;

    // Default config values
    private static final String DEFAULT_CERT_PATH = "certs/certificate.pem";
    private static final String DEFAULT_PRIVATE_KEY_PATH = "certs/private.pem";
    private static final String DEFAULT_WORKSPACE_PATH = "workspace";

    private AppConfig() {
        properties = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("Error loading config file: " + e.getMessage());
                setDefaults();
            }
        } else {
            setDefaults();
            saveConfig();
        }
    }

    private void setDefaults() {
        properties.setProperty("workspace.path", DEFAULT_WORKSPACE_PATH);
        properties.setProperty("certificate.path", DEFAULT_CERT_PATH);
        properties.setProperty("private.path", DEFAULT_PRIVATE_KEY_PATH);
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public String getWorkspacePath() {
        return properties.getProperty("workspace.path", DEFAULT_WORKSPACE_PATH);
    }

    public void setWorkspacePath(String path) {
        properties.getProperty("workspace.path", path);
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public String getProperty(String key) {
        return (String) properties.getOrDefault(key, null);
    }

    public void saveConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                properties.store(fos, "Token Manager Configuration");
            }
        } catch (IOException e) {
            System.err.println("Error saving config file: " + e.getMessage());
        }
    }
}