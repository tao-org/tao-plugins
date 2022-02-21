/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.tao.ldap;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.user.User;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LDAP client used for authentication
 *
 * @author Oana H.
 */
public class TaoLdapClient {

    private static final Logger logger = Logger.getLogger(TaoLdapClient.class.getName());

    // LDAP configuration properties
    private final String initialContextFactory;
    private final String domain;
    private final String providerUrl;
    private final String securityAuthentication;

    public TaoLdapClient() {
        // read LDAP settings from configuration
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        initialContextFactory = configurationProvider.getValue("ldap.context.initial.context.factory");
        domain = configurationProvider.getValue("ldap.domain");
        providerUrl = configurationProvider.getValue("ldap.context.provider.url");
        securityAuthentication = configurationProvider.getValue("ldap.context.security.authentication");
    }

    public User checkLoginCredentials(final String username, final String password){
        final String simpleUserName = username.contains("@") ? username.split("@")[0] : username;
        final String userDomain = (domain == null && username.contains("@")) ? username.split("@")[1] : domain;
        final Hashtable<String, String> ldapEnv = new Hashtable<>();
        ldapEnv.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
        ldapEnv.put(Context.PROVIDER_URL, providerUrl);
        ldapEnv.put(Context.SECURITY_AUTHENTICATION, securityAuthentication);
        ldapEnv.put(Context.SECURITY_PRINCIPAL, simpleUserName + "@" + userDomain);
        ldapEnv.put(Context.SECURITY_CREDENTIALS, password);
        User user = null;
        try {
            DirContext ctx = new InitialDirContext(ldapEnv);
            logger.fine("User " + username + " logged in");
            final StringBuilder dn = new StringBuilder();
            if (userDomain != null) {
                final String[] tokens = userDomain.split("\\.");
                for (String token : tokens) {
                    dn.append("DC=").append(token).append(",");
                }
                dn.setLength(dn.length() - 1);
            }
            SearchControls cons = new SearchControls();
            cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
            String[] attrIDs = {"distinguishedName", "sn", "givenname", "mail", "telephonenumber"};
            cons.setReturningAttributes(attrIDs);
            NamingEnumeration<SearchResult> answer = ctx.search(dn.toString(), "samAccountName=" + simpleUserName, cons);
            if (answer.hasMore()) {
                user = new User();
                Attributes attrs = answer.next().getAttributes();
                user.setUsername(username);
                //user.setPassword(Crypto.encrypt(password, username));
                user.setExternal(true);
                user.setFirstName(attrs.get("givenname").get().toString());
                user.setLastName(attrs.get("sn").get().toString());
                user.setEmail(attrs.get("mail").get().toString());
                final Attribute telephonenumber = attrs.get("telephonenumber");
                if (telephonenumber != null) {
                    final Object telObj = telephonenumber.get();
                    if (telObj != null) {
                        user.setPhone(telObj.toString());
                    }
                }
                user.setOrganization("n/a");
                user.setCreated(LocalDateTime.now());
            }
        } catch (AuthenticationException e) {
            logger.fine("Invalid login credentials!");
            logger.log(Level.FINE, e.getMessage());
        } catch (NamingException e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
        return user;
    }
}
