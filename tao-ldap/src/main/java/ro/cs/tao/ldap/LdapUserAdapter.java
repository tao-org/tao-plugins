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
                user.setId(attrs.get(LDAPAttributes.GUID).get().toString());
                user.setUsername(attrs.get(LDAPAttributes.ACCOUNT).get().toString());
                user.setUserType(UserType.LDAP);
                Attribute attribute = attrs.get(LDAPAttributes.NAME);
                if (attribute != null) {
                    user.setFirstName(String.valueOf(attribute.get()));
                }
                attribute = attrs.get(LDAPAttributes.SN);
                if (attribute != null) {
                    user.setLastName(String.valueOf(attribute.get()));
                }
                attribute = attrs.get(LDAPAttributes.MAIL);
                if (attribute != null) {
                    user.setEmail(String.valueOf(attribute.get()));
                }
                attribute = attrs.get(LDAPAttributes.PHONE);
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
