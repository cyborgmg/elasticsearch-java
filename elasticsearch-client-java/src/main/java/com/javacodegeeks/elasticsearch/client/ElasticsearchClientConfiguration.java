package com.javacodegeeks.elasticsearch.client;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchClientConfiguration {
	
	@SuppressWarnings("resource")
	@Bean(destroyMethod = "close")
	TransportClient transportClient() throws UnknownHostException  {
		return new PreBuiltTransportClient(
			Settings.builder().put(ClusterName.CLUSTER_NAME_SETTING.getKey(), "es-catalog").build()
	        )
	        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300));
	}
	
}
