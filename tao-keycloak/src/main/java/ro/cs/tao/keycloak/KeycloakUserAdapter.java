package ro.cs.tao.keycloak;

import org.keycloak.representations.idm.UserRepresentation;
import ro.cs.tao.user.User;
import ro.cs.tao.user.UserModelAdapter;
import ro.cs.tao.user.UserType;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;

public class KeycloakUserAdapter implements UserModelAdapter<UserRepresentation> {
    @Override
    public User toTaoUser(UserRepresentation profile) {
        User user = null;
        if (profile != null) {
            user = new User();
            user.setUsername(profile.getUsername());
            user.setUserType(UserType.KEYCLOAK);
            user.setFirstName(profile.getFirstName());
            user.setLastName(profile.getLastName());
            user.setEmail(profile.getEmail());
            user.setOrganization("n/a");
            user.setCreated(Instant.ofEpochMilli(profile.getCreatedTimestamp()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return user;
    }

    @Override
    public UserRepresentation fromTaoUser(User user) {
        final UserRepresentation kUser = new UserRepresentation();
        kUser.setEnabled(true);
        kUser.setUsername(user.getUsername());
        kUser.setFirstName(user.getFirstName());
        kUser.setLastName(user.getLastName());
        kUser.setEmail(user.getEmail());
        kUser.setAttributes(Collections.singletonMap("origin", Collections.singletonList("tao")));
        return kUser;
    }
}
