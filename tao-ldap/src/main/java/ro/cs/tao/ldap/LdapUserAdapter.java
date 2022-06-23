package ro.cs.tao.ldap;

import ro.cs.tao.user.User;
import ro.cs.tao.user.UserModelAdapter;
import ro.cs.tao.user.UserType;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
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
                user.setUserType(UserType.LDAP);
                Attribute attribute = attrs.get("givenname");
                if (attribute != null) {
                    user.setFirstName(String.valueOf(attribute.get()));
                }
                attribute = attrs.get("sn");
                if (attribute != null) {
                    user.setLastName(String.valueOf(attribute.get()));
                }
                attribute = attrs.get("mail");
                if (attribute != null) {
                    user.setEmail(String.valueOf(attribute.get()));
                }
                attribute = attrs.get("telephonenumber");
                if (attribute != null) {
                    user.setPhone(String.valueOf(attribute.get()));
                }
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
