package ro.cs.tao.datasource.remote.lsa.auth;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class KeycloackLogin {
    public static class LoginForm {
        public String action;
        public String credentialId;
    }

    public static void main(String[] args) throws Exception {
        String loginUrl = "https://sso.collgs.lu/auth/realms/lucollgs/protocol/openid-connect/auth" +
                "?client_id=lucollgs-dl&response_type=code&redirect_uri=https://collgs.lu/callback&scope=openid";

        // Cookie storage
        BasicCookieStore cookieStore = new BasicCookieStore();
        CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build();

        // Step 1: GET login page to initialize session
        HttpGet initialGet = new HttpGet(loginUrl);
        CloseableHttpResponse loginPageResponse = client.execute(initialGet);
        String loginHtml = new BufferedReader(new InputStreamReader(loginPageResponse.getEntity().getContent()))
                .lines().reduce("", (acc, line) -> acc + line);
        loginPageResponse.close();

        // Step 2: Parse form from login page
        LoginForm form = KeycloackLogin.parseLoginForm(loginHtml);

        String actionUrl = form.action;
        String credentialId = form.credentialId;

        // Step 3: POST credentials
        HttpPost loginPost = new HttpPost(actionUrl);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("username", "cudroiu")); // <-- change this
        params.add(new BasicNameValuePair("password", "Anathema@1977#")); // <-- change this
        params.add(new BasicNameValuePair("credentialId", credentialId));

        loginPost.setEntity(new UrlEncodedFormEntity(params));
        loginPost.setHeader("Content-Type", "application/x-www-form-urlencoded");

        CloseableHttpResponse loginResponse = client.execute(loginPost);
        //int statusCode = loginResponse.getCode();
        String redirectLocation = Arrays.stream(loginResponse.getHeaders("Location"))
                .map(Header::getValue).findFirst().orElse("N/A");

        //System.out.println("Login POST response code: " + statusCode);
        System.out.println("Redirect Location (if any): " + redirectLocation);
        loginResponse.close();

        // Step 4: Dump cookies
        System.out.println("\nAuthenticated Cookies:");
        for (Cookie c : cookieStore.getCookies()) {
            System.out.println(c.getName() + " = " + c.getValue());
        }

        // Client now has an authenticated session and can download protected resources
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client2 = HttpClient.newBuilder()
                //.cookieHandler(cookieManager)
                //.followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        // Step 1: manually set the known working connect.sid
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://collgs.lu/repository/data_hsm/Sentinel-2/C/MSI/Level-1C/S2MSI1C/2025/06/06/S2C_MSIL1C_20250606T055651_N0511_R091_T42RVP_20250606T074117.zip"))
//                .header("User-Agent", "Mozilla/5.0")
//                .header("Accept", "application/zip")
                .header("Cookie", "connect.sid=s%3AHI46_dptUjhJaGj1v6c2iaxn9f2Q5vxo.8U5bTa0bEQ3yT8OJ0M96mgykyT3oVeDR4dzTbDWWDUI;")
                .GET()
                .build();

        HttpResponse<Path> response = client2.send(request,
                HttpResponse.BodyHandlers.ofFile(Paths.get("download.zip")));

        System.out.println("Status: " + response.statusCode());
        System.out.println("Downloaded to: " + response.body());
//        HttpRequest downloadRequest = HttpRequest.newBuilder()
//                .uri(URI.create("https://collgs.lu/repository/data_hsm/Sentinel-1/A/SAR-C/L1/SLC/2025/05/17/S1A_IW_SLC__1SDV_20250517T011728_20250517T011755_059227_07597C_413F.zip"))
//                .header("User-Agent", "Java HttpClient")
//                .GET()
//                .build();
//
//        HttpClient client2 = HttpClient.newBuilder()
//                .followRedirects(HttpClient.Redirect.ALWAYS)
//                .cookieHandler(new java.net.CookieManager())
//                .build();
//        HttpResponse<InputStream> zipResponse = client2.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
//
//        String contentType = zipResponse.headers().firstValue("Content-Type").orElse("unknown");
//        System.out.println("Download Content-Type: " + contentType);
//
//        if (contentType.equals("application/zip")) {
//            try (InputStream is = zipResponse.body();
//                 FileOutputStream fos = new FileOutputStream("output.zip")) {
//                is.transferTo(fos);
//                System.out.println("Downloaded ZIP to output.zip");
//            }
//        } else {
//            System.out.println("Unexpected content type. Got HTML instead of ZIP?");
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipResponse.body()))) {
//                reader.lines().forEach(System.out::println);
//            }
//        }
    }

    public static final short execute(short value) {
        return (short) (255.0 / (1.0 + (short)Math.exp(-4.0 * (((value - 1000.0) / 1500.0) - 0.5))));
    }

    public static LoginForm parseLoginForm(String html) throws Exception {
        // Wrap HTML in a dummy root tag if needed
        String cleanedHtml = html.replaceAll("&(?!amp;|lt;|gt;|quot;|#\\d+;)", "&amp;");

        // Add dummy root if necessary
        String wrappedHtml = "<!DOCTYPE html><html>" + cleanedHtml + "</html>";

        // InputStream for parser
        InputStream input = new ByteArrayInputStream(wrappedHtml.getBytes(StandardCharsets.UTF_8));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
//        Document doc = builder.parse(input);
//
//        XPathFactory xPathFactory = XPathFactory.newInstance();
//        XPath xpath = xPathFactory.newXPath();
//
        // Extract form action
        String action = "https://sso.collgs.lu/auth/realms/lucollgs/login-actions/authenticate?session_code=_nBJb6ARn2ogo5WIObXsMZgQbwvJNbZuNDl-fF00ouc&amp;execution=1bd083cf-1bb3-4b72-beb3-2b4f026b475a&amp;client_id=lucollgs-dl&amp;tab_id=OulMrdPRuXU"; //xpath.evaluate("//form[@id='kc-form-login']/@action", doc);
        String credentialId = "";//xpath.evaluate("//input[@name='credentialId']/@value", doc);

        LoginForm form = new LoginForm();
        form.action = action;
        form.credentialId = credentialId;
        return form;
    }
}