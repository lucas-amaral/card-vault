package br.com.amaral.cardvault.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 */
class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET =
            "3cfa76ef14937c1c0ea519f8fc057a80fcd04a7420f8e8bcd0a7567c272e007b";
    private static final long EXPIRATION = 86400000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION);
    }

    private UserDetails buildUser(String username) {
        return User.builder()
                .username(username)
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    @Test
    @DisplayName("generateToken — token is not blank")
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken(buildUser("alice"));
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("extractUsername — returns correct username from token")
    void extractUsername_returnsCorrectUsername() {
        UserDetails user = buildUser("alice");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("isTokenValid — valid token for matching user returns true")
    void isTokenValid_matchingUser_returnsTrue() {
        UserDetails user = buildUser("alice");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid — token for different user returns false")
    void isTokenValid_differentUser_returnsFalse() {
        String token = jwtService.generateToken(buildUser("alice"));
        UserDetails other = buildUser("bob");

        assertThat(jwtService.isTokenValid(token, other)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid — expired token returns false")
    void isTokenValid_expiredToken_returnsFalse() {
        // Use a separate JwtService with a 1ms expiry
        JwtService shortLivedService = new JwtService();
        ReflectionTestUtils.setField(shortLivedService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(shortLivedService, "jwtExpiration", 1L);

        UserDetails user = buildUser("alice");
        String token = shortLivedService.generateToken(user);

        // Wait for token to expire
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortLivedService.isTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("generateToken — two calls produce different tokens (unique JTI / iat)")
    void generateToken_twoCalls_produceDifferentTokens() throws InterruptedException {
        UserDetails user = buildUser("alice");
        String token1 = jwtService.generateToken(user);
        Thread.sleep(5);
        String token2 = jwtService.generateToken(user);

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("getExpirationMs — returns configured value")
    void getExpirationMs_returnsConfiguredValue() {
        assertThat(jwtService.getExpirationMs()).isEqualTo(EXPIRATION);
    }

    @Test
    @DisplayName("extractUsername — tampered token throws exception")
    void extractUsername_tamperedToken_throwsException() {
        String token = jwtService.generateToken(buildUser("alice"));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(Exception.class);
    }
}
