package com.application.roles.configuration;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the caller's bearer token on outbound Feign calls.
 *
 * The Authentication service's user-lookup endpoints are no longer anonymous,
 * so calls this service makes on a user's behalf have to carry that user's
 * token. Propagating the caller's credential (rather than a service-wide one)
 * also keeps those lookups bounded by what the caller may already see.
 */
@Configuration
public class FeignAuthForwardingInterceptor {

    private static final String AUTHORIZATION = "Authorization";

    @Bean
    public RequestInterceptor forwardAuthorizationHeader() {
        return template -> {
            if (template.headers().containsKey(AUTHORIZATION)) {
                return; // an explicit header on the client method wins
            }
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
                return; // no inbound request (startup seeding, scheduled work)
            }
            String authorization = attrs.getRequest().getHeader(AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                template.header(AUTHORIZATION, authorization);
            }
        };
    }
}
