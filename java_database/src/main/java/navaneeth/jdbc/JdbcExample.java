package navaneeth.jdbc;

import navaneeth.config.ConnectionConfigLoader;
import navaneeth.config.DbConnectionConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runnable examples of JDBC: connect, statement, and read metadata (database product name / version).
 * Run after configuring {@code src/main/resources/db-connection.json} (or an override path via
 * {@code DB_CONFIG_PATH} / {@code db.config.path}).
 *
 * <p>Example:
 *
 * <pre>
 * mvn -q exec:java -Dexec.mainClass="navaneeth.jdbc.JdbcExample" -Dexec.args="postgresql"
 * </pre>
 */
public final class JdbcExample {

    private JdbcExample() {}

    public static void main(String[] args) {
        String which = args.length > 0 ? args[0].toLowerCase() : "postgresql";
        try {
            DbConnectionConfig cfg = ConnectionConfigLoader.load();
            DbConnectionConfig.DatabaseSection section =
                    "mssql".equals(which) ? cfg.mssql : cfg.postgresql;
            if (!ConnectionConfigLoader.isUsable(section)) {
                System.err.println(
                        "Configure the chosen database in db-connection.json (replace REPLACE_ME placeholders).");
                System.exit(1);
                return;
            }
            demoQuery(section);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void demoQuery(DbConnectionConfig.DatabaseSection db) throws SQLException {
        try (Connection conn = DriverManager.getConnection(db.jdbcUrl, db.user, db.password);
                Statement st = conn.createStatement()) {
            var meta = conn.getMetaData();
            System.out.println("URL: " + meta.getURL());
            System.out.println("Product: " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
            String sql =
                    db.jdbcUrl.contains("sqlserver") || db.jdbcUrl.contains("microsoft")
                            ? "SELECT DB_NAME() AS db_name, SYSTEM_USER AS db_user"
                            : "SELECT current_database() AS db_name, current_user AS db_user";
            try (ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    System.out.println("Database / user: " + rs.getString(1) + " / " + rs.getString(2));
                }
            }
        }
    }
}
