package io.github.calegrandy.askdb.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Accepts only Cognito access tokens issued to AskDB's app client. Spring Boot already checks the signature,
 * expiry and issuer, and adds this validator to those checks because it's a bean. Cognito access tokens have
 * no "aud" claim, so without this, a token for any other app in the same user pool, or an ID token, would pass.
 */
@Component
public class CognitoAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "Not an access token issued to AskDB", null);

    private final String clientId;

    public CognitoAccessTokenValidator(@Value("${askdb.cognito.client-id}") String clientId) {
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        boolean valid = "access".equals(token.getClaimAsString("token_use"))
                && clientId.equals(token.getClaimAsString("client_id"));
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
