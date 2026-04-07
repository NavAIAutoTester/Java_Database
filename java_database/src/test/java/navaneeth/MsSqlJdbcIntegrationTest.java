package navaneeth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import navaneeth.config.ConnectionConfigLoader;
import navaneeth.config.DbConnectionConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC integration examples against Microsoft SQL Server (e.g. Azure SQL Database free tier).
 */
@EnabledIf("mssqlConfigured")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MsSqlJdbcIntegrationTest {

    private static final String TABLE = "jdbc_demo_employee";
    private static final String JOIN_A = "jdbc_join_a";
    private static final String JOIN_B = "jdbc_join_b";
    private static final String CROSS_X = "jdbc_cross_x";
    private static final String CROSS_Y = "jdbc_cross_y";

    private static DbConnectionConfig.DatabaseSection mssql;

    @SuppressWarnings("unused")
    static boolean mssqlConfigured() {
        try {
            DbConnectionConfig cfg = ConnectionConfigLoader.load();
            return ConnectionConfigLoader.isUsable(cfg.mssql);
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void setup() throws Exception {
        DbConnectionConfig cfg = ConnectionConfigLoader.load();
        mssql = cfg.mssql;
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + JOIN_B + "', 'U') IS NOT NULL DROP TABLE dbo." + JOIN_B);
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + JOIN_A + "', 'U') IS NOT NULL DROP TABLE dbo." + JOIN_A);
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + CROSS_Y + "', 'U') IS NOT NULL DROP TABLE dbo." + CROSS_Y);
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + CROSS_X + "', 'U') IS NOT NULL DROP TABLE dbo." + CROSS_X);
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + TABLE + "', 'U') IS NOT NULL DROP TABLE dbo." + TABLE);
            st.executeUpdate(
                    "CREATE TABLE dbo."
                            + TABLE
                            + " ( id INT IDENTITY(1,1) PRIMARY KEY, name NVARCHAR(100) NOT NULL, salary DECIMAL(10,2) )");

            st.executeUpdate(
                    "CREATE TABLE dbo."
                            + JOIN_A
                            + " ( a_id INT NOT NULL PRIMARY KEY, a_name NVARCHAR(50) NOT NULL )");
            st.executeUpdate(
                    "CREATE TABLE dbo."
                            + JOIN_B
                            + " ( b_id INT NOT NULL PRIMARY KEY, a_id INT NULL, b_name NVARCHAR(50) NOT NULL, "
                            + "CONSTRAINT FK_"
                            + JOIN_B
                            + "_"
                            + JOIN_A
                            + " FOREIGN KEY (a_id) REFERENCES dbo."
                            + JOIN_A
                            + "(a_id) )");
            st.executeUpdate(
                    "INSERT INTO dbo."
                            + JOIN_A
                            + " (a_id, a_name) VALUES (1, N'A1'), (2, N'A2'), (3, N'A3')");
            st.executeUpdate(
                    "INSERT INTO dbo."
                            + JOIN_B
                            + " (b_id, a_id, b_name) VALUES (1, 1, N'B1'), (2, 2, N'B2'), (3, NULL, N'B_solo')");

            st.executeUpdate("CREATE TABLE dbo." + CROSS_X + " ( x INT NOT NULL PRIMARY KEY )");
            st.executeUpdate("CREATE TABLE dbo." + CROSS_Y + " ( y INT NOT NULL PRIMARY KEY )");
            st.executeUpdate("INSERT INTO dbo." + CROSS_X + " (x) VALUES (1), (2)");
            st.executeUpdate("INSERT INTO dbo." + CROSS_Y + " (y) VALUES (10), (20)");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (mssql == null || !ConnectionConfigLoader.isUsable(mssql)) {
            return;
        }
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + JOIN_B + "', 'U') IS NOT NULL DROP TABLE dbo." + JOIN_B);
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + JOIN_A + "', 'U') IS NOT NULL DROP TABLE dbo." + JOIN_A);
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + CROSS_Y + "', 'U') IS NOT NULL DROP TABLE dbo." + CROSS_Y);
            st.executeUpdate(
                    "IF OBJECT_ID('dbo." + CROSS_X + "', 'U') IS NOT NULL DROP TABLE dbo." + CROSS_X);
            st.executeUpdate("IF OBJECT_ID('dbo." + TABLE + "', 'U') IS NOT NULL DROP TABLE dbo." + TABLE);
        }
    }

    private static int countQueryRows(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            int n = 0;
            while (rs.next()) {
                n++;
            }
            return n;
        }
    }

    @Test
    @Order(1)
    @DisplayName("MSSQL: schema, columns, types, constraints via INFORMATION_SCHEMA")
    void metadataFromInformationSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT TABLE_SCHEMA FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '"
                                    + TABLE
                                    + "'")) {
                assertTrue(rs.next(), "table should exist");
                assertEquals("dbo", rs.getString(1));
            }

            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                                    + "WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = '"
                                    + TABLE
                                    + "' ORDER BY ORDINAL_POSITION")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("COLUMN_NAME") + ":" + rs.getString("DATA_TYPE"));
                }
                assertTrue(cols.stream().anyMatch(s -> s.startsWith("id:")), cols.toString());
            }

            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                                    + "WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = '"
                                    + TABLE
                                    + "'")) {
                boolean pk = false;
                while (rs.next()) {
                    if ("PRIMARY KEY".equals(rs.getString("CONSTRAINT_TYPE"))) {
                        pk = true;
                    }
                }
                assertTrue(pk);
            }

            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, "dbo", TABLE, null)) {
                assertTrue(rs.next());
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("MSSQL: INSERT with generated keys")
    void insertReturningKeys() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO dbo." + TABLE + " (name, salary) VALUES (?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Ada");
            ps.setBigDecimal(2, new BigDecimal("120000.50"));
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertTrue(keys.getLong(1) > 0);
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("MSSQL: SELECT")
    void selectRows() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO dbo." + TABLE + " (name, salary) VALUES (N'Grace', 95000.00)");
            try (ResultSet rs = st.executeQuery("SELECT name, salary FROM dbo." + TABLE + " ORDER BY id")) {
                int n = 0;
                while (rs.next()) {
                    n++;
                    assertTrue(rs.getString("name").length() > 0);
                }
                assertTrue(n >= 1);
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("MSSQL: UPDATE")
    void updateRows() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE dbo." + TABLE + " SET salary = ? WHERE name = ?")) {
            ps.setBigDecimal(1, new BigDecimal("100000.00"));
            ps.setString(2, "Grace");
            assertTrue(ps.executeUpdate() >= 0);
        }
    }

    @Test
    @Order(5)
    @DisplayName("MSSQL: DELETE")
    void deleteRows() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                PreparedStatement ps =
                        conn.prepareStatement("DELETE FROM dbo." + TABLE + " WHERE name = ?")) {
            ps.setString(1, "Grace");
            ps.executeUpdate();
        }
    }

    @Test
    @Order(6)
    @DisplayName("MSSQL: INNER JOIN")
    void innerJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM dbo."
                            + JOIN_A
                            + " a INNER JOIN dbo."
                            + JOIN_B
                            + " b ON a.a_id = b.a_id";
            assertEquals(2, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(7)
    @DisplayName("MSSQL: LEFT OUTER JOIN")
    void leftOuterJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM dbo."
                            + JOIN_A
                            + " a LEFT JOIN dbo."
                            + JOIN_B
                            + " b ON a.a_id = b.a_id ORDER BY a.a_id";
            assertEquals(3, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(8)
    @DisplayName("MSSQL: RIGHT OUTER JOIN")
    void rightOuterJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM dbo."
                            + JOIN_A
                            + " a RIGHT JOIN dbo."
                            + JOIN_B
                            + " b ON a.a_id = b.a_id ORDER BY b.b_id";
            assertEquals(3, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(9)
    @DisplayName("MSSQL: FULL OUTER JOIN")
    void fullOuterJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM dbo."
                            + JOIN_A
                            + " a FULL OUTER JOIN dbo."
                            + JOIN_B
                            + " b ON a.a_id = b.a_id";
            assertEquals(4, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(10)
    @DisplayName("MSSQL: CROSS JOIN")
    void crossJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            String sql = "SELECT * FROM dbo." + CROSS_X + " CROSS JOIN dbo." + CROSS_Y;
            assertEquals(4, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(11)
    @DisplayName("MSSQL: self-JOIN (INNER on same table)")
    void selfJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT COUNT(*) AS c FROM dbo."
                            + JOIN_A
                            + " x INNER JOIN dbo."
                            + JOIN_A
                            + " y ON x.a_id < y.a_id";
            try (ResultSet rs = st.executeQuery(sql)) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt("c"));
            }
        }
    }

    @Test
    @Order(12)
    @DisplayName("MSSQL: DROP TABLE (auxiliary table)")
    void dropTable() throws Exception {
        final String dropMe = "jdbc_demo_drop_mssql";
        try (Connection conn = DriverManager.getConnection(mssql.jdbcUrl, mssql.user, mssql.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate("IF OBJECT_ID('dbo." + dropMe + "', 'U') IS NOT NULL DROP TABLE dbo." + dropMe);
            st.executeUpdate("CREATE TABLE dbo." + dropMe + " (x INT PRIMARY KEY)");
            st.executeUpdate("DROP TABLE dbo." + dropMe);
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) AS c FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = '"
                                    + dropMe
                                    + "'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt("c"));
            }
        }
    }
}
