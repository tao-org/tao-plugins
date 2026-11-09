import ro.cs.tao.github.GithubClient;

public class GithubTest {

    public static void main(String[] args) {
        GithubClient client = new GithubClient();
        client.newTokenFromCode("<my personal access token>");
        System.exit(0);
    }
}
