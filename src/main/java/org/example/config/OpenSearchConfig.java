package org.example.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Value("${opensearch.username:}")
    private String username;

    @Value("${opensearch.password:}")
    private String password;

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, scheme));

        if (username != null && !username.isEmpty()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback((HttpAsyncClientBuilder httpClientBuilder) ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        return builder.build();
    }

    @Bean
    public OpenSearchClient openSearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new OpenSearchClient(transport);
    }
}
