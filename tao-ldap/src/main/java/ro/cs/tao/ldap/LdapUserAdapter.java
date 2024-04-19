package ro.cs.tao.ldap;

import ro.cs.tao.user.User;
import ro.cs.tao.user.UserModelAdapter;
import ro.cs.tao.user.UserType;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

public class LdapUserAdapter implements UserModelAdapter<Attributes> {
    private final Pattern guidPattern = Pattern.compile("([0-9a-f\\-]{16,20})");
    @Override
    public User toTaoUser(Attributes attrs) {
        User user = null;
        if (attrs != null) {
            try {
                user = new User();
                final Object guid = attrs.get(LDAPAttributes.GUID).get();
                if (!guidPattern.matcher(guid.toString()).matches()) {
                    user.setId(convertToDashedString((byte[]) guid));
                } else {
                    user.setId(guid.toString());
                }
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
    public String convertToDashedString(byte[] objectGUID) {
        return prefixZeros((int) objectGUID[3] & 0xFF) +
                prefixZeros((int) objectGUID[2] & 0xFF) +
                prefixZeros((int) objectGUID[1] & 0xFF) +
                prefixZeros((int) objectGUID[0] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[5] & 0xFF) +
                prefixZeros((int) objectGUID[4] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[7] & 0xFF) +
                prefixZeros((int) objectGUID[6] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[8] & 0xFF) +
                prefixZeros((int) objectGUID[9] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[10] & 0xFF) +
                prefixZeros((int) objectGUID[11] & 0xFF) +
                prefixZeros((int) objectGUID[12] & 0xFF) +
                prefixZeros((int) objectGUID[13] & 0xFF) +
                prefixZeros((int) objectGUID[14] & 0xFF) +
                prefixZeros((int) objectGUID[15] & 0xFF);
    }


    private String prefixZeros(int value) {
        return value <= 0xF
            ? "0" + Integer.toHexString(value)
            : Integer.toHexString(value);
    }

}
