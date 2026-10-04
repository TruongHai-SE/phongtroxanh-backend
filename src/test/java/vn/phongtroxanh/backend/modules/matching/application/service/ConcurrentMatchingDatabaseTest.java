package vn.phongtroxanh.backend.modules.matching.application.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import vn.phongtroxanh.backend.common.security.UserPrincipal;
import vn.phongtroxanh.backend.modules.matching.domain.SwipeAction;
import vn.phongtroxanh.backend.modules.matching.domain.SwipeActionConverter;
import vn.phongtroxanh.backend.modules.matching.domain.Swipe;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.SwipeRepository;
import vn.phongtroxanh.backend.modules.matching.presentation.dto.SwipeRequest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@EnabledIfEnvironmentVariable(named = "PTX_TEST_DB_URL", matches = "jdbc:postgresql://[^/]+/ptx_mvp_check(?:\\?.*)?")
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=${PTX_TEST_DB_URL}",
        "spring.datasource.username=${PTX_TEST_DB_USER:postgres}",
        "spring.datasource.password=${PTX_TEST_DB_PASSWORD:postgrespassword}",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
class ConcurrentMatchingDatabaseTest {
    @Autowired MatchingService matching;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @SpyBean SwipeRepository swipes;
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @AfterEach void removeFixtures() {
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", first, second);
    }

    @Test void simultaneousOppositeLikesCreateExactlyOneMatch() throws Exception {
        for (UUID id : new UUID[]{first, second}) {
            jdbc.update("INSERT INTO users(id, full_name, password_hash, role) VALUES (?, 'Concurrent match test', 'test', 'TENANT')", id);
            jdbc.update("INSERT INTO user_profiles(user_id, is_public) VALUES (?, true)", id);
            jdbc.update("INSERT INTO user_consumables(user_id) VALUES (?)", id);
        }
        CountDownLatch readsFinished = new CountDownLatch(2);
        // Hold each reverse-query result before commit. With pair locks the first times out,
        // commits, and the second then observes its swipe; without locks both results are empty.
        doAnswer(invocation -> {
            entityManager.flush();
            UUID current = invocation.getArgument(0);
            UUID target = invocation.getArgument(1);
            var result = jdbc.query("SELECT id, swiper_id, target_id, direction FROM swipes " +
                            "WHERE swiper_id = ? AND target_id = ? AND direction = 'RIGHT'",
                    (rs, row) -> Swipe.builder().id((UUID) rs.getObject("id"))
                            .swiperId((UUID) rs.getObject("swiper_id")).targetId((UUID) rs.getObject("target_id"))
                            .action(new SwipeActionConverter().convertToEntityAttribute(rs.getString("direction"))).build(), target, current).stream().findFirst();
            readsFinished.countDown();
            readsFinished.await(1, TimeUnit.SECONDS);
            return result;
        }).when(swipes).findReverseSwipe(any(), any(), any());

        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return like(first, second); });
            var b = workers.submit(() -> { start.await(); return like(second, first); });
            start.countDown();
            boolean firstMatched = a.get(15, TimeUnit.SECONDS);
            boolean secondMatched = b.get(15, TimeUnit.SECONDS);
            assertThat(firstMatched || secondMatched).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matches WHERE (user_a_id = ? AND user_b_id = ?) OR (user_a_id = ? AND user_b_id = ?)",
                Integer.class, first, second, second, first)).isEqualTo(1);
    }

    private boolean like(UUID actor, UUID target) {
        UserPrincipal principal = UserPrincipal.builder().id(actor).role("TENANT").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            return matching.swipe(SwipeRequest.builder().targetUserId(target).action(SwipeAction.LIKE).build()).isMatch();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
