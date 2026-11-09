package ro.cs.tao.ldap;

/**
 * The LDAPAttributes class defines constant values for commonly used LDAP attribute names.
 * It also provides an array containing all the attributes defined in this class.
 * These constants can be used to retrieve specific attributes of LDAP entries during directory operations.
 */
public class LDAPAttributes {
    static final String ACCOUNT = "samAccountName";
    static final String DN = "distinguishedName";
    static final String SN = "sn";
    static final String NAME = "givenName";
    static final String MAIL = "samAccountName";
    static final String PHONE = "telephonenumber";
    static final String GUID = "objectGUID";

    static final String[] ALL = new String[] { ACCOUNT, DN, SN, NAME, MAIL, PHONE, GUID };

}
