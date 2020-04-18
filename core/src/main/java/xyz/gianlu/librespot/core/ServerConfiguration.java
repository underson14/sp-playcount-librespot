package xyz.gianlu.librespot.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author evilarceus
 */
public interface ServerConfiguration {

    int port();

    @NotNull
    String endpoint();

    boolean enableHttps();

    @Nullable
    String httpsKs();

    @Nullable
    String httpsKsPass();
}
