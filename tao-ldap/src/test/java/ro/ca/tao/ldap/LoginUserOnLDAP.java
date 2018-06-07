package ro.ca.tao.ldap;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;


public class LoginUserOnLDAP {

    public static void main(String[] args) throws NamingException {
        String distinguishedName = "CN=Oana Hogoiu,OU=Domain Users OU,DC=C-S,DC=RO";
        String ldapServerUrl = "LDAP://dc.c-s.ro";
        String password = "password";

        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapServerUrl);

        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, distinguishedName);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put("java.naming.ldap.attributes.binary", "objectGUID");

        DirContext context = new InitialDirContext(env);
        System.out.println("The user is connected: distinguishedName='"+distinguishedName+"' \n");


        Attributes attributes = context.getAttributes(distinguishedName);

        int i=0;
        NamingEnumeration<String> ids = attributes.getIDs();
        while (ids.hasMore()) {
            String id = ids.next();
            Object value = attributes.get(id).get();
            i++;
            System.out.println(i+". => user => Attribute: " + id + ", Value: " + value+", value.class: "+value.getClass());
            if ("objectGUID".equalsIgnoreCase(id)) {
                String guidValue = convertToDashedString((byte[])value);
                System.out.println(" guidValue='"+guidValue+"'");
            }
        }

    }

    private static Map<String, Object> checkUserOnLdapServer(final String ldapUsername, String ldapServerUrl, String ldapSearchBase) throws NamingException {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapServerUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, "uid=admin,ou=system");
        env.put(Context.SECURITY_CREDENTIALS, "secret");

        DirContext context = new InitialDirContext(env);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String distinguishedName = "cn=" + ldapUsername + "," + ldapSearchBase;
        NamingEnumeration<SearchResult> enumeration = context.search(distinguishedName, "(objectClass=person)", searchControls);
        // the user exists in the LDAP server

        SearchResult result = enumeration.next();
        Attributes attrs = result.getAttributes();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", ldapUsername);

        NamingEnumeration<? extends Attribute> namingEnumeration = attrs.getAll();
        while (namingEnumeration.hasMore()) {
            Attribute attribute = namingEnumeration.next();
            if (attribute.getID().equalsIgnoreCase("mail")) {
                userMap.put("email", attribute.get().toString());
            }
            else if (attribute.getID().equalsIgnoreCase("givenName")) {
                userMap.put("lastName", attribute.get().toString());
            }
            else if (attribute.getID().equalsIgnoreCase("sn")) {
                userMap.put("firstName", attribute.get().toString());
            }
            else if (attribute.getID().equalsIgnoreCase("uid")) {
                userMap.put("userId", attribute.get().toString());
            }
            else if (attribute.getID().equalsIgnoreCase("telephoneNumber")) {
                userMap.put("telephone", attribute.get().toString());
            }
        }
        return userMap;
    }

    public static String convertToDashedString(byte[] objectGUID) {
        StringBuilder displayStr = new StringBuilder();
        displayStr.append(prefixZeros((int) objectGUID[3] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[2] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[1] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[0] & 0xFF));
        displayStr.append("-");
        displayStr.append(prefixZeros((int) objectGUID[5] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[4] & 0xFF));
        displayStr.append("-");
        displayStr.append(prefixZeros((int) objectGUID[7] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[6] & 0xFF));
        displayStr.append("-");
        displayStr.append(prefixZeros((int) objectGUID[8] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[9] & 0xFF));
        displayStr.append("-");
        displayStr.append(prefixZeros((int) objectGUID[10] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[11] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[12] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[13] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[14] & 0xFF));
        displayStr.append(prefixZeros((int) objectGUID[15] & 0xFF));
        return displayStr.toString();
    }


    private static String prefixZeros(int value) {
        if (value <= 0xF) {
            StringBuilder sb = new StringBuilder("0");
            sb.append(Integer.toHexString(value));

            return sb.toString();

        } else {
            return Integer.toHexString(value);
        }
    }
}
