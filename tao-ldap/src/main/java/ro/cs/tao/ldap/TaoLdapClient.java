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
import java.util.Hashtable;
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
        // for returning the guid as byte[]
        ldapEnv.put("java.naming.ldap.attributes.binary", "ObjectGUID");
        User user = null;
        try {
            DirContext ctx = new InitialDirContext(ldapEnv);
            logger.finest("LDAP login: " + username);
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
            cons.setReturningAttributes(LDAPAttributes.ALL);
            NamingEnumeration<SearchResult> answer = ctx.search(dn.toString(), LDAPAttributes.ACCOUNT + "=" + simpleUserName, cons);
            if (answer.hasMore()) {
                final Attributes attrs = answer.next().getAttributes();
                user = new LdapUserAdapter().toTaoUser(attrs);
            }
        } catch (AuthenticationException e) {
            logger.warning("Invalid login credentials! [" + e.getMessage() + "]");
        } catch (NamingException e) {
            logger.severe(e.getMessage());
        }
        return user;
    }
}
