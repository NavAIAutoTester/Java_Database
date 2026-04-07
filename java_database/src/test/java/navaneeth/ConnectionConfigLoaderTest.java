package navaneeth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import navaneeth.config.ConnectionConfigLoader;
import navaneeth.config.DbConnectionConfig;

import org.junit.jupiter.api.Test;

class ConnectionConfigLoaderTest {

    @Test
    void isUsableRejectsPlaceholderPassword() {
        DbConnectionConfig.DatabaseSection s = new DbConnectionConfig.DatabaseSection();
        s.jdbcUrl = "jdbc:postgresql://localhost:5432/db";
        s.user = "u";
        s.password = "REPLACE_ME_PASSWORD";
        assertFalse(ConnectionConfigLoader.isUsable(s));
    }

    @Test
    void isUsableAcceptsCompleteSection() {
        DbConnectionConfig.DatabaseSection s = new DbConnectionConfig.DatabaseSection();
        s.jdbcUrl = "jdbc:postgresql://localhost:5432/db";
        s.user = "u";
        s.password = "secret";
        assertTrue(ConnectionConfigLoader.isUsable(s));
    }
}
