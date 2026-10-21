package id.pbpandawa.security;

import id.pbpandawa.user.User;
import id.pbpandawa.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the Bearer token and loads the user from the database, so that
 * disabled accounts, logouts (token_version) and permission changes take
 * effect immediately.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   PermissionService permissionService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7).trim())
                    .flatMap(claims -> userRepository.findWithPlayerById(claims.userId())
                            .filter(User::isEnabled)
                            .filter(u -> u.getTokenVersion() == claims.tokenVersion()))
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(User user) {
        Set<Permission> permissions = permissionService.permissionsOf(user.getRole());
        AuthUser principal = new AuthUser(user.getId(), user.getUsername(), user.getRole(),
                user.getPlayer() != null ? user.getPlayer().getId() : null, permissions);
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
