package ro.cs.tao.github;

import org.kohsuke.github.GHMyself;
import ro.cs.tao.user.User;
import ro.cs.tao.user.UserModelAdapter;
import ro.cs.tao.user.UserStatus;
import ro.cs.tao.user.UserType;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;

public class GithubUserAdapter implements UserModelAdapter<GHMyself> {
    @Override
    public User toTaoUser(GHMyself object) {
        final User user = new User();
        user.setId(String.valueOf(object.getId()));
        try {
            user.setFirstName(object.getName());
            user.setUserType(UserType.GITHUB);
            user.setEmail(object.getEmail());
            user.setStatus(UserStatus.ACTIVE);
            user.setUsername(object.getLogin());
            Date date = object.getCreatedAt();
            if (date != null) {
                user.setCreated(Timestamp.from(object.getCreatedAt().toInstant()).toLocalDateTime());
            }
            date = object.getUpdatedAt();
            if (date != null) {
                user.setModified(Timestamp.from(date.toInstant()).toLocalDateTime());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Override
    public GHMyself fromTaoUser(User user) {
        return null;
    }
}
