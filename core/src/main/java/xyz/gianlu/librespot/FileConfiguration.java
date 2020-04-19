package xyz.gianlu.librespot;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.file.FormatDetector;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.electronwill.nightconfig.core.io.ConfigWriter;
import com.electronwill.nightconfig.toml.TomlParser;
import com.spotify.connectstate.Connect;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.gianlu.librespot.common.Utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * @author Gianlu
 */
public final class FileConfiguration extends AbsConfiguration {
    private static final Logger LOGGER = Logger.getLogger(FileConfiguration.class);

    static {
        FormatDetector.registerExtension("properties", new PropertiesFormat());
    }

    private final CommentedFileConfig config;

    public FileConfiguration(@Nullable String... override) throws IOException {
        File confFile = null;
        if (override != null && override.length > 0) {
            for (String arg : override) {
                if (arg != null && arg.startsWith("--conf-file="))
                    confFile = new File(arg.substring(12));
            }
        }

        if (confFile == null) confFile = new File("config.toml");

        if (!confFile.exists()) {
            File oldConf = new File("conf.properties");
            if (oldConf.exists()) confFile = oldConf;
        }

        boolean migrating = FormatDetector.detect(confFile) instanceof PropertiesFormat;

        config = CommentedFileConfig.builder(migrating ? new File("config.toml") : confFile).onFileNotFound(FileNotFoundAction.copyData(streamDefaultConfig())).build();
        config.load();

        if (migrating) {
            migrateOldConfig(confFile, config);
            config.save();
            confFile.delete();

            LOGGER.info("Your configuration has been migrated to `config.toml`, change your input file if needed.");
        } else {
            updateConfigFile(new TomlParser().parse(streamDefaultConfig()));
        }

        if (override != null && override.length > 0) {
            for (String str : override) {
                if (str == null) continue;

                if (str.contains("=") && str.startsWith("--")) {
                    String[] split = Utils.split(str, '=');
                    if (split.length != 2) {
                        LOGGER.warn("Invalid command line argument: " + str);
                        continue;
                    }

                    String key = split[0].substring(2);
                    config.set(key, convertFromString(key, split[1]));
                } else {
//                    LOGGER.warn("Invalid command line argument: " + str);
                }
            }
        }
    }

    private static boolean removeDeprecatedKeys(@NotNull Config defaultConfig, @NotNull Config config, @NotNull FileConfig base, @NotNull String prefix) {
        boolean save = false;

        for (Config.Entry entry : new ArrayList<>(config.entrySet())) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof Config) {
                if (removeDeprecatedKeys(defaultConfig, entry.getValue(), base, key + "."))
                    save = true;
            } else {
                if (!defaultConfig.contains(key)) {
                    LOGGER.trace("Removed entry from configuration file: " + key);
                    base.remove(key);
                    save = true;
                }
            }
        }

        return save;
    }

    private static boolean checkMissingKeys(@NotNull Config defaultConfig, @NotNull FileConfig config, @NotNull String prefix) {
        boolean save = false;

        for (Config.Entry entry : defaultConfig.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof Config) {
                if (checkMissingKeys(entry.getValue(), config, key + "."))
                    save = true;
            } else {
                if (!config.contains(key)) {
                    LOGGER.trace("Added new entry to configuration file: " + key);
                    config.set(key, entry.getValue());
                    save = true;
                }
            }
        }

        return save;
    }

    @NotNull
    private static Object convertFromString(@NotNull String key, @NotNull String value) {
        if (Objects.equals(key, "player.normalisationPregain")) {
            return Float.parseFloat(value);
        } else if (Objects.equals(key, "deviceType")) {
            if (value.equals("AudioDongle")) return "AUDIO_DONGLE";
            else return value.toUpperCase();
        } else if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean(value);
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return value;
            }
        }
    }

    private static void migrateOldConfig(@NotNull File confFile, @NotNull FileConfig config) throws IOException {
        Properties old = new Properties();
        try (FileReader fr = new FileReader(confFile)) {
            old.load(fr);
        }

        for (Object key : old.keySet()) {
            String val = old.getProperty((String) key);
            config.set((String) key, convertFromString((String) key, val));
        }
    }

    @NotNull
    private static InputStream streamDefaultConfig() {
        InputStream defaultConfig = FileConfiguration.class.getClassLoader().getResourceAsStream("default.toml");
        if (defaultConfig == null) throw new IllegalStateException();
        return defaultConfig;
    }

    private void updateConfigFile(@NotNull CommentedConfig defaultConfig) {
        boolean save = checkMissingKeys(defaultConfig, config, "");
        if (removeDeprecatedKeys(defaultConfig, config, config, "")) save = true;

        if (save) {
            config.clearComments();

            for (Map.Entry<String, UnmodifiableCommentedConfig.CommentNode> entry : defaultConfig.getComments().entrySet()) {
                UnmodifiableCommentedConfig.CommentNode node = entry.getValue();
                if (config.contains(entry.getKey())) {
                    config.setComment(entry.getKey(), node.getComment());
                    Map<String, UnmodifiableCommentedConfig.CommentNode> children = node.getChildren();
                    if (children != null) ((CommentedConfig) config.getRaw(entry.getKey())).putAllComments(children);
                }
            }

            config.save();
        }
    }

    @Override
    public @Nullable String deviceId() {
        return "c70f928698cc099e39bddfd469f7de38e3b805db";
    }

    @Override
    public @Nullable String deviceName() {
        return "sp-playcount-librespot";
    }

    @Override
    public @Nullable Connect.DeviceType deviceType() {
        return Connect.DeviceType.UNKNOWN;
    }

    @Override
    public @NotNull String preferredLocale() {
        return config.get("preferredLocale");
    }

    @Override
    public @NotNull Level loggingLevel() {
        String str = config.get("logLevel");
        return Level.toLevel(str);
    }

    @Override
    public boolean storeCredentials() {
        return config.get("storeCredentials");
    }

    @Override
    public @Nullable File credentialsFile() {
        String path = config.get("credentialsFile");
        if (path == null || path.isEmpty()) return null;
        return new File(path);
    }

    @Override
    public int port() {
        return config.get("server.port");
    }

    @Override
    public @NotNull String endpoint() {
        return config.get("server.endpoint");
    }

    @Override
    public boolean enableHttps() {
        return config.get("server.enableHttps");
    }

    @Override
    public @Nullable String httpsKs() {
        return config.get("server.httpsKs");
    }

    @Override
    public @Nullable String httpsKsPass() {
        return config.get("server.httpsKsPass");
    }

    private final static class PropertiesFormat implements ConfigFormat<Config> {
        @Override
        public ConfigWriter createWriter() {
            return null;
        }

        @Override
        public ConfigParser<Config> createParser() {
            return null;
        }

        @Override
        public Config createConfig(Supplier<Map<String, Object>> mapCreator) {
            return null;
        }

        @Override
        public boolean supportsComments() {
            return false;
        }
    }
}
