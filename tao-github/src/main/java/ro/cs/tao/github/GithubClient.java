package ro.cs.tao.github;

import org.kohsuke.github.*;
import ro.cs.tao.persistence.UserProvider;
import ro.cs.tao.security.Token;
import ro.cs.tao.security.TokenKeeper;
import ro.cs.tao.security.TokenProvider;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class GithubClient implements TokenProvider {

    private static final Logger logger = Logger.getLogger(GithubClient.class.getName());
    private TokenKeeper tokenKeeper;
    private Supplier<UserProvider> userProvider;

    public void setTokenKeeper(TokenKeeper tokenKeeper) {
        this.tokenKeeper = tokenKeeper;
    }

    public void setUserProvider(Supplier<UserProvider> provider) {
        this.userProvider = provider;
    }

    @Override
    public Token newToken(String user, String password) {
        Token token = null;
        if (this.tokenKeeper != null) {
            token = this.tokenKeeper.getToken(user);
        }
        if (token == null) {
            try {
                final GitHub client = new GitHubBuilder().withPassword(user, password).build();
                final GHMyself ghUser = client.getMyself();
                final GHMeta meta = client.getMeta();
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
        return token;
    }

    @Override
    public Token newTokenFromCode(String code) {
        Token token = null;
        try {
            final GitHub client = new GitHubBuilder().withOAuthToken(code).build();
            final GHMyself ghUser = client.getMyself();
            final PagedIterable<GHAuthorization> ghAuthorizations = client.listMyAuthorizations();
            final PagedIterator<GHAuthorization> iterator = ghAuthorizations.iterator();
            while (iterator.hasNext()) {
                final GHAuthorization next = iterator.next();
                final String nextToken = next.getToken();
                return new Token(nextToken, null, 3600);
            }
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
        return token;
    }
}
