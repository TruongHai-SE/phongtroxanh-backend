package vn.phongtroxanh.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.phongtroxanh.backend.common.dto.ProblemDetailResponse;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");

                            ProblemDetailResponse problem = ProblemDetailResponse.builder()
                                    .type("https://api.phongtroxanh.vn/errors/unauthorized")
                                    .title("Unauthorized")
                                    .status(HttpStatus.UNAUTHORIZED.value())
                                    .detail("Yêu cầu xác thực. Vui lòng đăng nhập để tiếp tục")
                                    .instance(request.getRequestURI())
                                    .code("UNAUTHORIZED")
                                    .timestamp(Instant.now())
                                    .build();

                            response.getWriter().write(objectMapper.writeValueAsString(problem));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");

                            ProblemDetailResponse problem = ProblemDetailResponse.builder()
                                    .type("https://api.phongtroxanh.vn/errors/forbidden")
                                    .title("Forbidden")
                                    .status(HttpStatus.FORBIDDEN.value())
                                    .detail("Bạn không có quyền truy cập tài nguyên này")
                                    .instance(request.getRequestURI())
                                    .code("FORBIDDEN")
                                    .timestamp(Instant.now())
                                    .build();

                            response.getWriter().write(objectMapper.writeValueAsString(problem));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/misc/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/map").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/compare").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/rooms/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/monetization/plans").permitAll()
                        .requestMatchers("/api/v1/monetization/vnpay-ipn", "/api/v1/monetization/vnpay-return").permitAll()
                        .requestMatchers("/api/v1/locations/**").permitAll()
                        .requestMatchers("/api/v1/payments/webhooks/**").permitAll()
                        // OpenAPI / Swagger UI
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Actuator
                        .requestMatchers("/actuator/**").permitAll()
                        // WebSocket Handshake
                        .requestMatchers("/ws/**").permitAll()
                        // Admin Endpoints
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // All other APIs require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Request-ID", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-ID", "Set-Cookie"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
