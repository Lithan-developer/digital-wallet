package com.wallet.service;

import com.wallet.domain.User;
import com.wallet.domain.Wallet;
import com.wallet.dto.Dtos.*;
import com.wallet.exception.ApiException;
import com.wallet.repo.UserRepository;
import com.wallet.repo.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AuditLogger audit;
    @Value("${app.jwt.expiry-minutes}") private long expiryMinutes;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            audit.log("REGISTER", null, "FAILED", "reason=email_taken");
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User u = new User();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);

        Wallet w = new Wallet();   // one wallet per user, created atomically with the account
        w.setUser(u);
        wallets.save(w);

        audit.log("REGISTER", u.getId(), "SUCCESS", "walletId=" + w.getId());
        return tokenFor(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User u = users.findByEmail(email).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            audit.log("LOGIN", u == null ? null : u.getId(), "FAILED", "email=" + email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }
        audit.log("LOGIN", u.getId(), "SUCCESS", null);
        return tokenFor(u);
    }

    private AuthResponse tokenFor(User u) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("wallet-api").issuedAt(now)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .subject(u.getId().toString())
                .claim("email", u.getEmail())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiryMinutes * 60, u.getEmail(), u.getFullName());
    }
}
