package com.javacodegeeks.elasticsearch.client;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig.Builder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder.RequestConfigCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchClientConfiguration {
	@Bean(destroyMethod = "close")
	RestClient transportClient() {
		return RestClient
			.builder(new HttpHost("localhost", 9200))
			.setRequestConfigCallback(new RequestConfigCallback() {
	            @Override
	            public Builder customizeRequestConfig(Builder builder) {
	                return builder
	                	.setConnectTimeout(1000)
	                    .setSocketTimeout(5000);
	            }
	        })
			.build();
	}
}
