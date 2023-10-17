package util;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.json.JSONObject;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;


public class ElasticSearchClient {

    protected static ElasticsearchClient esClient = initialize();

    public static ElasticsearchClient getESClient() {
        return esClient;
    }

    private static ElasticsearchClient initialize() {
        ElasticsearchClient client = null;
        try {
            final CredentialsProvider credentialsProvider =
                    new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials("elastic", System.getenv("ELASTIC_PASSWORD")));

            Path caCertificatePath = Paths.get("./http_ca.crt");
            CertificateFactory factory =
                    CertificateFactory.getInstance("X.509");
            Certificate trustedCa;
            try (InputStream is = Files.newInputStream(caCertificatePath)) {
                trustedCa = factory.generateCertificate(is);
            }
            KeyStore trustStore = KeyStore.getInstance("pkcs12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", trustedCa);
            SSLContextBuilder sslContextBuilder = SSLContexts.custom()
                    .loadTrustMaterial(trustStore, null);
            final SSLContext sslContext = sslContextBuilder.build();
            RestClientBuilder builder = RestClient.builder(
                            new HttpHost(System.getenv("ELASTIC_HOST"), 9200, "https"))
                    .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(
                                HttpAsyncClientBuilder httpClientBuilder) {
                            httpClientBuilder.setSSLContext(sslContext);
                            httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                            return httpClientBuilder
                                    .setDefaultCredentialsProvider(credentialsProvider);
                        }
                    });

            /*
            final CredentialsProvider credentialsProvider =
                    new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials("elastic", System.getenv("ELASTIC_PASSWORD")));


            RestClientBuilder builder = RestClient.builder(
                            new HttpHost("localhost", 9200))
                    .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(
                                HttpAsyncClientBuilder httpClientBuilder) {
                            //httpClientBuilder.setSSLContext(sslContext);
                            return httpClientBuilder
                                    .setDefaultCredentialsProvider(credentialsProvider);
                        }
                    }); */
            RestClient restClient = builder.build();

            // Create the transport with a Jackson mapper
            ElasticsearchTransport transport = new RestClientTransport(
                    restClient, new JacksonJsonpMapper());

            client = new ElasticsearchClient(transport);

            // And create the API client
             System.out.println("got client" + client);
         } catch(Exception e) {
            e.printStackTrace();
        }
        return client;

    }

    public void insert(JSONObject obj, String index) {
        Reader input = new StringReader(obj.toString(2));

        IndexRequest<JsonData> request = IndexRequest.of(i -> i
                .index(index)
                .withJson(input)
        );

        try {
            IndexResponse response = esClient.index(request);

            System.out.println(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void search(String term, String field, String index) {
        try {
            SearchResponse response = esClient.search(s -> s
                            .index(index)
                            .query(q -> q
                                    .match(t -> t
                                            .field(field)
                                            .query(term)
                                    )
                            ),
                    Void.class
            );

            TotalHits total = response.hits().total();
            System.out.println(total);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        JSONObject obj = new JSONObject();
        obj.put("foo", 1);
        obj.put("bar", 1);
        ElasticSearchClient cl = new ElasticSearchClient();
        cl.insert(obj, "test");
        cl.search("foo", "1","test");
        System.exit(1);
    }
}
