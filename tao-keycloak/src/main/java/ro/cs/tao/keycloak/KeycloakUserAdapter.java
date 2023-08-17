package ro.cs.tao.keycloak;

import org.keycloak.representations.idm.UserRepresentation;
import ro.cs.tao.user.User;
import ro.cs.tao.user.UserModelAdapter;
import ro.cs.tao.user.UserPreference;
import ro.cs.tao.user.UserType;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class KeycloakUserAdapter implements UserModelAdapter<UserRepresentation> {
    @Override
    public User toTaoUser(UserRepresentation profile) {
        User user = null;
        if (profile != null) {
            user = new User();
            user.setId(profile.getId());
            user.setUsername(profile.getUsername());
            user.setUserType(UserType.KEYCLOAK);
            user.setFirstName(profile.getFirstName());
            user.setLastName(profile.getLastName());
            user.setEmail(profile.getEmail());
            user.setOrganization("n/a");
            user.setCreated(Instant.ofEpochMilli(profile.getCreatedTimestamp()).atZone(ZoneId.systemDefault()).toLocalDateTime());
            final Map<String, List<String>> attributes = profile.getAttributes();
            if (attributes != null) {
                List<UserPreference> preferences = null;
                List<String> values = attributes.get("workspace.aws.access.key");
                if (values != null) {
                    preferences = new ArrayList<>();
                    preferences.add(new UserPreference("workspace.aws.access.key", values.get(0)));
                }
                values = attributes.get("workspace.aws.secret.key");
                if (values != null) {
                    if (preferences == null) {
                        preferences = new ArrayList<>();
                    }
                    preferences.add(new UserPreference("workspace.aws.secret.key", values.get(0)));
                }
                values = attributes.get("workspace.aws.bucket");
                if (values != null) {
                    if (preferences == null) {
                        preferences = new ArrayList<>();
                    }
                    preferences.add(new UserPreference("workspace.aws.bucket", values.get(0)));
                }
                if (preferences != null) {
                    user.setPreferences(preferences);
                }
            }
        }
        return user;
    }

    @Override
    public UserRepresentation fromTaoUser(User user) {
        final UserRepresentation kUser = new UserRepresentation();
        kUser.setId(user.getId());
        kUser.setEnabled(true);
        kUser.setUsername(user.getUsername());
        kUser.setFirstName(user.getFirstName());
        kUser.setLastName(user.getLastName());
        kUser.setEmail(user.getEmail());
        kUser.setAttributes(Collections.singletonMap("origin", Collections.singletonList("tao")));
        return kUser;
    }
}
