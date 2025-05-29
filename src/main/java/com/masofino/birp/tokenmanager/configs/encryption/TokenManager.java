package com.masofino.birp.tokenmanager.configs.encryption;

import com.masofino.birp.tokenmanager.configs.properties.AppConfig;
import com.masofino.birp.tokenmanager.entities.Token;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class TokenManager {
    private AppConfig appConfig = AppConfig.getInstance();
    private List<Token> tokens = new ArrayList<>();

    public TokenManager() throws IOException {
       Files.createDirectories(appConfig.getTokensStoragePath());
       loadTokens();
    }

    private void loadTokens() throws IOException {
        if (Files.exists(appConfig.getTokensStoragePath())) {
            Files.list(appConfig.getTokensStoragePath()).filter(Files::isDirectory).forEach(dir -> {
                Path certificatePath = dir.resolve("certificate.pem");
                Path privateKeyPath = dir.resolve("private.pem.enc");
                if (Files.exists(certificatePath) && Files.isReadable(certificatePath) && Files.exists(privateKeyPath) && Files.isReadable(privateKeyPath)) {
                   tokens.add(new Token(dir.getFileName().toString(), certificatePath, privateKeyPath));
                }
            });
        }
    }

    public void deleteToken(Token token) throws IOException {
        Path directory = token.getCertificatePath().getParent();
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {

                        }
                    });
        }
        tokens.remove(token);
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public void importToken(String name, Path certificatePath, Path privateKeyPath, String pin) throws IOException, GeneralSecurityException {
       Path dir = appConfig.getTokensStoragePath().resolve(name);
       Files.createDirectories(dir);
       Path certificateDest = dir.resolve("certificate.pem");
       Path privateKeyDest = dir.resolve("private.pem.enc");

       Files.copy(certificatePath, certificateDest);
       byte[] privateKeyBytes = Files.readAllBytes(privateKeyPath);
       byte[] encryptedBytes = encrypt(privateKeyBytes, pin.toCharArray());
       Files.write(privateKeyDest, encryptedBytes);
       tokens.add(new Token(name, privateKeyDest, certificateDest));
    }

    public Token generateToken(String name, SecureRandom random, String pin) throws GeneralSecurityException, IOException, OperatorCreationException {
        Path dir = appConfig.getTokensStoragePath().resolve(name);
        Files.createDirectories(dir);

        KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, random);
        KeyPair keyPair = generator.generateKeyPair();

        Path certificateDest = dir.resolve("certificate.pem");
        Path privateKeyDest = dir.resolve("private.pem.enc");

        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        X500Name subject = new org.bouncycastle.asn1.x500.X500Name("CN=" + name);
        BigInteger serial = new BigInteger(64, random);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        try (JcaPEMWriter w = new JcaPEMWriter(Files.newBufferedWriter(certificateDest))) {
            w.writeObject(holder);
        }

        byte[] enc = encrypt(keyPair.getPrivate().getEncoded(), pin.toCharArray());
        Files.write(privateKeyDest, enc);

        Token token = new Token(name, certificateDest, privateKeyDest);
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
