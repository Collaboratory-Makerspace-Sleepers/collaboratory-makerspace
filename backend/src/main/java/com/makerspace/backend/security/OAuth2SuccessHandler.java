package com.makerspace.backend.security;

import com.makerspace.backend.config.security.OAuthProfile;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserResolution;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        OAuthProfile profile = OAuthProfile.from(oidcUser);

        UserResolution resolution;
        try {
            resolution = userService.resolve(profile);
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "/login?error=server");
            return;
        }

        switch (resolution) {
            case UserResolution.Active active ->
                    issueTokenAndRedirect(active.user(), response);

            case UserResolution.NotFound notFound -> {
                try {
                    issueTokenAndRedirect(userService.provision(notFound.profile()), response);
                } catch (Exception e) {
                    response.sendRedirect(frontendUrl + "/login?error=server");
                }
            }

            case UserResolution.Deleted ignored ->
                    response.sendRedirect(frontendUrl + "/account-closed");
        }
    }

    private void issueTokenAndRedirect(User user, HttpServletResponse response) throws IOException {
        String token = jwtService.generateToken(user);

        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        response.sendRedirect(frontendUrl + "/oauth-callback");
    }
}