package ro.cs.tao.ldap;

import ro.cs.tao.user.User;
import ro.cs.tao.user.UserModelAdapter;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.time.LocalDateTime;

public class LdapUserAdapter implements UserModelAdapter<Attributes> {
    @Override
    public User toTaoUser(Attributes attrs) {
        User user = null;
        if (attrs != null) {
            try {
                user = new User();
                user.setUsername(attrs.get("samAccountName").get().toString());
                user.setExternal(true);
                user.setFirstName(attrs.get("givenname").get().toString());
                user.setLastName(attrs.get("sn").get().toString());
                user.setEmail(attrs.get("mail").get().toString());
                user.setPhone(attrs.get("telephonenumber").get().toString());
                user.setOrganization("n/a");
                user.setCreated(LocalDateTime.now());
            } catch (NamingException e) {
                e.printStackTrace();
            }
        }
        return user;
    }

    @Override
    public Attributes fromTaoUser(User user) {
        // Not supported
        return null;
    }
}
