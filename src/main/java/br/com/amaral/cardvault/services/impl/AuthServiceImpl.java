package br.com.amaral.cardvault.services.impl;

import br.com.amaral.cardvault.entities.dto.request.AuthRequest;
import br.com.amaral.cardvault.entities.dto.response.AuthResponse;
import br.com.amaral.cardvault.services.AuthService;
import br.com.amaral.cardvault.services.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link AuthService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Override
    public AuthResponse authenticate(final AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        final String token = jwtService.generateToken(userDetails);

        log.info("User authenticated successfully: username={}", request.username());
        return new AuthResponse(token, jwtService.getExpirationMs());
    }
}
