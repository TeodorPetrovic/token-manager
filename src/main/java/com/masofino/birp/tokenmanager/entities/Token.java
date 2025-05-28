package com.masofino.birp.tokenmanager.entities;

import java.nio.file.Path;

public class Token {
    private final String name;
    private final Path privateKeyPath;
    private final Path publicKeyPath;

    public Token(String name, Path privateKeyPath, Path publicKeyPath) {
        this.name = name;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
    }

    public String getName() {
        return name;
    }

    public Path getPrivateKeyPath() {
        return privateKeyPath;
    }

    public Path getPublicKeyPath() {
        return publicKeyPath;
    }
}