package navaneeth.config;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads {@link DbConnectionConfig} from {@code db-connection.json}. Resolution: optional file on disk
 * (see {@link #resolveConfigPath()}) first, then classpath resource {@code db-connection.json} (canonical
 * location in source: {@code src/main/resources/db-connection.json}).
 */
public final class ConnectionConfigLoader {

    private static final Gson GSON = new Gson();

    private ConnectionConfigLoader() {}

    public static DbConnectionConfig load() throws IOException {
        Optional<Path> path = resolveConfigPath();
        if (path.isPresent()) {
            try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
                return parse(reader);
            }
        }
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db-connection.json");
        if (in == null) {
            in = ConnectionConfigLoader.class.getResourceAsStream("/db-connection.json");
        }
        if (in != null) {
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return parse(reader);
            }
        }
        throw new IOException(
                "db-connection.json not found. Add src/main/resources/db-connection.json, set DB_CONFIG_PATH, "
                        + "or -Ddb.config.path=..., or place db-connection.json in the working directory.");
    }

    private static DbConnectionConfig parse(Reader reader) throws IOException {
        try {
            DbConnectionConfig cfg = GSON.fromJson(reader, DbConnectionConfig.class);
            if (cfg == null) {
                throw new IOException("Empty or invalid JSON");
            }
            return cfg;
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON in db-connection.json", e);
        }
    }

    /**
     * Resolves {@code db-connection.json} from environment, system property, or working directory.
     */
    public static Optional<Path> resolveConfigPath() {
        String env = System.getenv("DB_CONFIG_PATH");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env.trim());
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        String prop = System.getProperty("db.config.path");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop.trim());
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
            cwd.resolve("db-connection.json"),
            cwd.resolve("java_database").resolve("db-connection.json"),
        };
        for (Path c : candidates) {
            if (Files.isRegularFile(c)) {
                return Optional.of(c);
            }
        }
        Path parent = cwd.getParent();
        if (parent != null) {
            Path root = parent.resolve("db-connection.json");
            if (Files.isRegularFile(root)) {
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns true when the section has non-placeholder credentials suitable for integration tests.
     */
    public static boolean isUsable(DbConnectionConfig.DatabaseSection section) {
        if (section == null) {
            return false;
        }
        if (section.jdbcUrl == null || section.jdbcUrl.isBlank()) {
            return false;
        }
        if (section.jdbcUrl.contains("REPLACE_ME")) {
            return false;
        }
        if (section.user == null || section.user.isBlank()) {
            return false;
        }
        if (section.password == null || section.password.isBlank()) {
            return false;
        }
        if (section.password.contains("REPLACE_ME")) {
            return false;
        }
        return true;
    }
}
