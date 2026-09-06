package com.ledgermatch.auth;

import com.ledgermatch.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController @RequestMapping("/auth")
public class AuthController {
    record Credentials(@Email @NotBlank String email, @NotBlank @Size(min=8) String password) {}
    record TokenResponse(String token, String userId, String email) {}
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final JwtService jwt;
    public AuthController(JdbcTemplate db, PasswordEncoder encoder, JwtService jwt) { this.db=db; this.encoder=encoder; this.jwt=jwt; }
    @PostMapping("/signup") public TokenResponse signup(@Valid @RequestBody Credentials input) {
        UUID id = UUID.randomUUID(); String email = input.email().trim().toLowerCase();
        try { db.update("insert into users(id,email,password_hash) values (?,?,?)", id,email,encoder.encode(input.password())); }
        catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered"); }
        return new TokenResponse(jwt.issue(id,email), id.toString(), email);
    }
    @PostMapping("/login") public TokenResponse login(@Valid @RequestBody Credentials input) {
        var rows = db.query("select id,email,password_hash from users where email=?", (rs,n) -> new Object[]{rs.getObject("id", UUID.class),rs.getString("email"),rs.getString("password_hash")}, input.email().trim().toLowerCase());
        if (rows.isEmpty() || !encoder.matches(input.password(), (String) rows.getFirst()[2])) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
        UUID id=(UUID)rows.getFirst()[0]; String email=(String)rows.getFirst()[1]; return new TokenResponse(jwt.issue(id,email),id.toString(),email);
    }
}