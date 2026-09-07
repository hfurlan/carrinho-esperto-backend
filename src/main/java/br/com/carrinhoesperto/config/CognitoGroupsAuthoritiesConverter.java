package br.com.carrinhoesperto.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Maps each value of the {@code cognito:groups} access-token claim to a Spring
 * authority {@code ROLE_<GROUP>} (upper-cased). Lets {@code hasRole(...)} gate a
 * route by Cognito group membership without any extra table or lookup.
 */
public class CognitoGroupsAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups == null) {
            return List.of();
        }
        return groups.stream()
            .map(group -> new SimpleGrantedAuthority("ROLE_" + group.trim().toUpperCase(Locale.ROOT)))
            .map(GrantedAuthority.class::cast)
            .toList();
    }
}
