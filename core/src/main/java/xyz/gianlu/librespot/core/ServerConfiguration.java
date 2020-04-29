package xyz.gianlu.librespot.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author evilarceus
 */
public interface ServerConfiguration {

    int port();

    @NotNull
    String albumEndpoint();

    @NotNull
    String artistEndpoint();

    @NotNull
    String artistAboutEndpoint();

    boolean enableHttps();

    @Nullable
    String httpsKs();

    @Nullable
    String httpsKsPass();

    boolean storeCredentials();

    @Nullable
    File credentialsFile();
}
