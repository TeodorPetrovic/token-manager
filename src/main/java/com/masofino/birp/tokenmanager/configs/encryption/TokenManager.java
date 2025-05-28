package com.masofino.birp.tokenmanager.configs.encryption;

import com.masofino.birp.tokenmanager.entities.Token;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Base64;
import java.util.List;

public class TokenManager {
    private Path baseDir;
    private List<Token> tokens;

    public TokenManager(Path baseDir) throws IOException {
       this.baseDir = baseDir;
       Files.createDirectories(baseDir);
       loadTokens();
    }

    private void loadTokens() throws IOException {
        if (Files.exists(baseDir)) {
            Files.list(baseDir).filter(Files::isDirectory).forEach(dir -> {
                Path publicKeyPath = dir.resolve("public.pem");
                Path privateKeyPath = dir.resolve("private.pem.enc");
                if (Files.exists(publicKeyPath) && Files.exists(privateKeyPath)) {
                   tokens.add(new Token(dir.getFileName().toString(), publicKeyPath, privateKeyPath));
                }
            });
        }
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public void importToken(String name, Path publicKeyPath, Path privateKeyPath, String pin) throws IOException, GeneralSecurityException {
       Path dir = baseDir.resolve(name);
       Files.createDirectories(dir);
       Path publicKeyDest = dir.resolve("public.pem");
       Path privateKeyDest = dir.resolve("private.pem.enc");

       Files.copy(publicKeyPath, publicKeyDest);
       byte[] privateKeyBytes = Files.readAllBytes(privateKeyPath);
       byte[] encryptedBytes = encrypt(privateKeyBytes, pin.toCharArray());
       Files.write(privateKeyDest, encryptedBytes);
       tokens.add(new Token(name, privateKeyDest, publicKeyDest));
    }

    public Token generateToken(String name, SecureRandom random, String pin) throws GeneralSecurityException, IOException {
        Path dir = baseDir.resolve(name);
        Files.createDirectories(dir);

        KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, random);
        KeyPair keyPair = generator.generateKeyPair();

        Path publicKeyDest = dir.resolve("public.pem");
        Path privateKeyDest = dir.resolve("private.pem.enc");

        String pubPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";
        Files.writeString(publicKeyDest, pubPem);
        byte[] enc = encrypt(keyPair.getPrivate().getEncoded(), pin.toCharArray());
        Files.write(privateKeyDest, enc);

        Token token = new Token(name, privateKeyDest, publicKeyDest);
        tokens.add(token);
        return token;
    }

    private byte[] encrypt(byte[] data, char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryption = cipher.doFinal(data);
        byte[] result = new byte[salt.length + encryption.length];

        System.arraycopy(salt, 0, result, 0, salt.length);
        System.arraycopy(encryption, 0, result, salt.length, encryption.length);
        return result;
    }

    public byte[] decrypt(Token token, char[] password) throws GeneralSecurityException, IOException {
        byte[] blob = Files.readAllBytes(token.getPrivateKeyPath());

        byte[] salt = new byte[16];
        System.arraycopy(blob, 0, salt, 0, 16);

        byte[] encryption = new byte[blob.length - 16];
        System.arraycopy(blob, 16, encryption, 0, encryption.length);

        PBEKeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES");

        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(encryption);
    }


}
