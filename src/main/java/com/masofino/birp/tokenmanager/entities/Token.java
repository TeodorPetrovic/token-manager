package com.masofino.birp.tokenmanager.entities;

import java.nio.file.Path;

public class Token {
    private final String name;
    private final Path privateKeyPath;
    private final Path certificatePath;

    public Token(String name, Path certificatePath, Path privateKeyPath) {
        this.name = name;
        this.privateKeyPath = privateKeyPath;
        this.certificatePath = certificatePath;
    }

    public String getName() {
        return name;
    }

    public Path getPrivateKeyPath() {
        return privateKeyPath;
    }

    public Path getCertificatePath() {
        return certificatePath;
    }
}