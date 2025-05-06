package ro.cs.tao.github;

import org.kohsuke.github.GHMeta;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.persistence.UserProvider;
import ro.cs.tao.security.Token;
import ro.cs.tao.security.TokenKeeper;
import ro.cs.tao.security.TokenProvider;
import ro.cs.tao.user.User;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class GithubClient implements TokenProvider {

    private static final Logger logger = Logger.getLogger(GithubClient.class.getName());
    private TokenKeeper tokenKeeper;
    private Supplier<UserProvider> userProvider;

    public GithubClient() {
    }

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

        // Exchange GitHub code for access token
        RestTemplate restTemplate = new RestTemplate();
        String url = ConfigurationManager.getInstance().getValue(Keys.GITHUB_AUTH_URL);

        // Create request parameters
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put(Keys.CLIENT_ID, ConfigurationManager.getInstance().getValue(Keys.GITHUB_CLIENT_ID));
        requestParams.put(Keys.CLIENT_SECRET, ConfigurationManager.getInstance().getValue(Keys.GITHUB_CLIENT_SECRET));
        requestParams.put(Keys.AUTH_CODE, code);

        // Create headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        // Create HttpEntity with parameters and headers
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestParams, headers);

        try {
            // Make the POST request
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            // Get response body
            Map<String, Object> responseBody = response.getBody();

            // Extract parameters from the response
            if (responseBody != null) {
                if(responseBody.containsKey(Keys.RESPONSE_ERROR)) {
                    throw new RuntimeException((String) responseBody.get(Keys.RESPONSE_ERROR_DESCRIPTION));
                } else {
                    return new Token((String) responseBody.get(Keys.ACCESS_TOKEN), null, null, (int) 3600 * 8);
                }
            }
        } catch (Exception e) {
            logger.severe(e.getMessage());
        }

        return null;

//        Token token = null;
//
//        try {
//            final GitHub client = new GitHubBuilder().withOAuthToken(code).build();
//            final GHMyself ghUser = client.getMyself();
////            final PagedIterable<GHAuthorization> ghAuthorizations = client.listMyAuthorizations();
////            final PagedIterator<GHAuthorization> iterator = ghAuthorizations.iterator();
////            while (iterator.hasNext()) {
////                final GHAuthorization next = iterator.next();
////                final String nextToken = next.getToken();
////                return new Token(nextToken, null, 3600);
////            }
//
//            Map<String, GHRepository> publicRepos =  ghUser.getRepositories();
//            System.out.println(publicRepos);
//
//            Map<String, GHRepository> allRepos =  ghUser.getAllRepositories();
//            System.out.println(allRepos);
//        } catch (IOException e) {
//            logger.severe(e.getMessage());
//        }
//        return token;
    }

    @Override
    public Token newToken(String refreshToken) {
        return refreshToken(refreshToken);
    }

    private Token refreshToken(String refreshToken) {
        return null;
    }

    public User checkLoginCredentials(final String username, final String password) {
        Token token = null;
        if (this.tokenKeeper != null) {
            token = this.tokenKeeper.getToken(username);
        }
        GHMyself ghUser = null;
        if (token == null) {
            try {
                final GitHub client = new GitHubBuilder().withPassword(username, password).build();
                ghUser = client.getMyself();
                final GHMeta meta = client.getMeta();
            } catch (IOException e) {
                logger.severe(e.getMessage());
                throw new RuntimeException("Invalid credentials supplied");
            }
        }
        if (this.tokenKeeper != null) {
            this.tokenKeeper.put(token, username);
        }
        User user = null;
        if (this.userProvider != null) {
            user = this.userProvider.get().getByName(username);
        }
        return user != null ? user : new GithubUserAdapter().toTaoUser(ghUser);
    }

    public User checkLoginCredentials(final String code) {
        final Token token = newTokenFromCode(code);
        if (token == null) {
            throw new RuntimeException("Invalid access code");
        }
        try {
            final GitHub client = new GitHubBuilder().withOAuthToken(token.getToken()).build();
            final GHMyself ghUser = client.getMyself();
            return new GithubUserAdapter().toTaoUser(ghUser);
        } catch (IOException e) {
            logger.severe(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean validate(String token) {
        if (token == null) return false;
        try {
            final GitHub client = new GitHubBuilder().withOAuthToken(token).build();
            final GHMyself ghUser = client.getMyself();
            if (ghUser != null) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            logger.severe(e.getMessage());
            return false;
        }
    }
}
