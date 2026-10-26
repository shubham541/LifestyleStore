package com.shubham.lifestylestore.web;

import com.shubham.lifestylestore.domain.UserAccount;
import com.shubham.lifestylestore.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/account")
public class AccountController {

    public record Profile(String name, String email, String contact, String city, String address) {
        static Profile of(UserAccount u) {
            return new Profile(u.name(), u.email(), u.contact(), u.city(), u.address());
        }
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String oldPassword,
            @NotBlank @Size(min = 10, max = 72, message = AuthController.PASSWORD_RULE) String newPassword) {
    }

    private final AuthService auth;

    public AccountController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping
    public Profile me(@AuthenticationPrincipal Jwt jwt) {
        return Profile.of(auth.profile(jwt.getSubject()));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest r) {
        auth.changePassword(jwt.getSubject(), r.oldPassword(), r.newPassword());
    }
}
