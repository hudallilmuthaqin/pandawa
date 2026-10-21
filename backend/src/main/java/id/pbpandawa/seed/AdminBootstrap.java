package id.pbpandawa.seed;

import id.pbpandawa.user.Role;
import id.pbpandawa.user.User;
import id.pbpandawa.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin from ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD
 * when the database has no admin yet. Does nothing afterwards, so the password
 * can (and should) be changed in the app and the variables removed.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap-admin.username:}") String username,
                          @Value("${app.bootstrap-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || userRepository.countByRoleAndEnabledTrue(Role.ADMIN) > 0) {
            return;
        }
        if (password.length() < 12) {
            log.error("ADMIN_BOOTSTRAP_PASSWORD must be at least 12 characters; bootstrap admin NOT created");
            return;
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.error("Bootstrap username '{}' already exists but is not an enabled admin; skipping", username);
            return;
        }
        userRepository.save(new User(username.trim(), passwordEncoder.encode(password), Role.ADMIN));
        log.warn("Bootstrap admin '{}' created. Change its password and remove ADMIN_BOOTSTRAP_* variables.", username);
    }
}
