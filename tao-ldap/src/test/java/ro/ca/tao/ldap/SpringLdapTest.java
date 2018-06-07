package ro.ca.tao.ldap;

import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.DirContextAuthenticationStrategy;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;

public class SpringLdapTest {

    public static void main(String[] args){
        String password = "xxx";
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://dc.c-s.ro");
        contextSource.setBase("DC=C-S,DC=RO");
        contextSource.setAnonymousReadOnly(true);
        contextSource.setUserDn("uid=admin,ou=system");
        contextSource.setPassword("secret");
        contextSource.afterPropertiesSet();

        LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
        try {
            ldapTemplate.afterPropertiesSet();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Perform the authentication.
        Filter filter = new EqualsFilter("sAMAccountName", "oana");

        /*boolean authenticated = ldapTemplate.authenticate("OU=Domain Users OU",
          filter.encode(),
          "password");*/

        boolean authenticated = ldapTemplate.authenticate("OU=Domain Users OU,DC=C-S,DC=RO",
          filter.encode(),
          password);

        // Display the results.
        System.out.println("Authenticated: " + authenticated);
    }
}
