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
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC integration examples against PostgreSQL (e.g. Neon, Supabase, or any hosted Postgres).
 */
@EnabledIf("postgresConfigured")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgreSqlJdbcIntegrationTest {

    private static final String TABLE = "jdbc_demo_employee";
    /** Left side of join examples: three rows. */
    private static final String JOIN_A = "jdbc_join_a";
    /** Right side: two rows match A; one row has NULL a_id (no parent). */
    private static final String JOIN_B = "jdbc_join_b";
    private static final String CROSS_X = "jdbc_cross_x";
    private static final String CROSS_Y = "jdbc_cross_y";

    private static DbConnectionConfig.DatabaseSection pg;

    @SuppressWarnings("unused")
    static boolean postgresConfigured() {
        try {
            DbConnectionConfig cfg = ConnectionConfigLoader.load();
            return ConnectionConfigLoader.isUsable(cfg.postgresql);
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void setup() throws Exception {
        DbConnectionConfig cfg = ConnectionConfigLoader.load();
        pg = cfg.postgresql;
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS " + JOIN_B + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + JOIN_A + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + CROSS_Y + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + CROSS_X + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + TABLE + " CASCADE");
            st.executeUpdate(
                    "CREATE TABLE "
                            + TABLE
                            + " ( id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, salary NUMERIC(10,2) )");

            st.executeUpdate(
                    "CREATE TABLE "
                            + JOIN_A
                            + " ( a_id INT PRIMARY KEY, a_name VARCHAR(50) NOT NULL )");
            st.executeUpdate(
                    "CREATE TABLE "
                            + JOIN_B
                            + " ( b_id INT PRIMARY KEY, a_id INT NULL REFERENCES "
                            + JOIN_A
                            + "(a_id), b_name VARCHAR(50) NOT NULL )");
            st.executeUpdate("INSERT INTO " + JOIN_A + " VALUES (1, 'A1'), (2, 'A2'), (3, 'A3')");
            st.executeUpdate(
                    "INSERT INTO " + JOIN_B + " VALUES (1, 1, 'B1'), (2, 2, 'B2'), (3, NULL, 'B_solo')");

            st.executeUpdate("CREATE TABLE " + CROSS_X + " ( x INT PRIMARY KEY )");
            st.executeUpdate("CREATE TABLE " + CROSS_Y + " ( y INT PRIMARY KEY )");
            st.executeUpdate("INSERT INTO " + CROSS_X + " VALUES (1), (2)");
            st.executeUpdate("INSERT INTO " + CROSS_Y + " VALUES (10), (20)");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pg == null || !ConnectionConfigLoader.isUsable(pg)) {
            return;
        }
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS " + JOIN_B + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + JOIN_A + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + CROSS_Y + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + CROSS_X + " CASCADE");
            st.executeUpdate("DROP TABLE IF EXISTS " + TABLE + " CASCADE");
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
    @DisplayName("PostgreSQL: schema, columns, types, constraints via information_schema")
    void metadataFromInformationSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT table_schema FROM information_schema.tables WHERE table_name = '"
                                    + TABLE
                                    + "'")) {
                assertTrue(rs.next(), "table should exist");
                String schema = rs.getString(1);
                assertEquals("public", schema);
            }

            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT column_name, data_type, udt_name FROM information_schema.columns "
                                    + "WHERE table_schema = 'public' AND table_name = '"
                                    + TABLE
                                    + "' ORDER BY ordinal_position")) {
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString("column_name") + ":" + rs.getString("data_type"));
                }
                assertTrue(names.stream().anyMatch(s -> s.startsWith("id:")), names.toString());
            }

            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT constraint_name, constraint_type FROM information_schema.table_constraints "
                                    + "WHERE table_schema = 'public' AND table_name = '"
                                    + TABLE
                                    + "'")) {
                boolean pk = false;
                while (rs.next()) {
                    if ("PRIMARY KEY".equals(rs.getString("constraint_type"))) {
                        pk = true;
                    }
                }
                assertTrue(pk);
            }

            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, "public", TABLE, null)) {
                assertTrue(rs.next());
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("PostgreSQL: INSERT with generated keys")
    void insertReturningKeys() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO " + TABLE + " (name, salary) VALUES (?, ?)",
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
    @DisplayName("PostgreSQL: SELECT")
    void selectRows() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO " + TABLE + " (name, salary) VALUES ('Grace', 95000.00)");
            try (ResultSet rs = st.executeQuery("SELECT name, salary FROM " + TABLE + " ORDER BY id")) {
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
    @DisplayName("PostgreSQL: UPDATE")
    void updateRows() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE " + TABLE + " SET salary = ? WHERE name = ?")) {
            ps.setBigDecimal(1, new BigDecimal("100000.00"));
            ps.setString(2, "Grace");
            assertTrue(ps.executeUpdate() >= 0);
        }
    }

    @Test
    @Order(5)
    @DisplayName("PostgreSQL: DELETE")
    void deleteRows() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                PreparedStatement ps = conn.prepareStatement("DELETE FROM " + TABLE + " WHERE name = ?")) {
            ps.setString(1, "Grace");
            ps.executeUpdate();
        }
    }

    @Test
    @Order(6)
    @DisplayName("PostgreSQL: INNER JOIN")
    void innerJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM "
                            + JOIN_A
                            + " a INNER JOIN "
                            + JOIN_B
                            + " b ON a.a_id = b.a_id";
            assertEquals(2, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(7)
    @DisplayName("PostgreSQL: LEFT OUTER JOIN")
    void leftOuterJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM "
                            + JOIN_A
                            + " a LEFT JOIN "
                            + JOIN_B
                            + " b ON a.a_id = b.a_id ORDER BY a.a_id";
            assertEquals(3, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(8)
    @DisplayName("PostgreSQL: RIGHT OUTER JOIN")
    void rightOuterJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM "
                            + JOIN_A
                            + " a RIGHT JOIN "
                            + JOIN_B
                            + " b ON a.a_id = b.a_id ORDER BY b.b_id";
            assertEquals(3, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(9)
    @DisplayName("PostgreSQL: FULL OUTER JOIN")
    void fullOuterJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT a.a_id, b.b_id FROM "
                            + JOIN_A
                            + " a FULL OUTER JOIN "
                            + JOIN_B
                            + " b ON a.a_id = b.a_id";
            assertEquals(4, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(10)
    @DisplayName("PostgreSQL: CROSS JOIN")
    void crossJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            String sql = "SELECT * FROM " + CROSS_X + " CROSS JOIN " + CROSS_Y;
            assertEquals(4, countQueryRows(st, sql));
        }
    }

    @Test
    @Order(11)
    @DisplayName("PostgreSQL: self-JOIN (INNER on same table)")
    void selfJoin() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            String sql =
                    "SELECT COUNT(*) AS c FROM "
                            + JOIN_A
                            + " x INNER JOIN "
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
    @DisplayName("PostgreSQL: DROP TABLE (auxiliary table)")
    void dropTable() throws Exception {
        final String dropMe = "jdbc_demo_drop_pg";
        try (Connection conn = DriverManager.getConnection(pg.jdbcUrl, pg.user, pg.password);
                Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS " + dropMe);
            st.executeUpdate("CREATE TABLE " + dropMe + " (x INT PRIMARY KEY)");
            st.executeUpdate("DROP TABLE " + dropMe);
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '"
                                    + dropMe
                                    + "'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
