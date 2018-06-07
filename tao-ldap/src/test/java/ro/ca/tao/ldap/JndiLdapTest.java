package ro.ca.tao.ldap;


import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

public class JndiLdapTest {
    public static void main(String[] args){

        String username = "C-S\\oana";
        String password = "";

        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://dc.c-s.ro/");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, username);
        env.put(Context.SECURITY_CREDENTIALS, password);

        try {
            DirContext ctx = new InitialDirContext(env);
            System.out.println("Ok");
        } catch (AuthenticationException e) {
            System.out.println(e.getMessage());
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }
}
