package vn.phongtroxanh.backend.modules.user.infrastructure.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Run against an isolated database with schema.sql applied; every fixture is rolled back.
@EnabledIfEnvironmentVariable(named = "PTX_TEST_DB_URL", matches = ".+")
class DailySwipeQuotaDatabaseTest {
    private Connection connection;
    private JdbcTemplate jdbc;
    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @BeforeEach void seedAccount() throws Exception {
        connection = DriverManager.getConnection(System.getenv("PTX_TEST_DB_URL"),
                System.getenv().getOrDefault("PTX_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("PTX_TEST_DB_PASSWORD", "postgrespassword"));
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.update("INSERT INTO users(id, full_name, password_hash, role) VALUES (?, 'Quota test', 'test', 'TENANT')", userId);
        jdbc.update("INSERT INTO user_consumables(user_id, swipes_left, free_swipes_left, last_swipe_reset_at) VALUES (?, 19, 4, ?)",
                userId, Date.valueOf(today.minusDays(1)));
    }

    @AfterEach void rollback() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test void dailyResetPreservesPurchasedSwipesAndRunsOnlyOnce() throws Exception {
        assertThat(reset(today)).isEqualTo(1);
        assertBalance(30, 15); // Fifteen purchased credits plus today's fifteen free swipes.
        assertThat(reset(today)).isZero();
        assertBalance(30, 15);
        jdbc.update("UPDATE user_consumables SET swipes_left = 0, free_swipes_left = 0 WHERE user_id = ?", userId);
        assertThat(reset(today.plusDays(1))).isEqualTo(1);
        assertBalance(15, 15);
    }

    @Test void activeSubscriptionGetsDailyAllowanceAndExpiredSubscriptionReturnsToFreeQuota() throws Exception {
        String planId = "QUOTA_" + UUID.randomUUID();
        jdbc.update("INSERT INTO package_plans(id, target_role, name, price_monthly, price_yearly, features) " +
                "VALUES (?, 'TENANT', 'Quota test', 1, 1, '{\"swipes_per_day\":50}'::jsonb)", planId);
        jdbc.update("INSERT INTO subscriptions(user_id, plan_id, billing_cycle, start_date, end_date, is_active) " +
                "VALUES (?, ?, 'MONTHLY', NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 days', true)", userId, planId);
        assertThat(reset(today)).isEqualTo(1);
        assertBalance(65, 50);
        jdbc.update("UPDATE subscriptions SET end_date = NOW() - INTERVAL '1 second' WHERE user_id = ?", userId);
        assertThat(reset(today.plusDays(1))).isEqualTo(1);
        assertBalance(30, 15);
    }

    private int reset(LocalDate date) throws Exception {
        String sql = UserConsumableRepository.class.getMethod("resetDailySwipesAtomic", UUID.class, LocalDate.class)
                .getAnnotation(Query.class).value();
        return new NamedParameterJdbcTemplate(jdbc).update(sql, Map.of("userId", userId, "today", Date.valueOf(date)));
    }

    private void assertBalance(int total, int free) {
        Map<String, Object> balance = jdbc.queryForMap("SELECT swipes_left, free_swipes_left FROM user_consumables WHERE user_id = ?", userId);
        assertThat(balance.get("swipes_left")).isEqualTo(total);
        assertThat(balance.get("free_swipes_left")).isEqualTo(free);
    }
}
