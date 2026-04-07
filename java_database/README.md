# java_database

Maven (`maven-archetype-quickstart`) project demonstrating **JDBC** against **PostgreSQL** and **Microsoft SQL Server**, with JUnit 5 integration tests (CRUD, metadata, **joins**, and a small runnable example).

## Prerequisites

- JDK 17+
- Maven 3.9+
- Network access to your databases (hosted PostgreSQL and/or SQL Server)

## Free hosted databases (examples)

| Engine        | Typical free-tier option | Notes |
|---------------|--------------------------|--------|
| PostgreSQL    | [Neon](https://neon.tech), [Supabase](https://supabase.com) | Use `sslmode=require` in the JDBC URL. |
| SQL Server    | [Azure SQL Database](https://azure.microsoft.com/en-us/products/azure-sql/database/) (free tier) | Use `encrypt=true` in the JDBC URL; allow your client IP in the server firewall. |

Create a database (or use the default), then put the **host**, **database name**, **user**, and **password** into `src/main/resources/db-connection.json` (see below). Replace every `REPLACE_ME` placeholder; integration tests only run when credentials are real (no `REPLACE_ME` substrings).

## Project layout

- `pom.xml` — dependencies: PostgreSQL JDBC, Microsoft JDBC, Gson (JSON config), JUnit 5.
- `src/main/resources/db-connection.json` — **single** credentials file (on the classpath for tests and `exec:java`). Template placeholders are committed; **do not commit real passwords**—use `DB_CONFIG_PATH`, a gitignored copy next to `pom.xml`, or a path outside the repo.
- `db-connection.json.example` — copy-paste reference with realistic URL shapes (same JSON shape as above).
- `src/main/java/navaneeth/config/` — loads `db-connection.json` (see resolution order below).
- `src/main/java/navaneeth/jdbc/JdbcExample.java` — minimal CLI demo: connect and print database metadata.
- `src/test/java/navaneeth/PostgreSqlJdbcIntegrationTest.java` — Postgres: metadata (`information_schema`), CRUD, **join suite** (see [Integration tests: SQL joins](#integration-tests-sql-joins)), `DROP TABLE`.
- `src/test/java/navaneeth/MsSqlJdbcIntegrationTest.java` — SQL Server: **mirror of the same tests** using `INFORMATION_SCHEMA` / `dbo`.

## Configuring `db-connection.json`

The file is a JSON object with two optional sections, `postgresql` and `mssql`. Each section has:

| Field       | Meaning |
|------------|---------|
| `jdbcUrl`  | Full JDBC URL (driver-specific query parameters included). |
| `user`     | Database user. |
| `password` | Database password. |

**Resolution order** (first match wins):

1. Environment variable `DB_CONFIG_PATH` (absolute or relative path to the JSON file).
2. System property `db.config.path` (e.g. `-Ddb.config.path=C:\secrets\db-connection.json`).
3. `./db-connection.json` from the current working directory (optional **override**; useful to keep secrets out of `src/main/resources`).
4. `./java_database/db-connection.json` if you run Maven from the repository parent folder.
5. **Classpath** `db-connection.json` — the copy packaged from `src/main/resources/db-connection.json` (default for `mvn test` and `mvn exec:java` when no file on disk matches steps 1–4).

Example PostgreSQL URL (Neon-style):

`jdbc:postgresql://ep-xxxx.region.aws.neon.tech/neondb?sslmode=require`

Example SQL Server URL (Azure-style):

`jdbc:sqlserver://yourserver.database.windows.net:1433;database=mydb;encrypt=true;trustServerCertificate=false;loginTimeout=30;`

## JDBC concepts (quick reference)

### Drivers and URLs

- **PostgreSQL**: artifact `org.postgresql:postgresql`. URL prefix `jdbc:postgresql://host:port/database?...`
- **SQL Server**: artifact `com.microsoft.sqlserver:mssql-jdbc`. URL prefix `jdbc:sqlserver://host:port;database=name;...`

JDBC 4+ **does not require** `Class.forName(...)` when the driver JAR is on the classpath; drivers register automatically.

### Connections

```java
Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
```

Prefer **try-with-resources** so `Connection`, `Statement`, `PreparedStatement`, and `ResultSet` close reliably.

### Statements

- **`Statement`** — simple SQL; avoid string concatenation with untrusted input (SQL injection).
- **`PreparedStatement`** — parameterized SQL (`?` placeholders); use for user data.

### Generated keys

After `INSERT`, use `Statement.RETURN_GENERATED_KEYS` and `getGeneratedKeys()` to read auto-generated IDs (PostgreSQL `SERIAL` / SQL Server `IDENTITY`).

### Metadata

- **`DatabaseMetaData`** (`conn.getMetaData()`) — portable catalog APIs (schemas, tables, columns).
- **`INFORMATION_SCHEMA` / `information_schema`** — SQL-standard views for tables, columns, constraints (syntax and schema names differ slightly: e.g. PostgreSQL often uses `public`, SQL Server uses `dbo`).

The tests show both approaches. For **join** examples wired through JDBC, see [Integration tests: SQL joins](#integration-tests-sql-joins).

## Integration tests: SQL joins

Both `PostgreSqlJdbcIntegrationTest` and `MsSqlJdbcIntegrationTest` build the same **fixture** in `@BeforeAll` (and remove it in `@AfterAll`), then run matching `@Test` methods in order (`@Order` 6–11 for joins; the auxiliary `DROP TABLE` demo is `@Order` 12).

### Fixture tables and seed data

| Table | Purpose |
|-------|---------|
| `jdbc_join_a` | “Left” set: `a_id` 1…3 with labels `A1`…`A3`. |
| `jdbc_join_b` | “Right” set: two rows linked to `a_id` 1 and 2, plus one row with **`a_id` NULL** (`B_solo`) so it does not match any row in `A`. |
| `jdbc_cross_x`, `jdbc_cross_y` | Two rows each, used only for `CROSS JOIN` (2×2 = 4 rows). |

Example relationship (PostgreSQL-style names; SQL Server uses `dbo.` prefix in the tests):

- `jdbc_join_a`: `(1, A1)`, `(2, A2)`, `(3, A3)`
- `jdbc_join_b`: `(b_id=1, a_id=1, B1)`, `(2, 2, B2)`, `(3, NULL, B_solo)`

The NULL foreign key is intentional: it lets **RIGHT JOIN** return a row with no matching `A`, and **FULL OUTER JOIN** return both an unmatched `A` (`A3`) and an unmatched `B` (`B_solo`).

### Join types exercised and expected row counts

Tests execute SQL via JDBC (`Statement.executeQuery`), iterate the `ResultSet` (or use `COUNT(*)` for the self-join), and assert counts.

| Test (order) | SQL join | Expected rows | Meaning |
|--------------|----------|---------------|---------|
| `@Order(6)` `innerJoin` | `… a INNER JOIN … b ON a.a_id = b.a_id` | **2** | Only `(1,1)` and `(2,2)`; `B_solo` is excluded. |
| `@Order(7)` `leftOuterJoin` | `… a LEFT JOIN … b ON …` | **3** | All three `A` rows; `A3` has no `B` side (NULLs). |
| `@Order(8)` `rightOuterJoin` | `… a RIGHT JOIN … b ON …` | **3** | All three `B` rows; `B_solo` has no `A` side (NULLs). |
| `@Order(9)` `fullOuterJoin` | `… a FULL OUTER JOIN … b ON …` | **4** | Inner pairs plus unmatched `A3` plus unmatched `B_solo`. |
| `@Order(10)` `crossJoin` | `jdbc_cross_x CROSS JOIN jdbc_cross_y` | **4** | Cartesian product; no `ON` clause. |
| `@Order(11)` `selfJoin` | `jdbc_join_a x INNER JOIN jdbc_join_a y ON x.a_id < y.a_id` | **3** | Pairs `(1,2)`, `(1,3)`, `(2,3)` via `COUNT(*)`. |

`OUTER` is optional in SQL (`LEFT JOIN` ≡ `LEFT OUTER JOIN`). A **self-join** is not a separate join keyword: it is the same table listed twice with aliases, here using an `INNER JOIN` predicate.

### Running only the join tests

Joins are ordinary methods in the integration classes; run the whole class (credentials required) or rely on Maven’s ordering:

```text
mvn test -Dtest=PostgreSqlJdbcIntegrationTest
mvn test -Dtest=MsSqlJdbcIntegrationTest
```

With placeholder credentials, the entire class—including join tests—is **skipped** (see below).

## Running from the command line

Run all commands from the `java_database` directory (the folder that contains `pom.xml`), unless noted.

### Build and unit tests

```text
mvn clean test
```

With placeholders in `db-connection.json`, **integration tests are skipped** (**24** tests: 12 per integration class, including metadata, CRUD, joins, and the auxiliary `DROP` demo), while **2** unit tests (`ConnectionConfigLoaderTest`) still run. After you set real credentials, the PostgreSQL and/or SQL Server suite runs in full—including **all six join scenarios**—when that section is usable.

### Run only selected tests

```text
mvn test -Dtest=ConnectionConfigLoaderTest
mvn test -Dtest=PostgreSqlJdbcIntegrationTest
mvn test -Dtest=MsSqlJdbcIntegrationTest
```

### Run the JDBC example main class

PostgreSQL (default):

```text
mvn exec:java
```

SQL Server:

```text
mvn exec:java "-Dexec.args=mssql"
```

### Package the JAR

```text
mvn clean package
```

The runnable entry point for the archetype default is `navaneeth.App`; the JDBC demo is `navaneeth.jdbc.JdbcExample` (use `exec-maven-plugin` as above).

## Security notes

- Treat `db-connection.json` like a secret once it contains real passwords.
- Prefer firewall rules, least-privilege DB users, and TLS (`sslmode=require`, `encrypt=true`).
- Add `src/main/resources/db-connection.json` to `.gitignore` if you replace placeholders in place and must not commit secrets, or keep placeholders in git and use `DB_CONFIG_PATH` / a local `db-connection.json` next to `pom.xml` for real credentials.

## Troubleshooting

- **Tests skipped**: Ensure `jdbcUrl`, `user`, and `password` contain no `REPLACE_ME` substring and are non-empty.
- **PostgreSQL SSL**: Cloud providers often require `?sslmode=require` (or stricter) on the URL.
- **Azure SQL / firewall**: Add your public IP to the server firewall or use Azure resources that bypass the rule for trusted services.
- **Config not found**: Ensure `src/main/resources/db-connection.json` exists, or set `DB_CONFIG_PATH`, or place `db-connection.json` in the process working directory.
