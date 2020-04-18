package xyz.gianlu.librespot;

import com.spotify.connectstate.Connect;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.gianlu.librespot.core.*;

/**
 * @author Gianlu
 */
public abstract class AbsConfiguration implements TimeProvider.Configuration, AuthConfiguration, ServerConfiguration {

    @Nullable
    public abstract String deviceId();

    @Nullable
    public abstract String deviceName();

    @Nullable
    public abstract Connect.DeviceType deviceType();

    @NotNull
    public abstract String preferredLocale();

    @NotNull
    public abstract Level loggingLevel();
}
