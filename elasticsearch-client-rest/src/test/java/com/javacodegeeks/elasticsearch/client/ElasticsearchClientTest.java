package com.javacodegeeks.elasticsearch.client;

import static com.jayway.jsonpath.Configuration.defaultConfiguration;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.minidev.json.JSONObject;

@SpringBootTest(classes = ElasticsearchClientConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class ElasticsearchClientTest {
	private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchClientTest.class);
	
	@Autowired private RestClient client;

	@Test
	public void esClusterIsHealthy() throws Exception {
		final Response response = client
			.performRequest(HttpGet.METHOD_NAME, "_cluster/health", emptyMap());

		final Object json = defaultConfiguration()
				.jsonProvider()
				.parse(EntityUtils.toString(response.getEntity()));
		
		assertThat(json, hasJsonPath("$.status", equalTo("green")));
	}
	
	@Test
	public void esIndexBook() throws IOException {
		final Map<String, Object> source = new LinkedHashMap<>();
		source.put("title", "Elasticsearch: The Definitive Guide. A Distributed Real-Time Search and Analytics Engine");
		source.put("categories", 
			new Map[] {
				singletonMap("name", "analytics"),
				singletonMap("name", "search"),
				singletonMap("name", "database store")
		    }
		);
	    source.put("publisher", "O'Reilly");
	    source.put("description", "Whether you need full-text search or real-time analytics of structured dataвЂ”or bothвЂ”the Elasticsearch distributed search engine is an ideal way to put your data to work. This practical guide not only shows you how to search, analyze, and explore data with Elasticsearch, but also helps you deal with the complexities of human language, geolocation, and relationships.");
	    source.put("published_date", "2015-02-07");
	    source.put("isbn", "978-1449358549");
	    source.put("rating", 4);
	    
	    final HttpEntity payload = new NStringEntity(JSONObject.toJSONString(source), 
	    	ContentType.APPLICATION_JSON);
		
		client.performRequestAsync(
			HttpPut.METHOD_NAME, 
			"catalog/books/978-1449358549",
			emptyMap(),
			payload,
			new ResponseListener() {
				@Override
				public void onSuccess(Response response) {
					LOG.info("The document has been indexed successfully");
				}
				
				@Override
				public void onFailure(Exception ex) {
					LOG.error("The document has been not been indexed", ex);
				}
			});
	}

	@Test
	public void esSearch() throws IOException {
		final Map<String, Object> authors = new LinkedHashMap<>();
		authors.put("type", "authors");
		authors.put("query", 
			singletonMap("term",
				singletonMap("last_name", "Gormley")
			)
		);
		
		final Map<String, Object> categories = new LinkedHashMap<>();
		categories.put("path", "categories");
		categories.put("query",
			singletonMap("match", 
				singletonMap("categories.name", "search")
            )
        );
		
		final Map<String, Object> query = new LinkedHashMap<>();
		query.put("size", 10);
		query.put("_source", new String[] { "title", "publisher" });
		query.put("query", 
			singletonMap("bool",
				singletonMap("must", new Map[] {
					singletonMap("range",
						singletonMap("rating", 
							singletonMap("gte", 4)
						)
					),
					singletonMap("has_child", authors),
					singletonMap("nested", categories)
				})
			)
		);

	    final HttpEntity payload = new NStringEntity(JSONObject.toJSONString(query), 
		    ContentType.APPLICATION_JSON);

		final Response response = client
			.performRequest(HttpPost.METHOD_NAME, "catalog/books/_search", 
				emptyMap(), payload);

		final Object json = defaultConfiguration()
			.jsonProvider()
			.parse(EntityUtils.toString(response.getEntity()));

		assertThat(json, hasJsonPath("$.hits.hits[0]._source.title", 
			containsString("Elasticsearch: The Definitive Guide.")));
	}
}
