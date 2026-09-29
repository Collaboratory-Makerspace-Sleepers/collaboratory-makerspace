package com.makerspace.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends OAuth2 failures back to the SPA instead of leaving them on the backend.
 *
 * Without this, a failed callback falls through to Spring's generated login page
 * on the API origin (port 8080). That page's own "login" links restart the flow
 * from a different origin, dropping the OAuth state, which fails again — an
 * unbreakable redirect loop with no visible cause.
 */
@Slf4j
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // Matched by class name rather than by type: the concrete OIDC
        // exception types live in modules whose presence varies across Spring
        // Security versions, and referencing them directly would not compile
        // against all of them.
        String reason = classify(exception);

        log.warn("OAuth2 login failed ({}): {}", reason, exception.toString());

        response.sendRedirect(frontendUrl + "/login?error=" + reason);
    }

    private String classify(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String n = c.getClass().getSimpleName();
            if (n.contains("RedirectMismatch")) return "redirect_mismatch";
            if (n.contains("AuthorizationCode")) return "code_rejected";
            if (n.contains("IdToken")) return "id_token_invalid";
            if (n.contains("InvalidClientRegistration")) return "unknown_provider";
        }
        return "failed";
    }
}
