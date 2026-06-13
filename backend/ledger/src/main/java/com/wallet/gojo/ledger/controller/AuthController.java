package com.wallet.gojo.ledger.controller;

import com.wallet.gojo.ledger.domain.dto.AuthRequest;
import com.wallet.gojo.ledger.domain.dto.AuthResponse;
import com.wallet.gojo.ledger.security.JwtProvider;
import com.wallet.gojo.ledger.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {

        // Step 1: Pack the credentials into a framework-friendly container
        UsernamePasswordAuthenticationToken loginCredentials =
                new UsernamePasswordAuthenticationToken(request.email(), request.password());

        // Step 2: Hand the credentials to the manager to verify against the DB and password encoder
        Authentication authenticationResult = authenticationManager.authenticate(loginCredentials);

        // Step 3: Extract our fully verified SecurityUser from the successful authentication result
        SecurityUser verifiedUser = (SecurityUser) authenticationResult.getPrincipal();

        // Step 4: Generate the secure digital passport token using that verified user profile
        String token = jwtProvider.generateToken(verifiedUser);

        // Step 5: Wrap it up and mail it back to the user with an HTTP 200 OK status
        return ResponseEntity.ok(new AuthResponse(token));
    }
}