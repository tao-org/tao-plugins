package ro.cs.tao.ldap;

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
