module com.masofino.birp.tokenmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires jdk.httpserver;
    requires com.dustinredmond.fxtrayicon;
    requires java.prefs;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires java.net.http;

    opens com.masofino.birp.tokenmanager to javafx.fxml;
    exports com.masofino.birp.tokenmanager;

    opens com.masofino.birp.tokenmanager.controllers to javafx.fxml;
    exports com.masofino.birp.tokenmanager.controllers;

    opens com.masofino.birp.tokenmanager.configs.encryption to javafx.fxml;
    exports com.masofino.birp.tokenmanager.configs.encryption;

    opens com.masofino.birp.tokenmanager.entities to javafx.fxml;
    exports com.masofino.birp.tokenmanager.entities;

}