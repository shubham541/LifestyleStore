package com.shubham.lifestylestore.web;

import com.shubham.lifestylestore.service.AuthService;
import com.shubham.lifestylestore.service.AuthService.Registration;
import com.shubham.lifestylestore.service.AuthService.Token;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // bcrypt only uses the first 72 bytes, so cap the password there.
    public static final String PASSWORD_RULE = "Password must be 10-72 characters";

    public record SignupRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72, message = PASSWORD_RULE) String password,
            @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "Enter a valid phone number") String contact,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 200) String address) {
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Valid @RequestBody SignupRequest r) {
        auth.register(new Registration(r.name(), r.email(), r.password(), r.contact(), r.city(), r.address()));
    }

    @PostMapping("/login")
    public Token login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }
}
