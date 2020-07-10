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

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
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
    private String initialContextFactory;
    private String providerUrl;
    private String securityAuthentication;

    public TaoLdapClient() {
        // read LDAP settings from configuration
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        initialContextFactory = configurationProvider.getValue("ldap.context.initial.context.factory");
        providerUrl = configurationProvider.getValue("ldap.context.provider.url");
        securityAuthentication = configurationProvider.getValue("ldap.context.security.authentication");
    }

    public boolean checkLoginCredentials(final String username, final String password){

        final Hashtable ldapEnv = new Hashtable();
        ldapEnv.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
        ldapEnv.put(Context.PROVIDER_URL, providerUrl);
        ldapEnv.put(Context.SECURITY_AUTHENTICATION, securityAuthentication);
        ldapEnv.put(Context.SECURITY_PRINCIPAL, username);
        ldapEnv.put(Context.SECURITY_CREDENTIALS, password);

        try {
            DirContext ctx = new InitialDirContext(ldapEnv);
            logger.fine("Login successful for user " + username);
            return true;

        } catch (AuthenticationException e) {
            logger.fine("Invalid login credentials!");
            logger.log(Level.FINE, e.getMessage());
            return false;

        } catch (NamingException e) {
            logger.log(Level.SEVERE, e.getMessage());
            return false;
        }
    }
}
