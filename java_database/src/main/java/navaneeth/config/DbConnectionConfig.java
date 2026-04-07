package navaneeth.config;

/**
 * Maps the root object in {@code db-connection.json}.
 */
public class DbConnectionConfig {

    public DatabaseSection postgresql;
    public DatabaseSection mssql;

    public static class DatabaseSection {
        public String jdbcUrl;
        public String user;
        public String password;
    }
}
