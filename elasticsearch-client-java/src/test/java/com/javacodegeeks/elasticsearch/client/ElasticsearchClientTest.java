package com.javacodegeeks.elasticsearch.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkResponse;
// import org.elasticsearch.action.index.IndexRequest.OpType;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
//import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@SpringBootTest(classes = ElasticsearchClientConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class ElasticsearchClientTest {
	private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchClientTest.class);
	
	@Autowired 
	private TransportClient client;
	
	@Value("classpath:catalog-index.json") 
	private Resource index;

	@Test
	public void esClusterIsHealthyUsingHealth() {
		final ClusterHealthResponse response = client
			.admin()
			.cluster()
			.health(
				Requests
					.clusterHealthRequest()
					.waitForGreenStatus()
					.timeout(TimeValue.timeValueSeconds(5))
			)
			.actionGet();

		assertThat(response.isTimedOut())
			.withFailMessage("The cluster is unhealthy: %s", response.getStatus())
			.isFalse();
	}
	
	@Test
	public void esClusterIsHealthyUsingPrepareHealth() {
		final ClusterHealthResponse response = client
			.admin()
			.cluster()
			.prepareHealth()
			.setWaitForGreenStatus()
			.setTimeout(TimeValue.timeValueSeconds(5))
			.execute()
			.actionGet();

		assertThat(response.isTimedOut())
			.withFailMessage("The cluster is unhealthy: %s", response.getStatus())
			.isFalse();
	}
	
	@Test
	public void esClusterDoesIndexExist() throws IOException {
		final IndicesExistsResponse response = client
			.admin()
			.indices()
			.prepareExists("catalog")
			.get(TimeValue.timeValueMillis(100));
		
		if (!response.isExists()) {
			esClusterCreateIndex();
		}
	}
	
	@Test
	public void esIndexBook() throws IOException {
		final XContentBuilder source = JsonXContent
			.contentBuilder()
			.startObject()
			.field("title", "Elasticsearch: The Definitive Guide. A Distributed Real-Time Search and Analytics Engine")
			.startArray("categories")
				.startObject().field("name", "analytics").endObject()
				.startObject().field("name", "search").endObject()
				.startObject().field("name", "database store").endObject()
	        .endArray()
			.field("publisher", "O'Reilly")
			.field("description", "Whether you need full-text search or real-time analytics of structured dataвЂ”or bothвЂ”the Elasticsearch distributed search engine is an ideal way to put your data to work. This practical guide not only shows you how to search, analyze, and explore data with Elasticsearch, but also helps you deal with the complexities of human language, geolocation, and relationships.")
			.field("published_date", new LocalDate(2015, 02, 07).toDate())
			.field("isbn", "978-1449358549")
			.field("rating", 4)
			.endObject();
		
		client
			.prepareIndex("catalog", "books")
			.setId("978-1449358549")
			//.setContentType(XContentType.JSON)
			.setSource(source)
			.setOpType(OpType.INDEX)
			.setRefreshPolicy(RefreshPolicy.WAIT_UNTIL)
			.setTimeout(TimeValue.timeValueMillis(100))
			.execute(new ActionListener<IndexResponse>() {
				@Override
				public void onResponse(IndexResponse response) {
					LOG.info("The document has been indexed with the result: {}", 
						response.getResult());
				}
				
				@Override
				public void onFailure(Exception ex) {
					LOG.error("The document has been not been indexed", ex);
				}
			});
	}

	@Test
	public void esIndexAuthors() throws IOException {
		final XContentBuilder clintonGormley = JsonXContent
			.contentBuilder()
			.startObject()
			.field("first_name", "Clinton")
			.field("last_name", "Gormley")
			.endObject();
		
		final XContentBuilder zacharyTong = JsonXContent
			.contentBuilder()
			.startObject()
			.field("first_name", "Zachary")
			.field("last_name", "Tong")
			.endObject();

		final BulkResponse response = client
			.prepareBulk()
			.add(
				Requests
					.indexRequest("catalog")
					.type("authors")
					.id("1")
					.source(clintonGormley)
					.parent("978-1449358549")
					.opType(OpType.INDEX)
			)
			.add(
				Requests
					.indexRequest("catalog")
					.type("authors")
					.id("2")
					.source(zacharyTong)
					.parent("978-1449358549")
					.opType(OpType.INDEX)
			)
			.setRefreshPolicy(RefreshPolicy.WAIT_UNTIL)
			.setTimeout(TimeValue.timeValueMillis(500))
			.get(TimeValue.timeValueSeconds(1));
		
		assertThat(response.hasFailures())
			.withFailMessage("Bulk operation reported some failures: %s", response.buildFailureMessage())
			.isFalse();
	}
	
	@Test
	public void esSearchAll() {
		final SearchResponse response = client
			.prepareSearch("catalog")
			.setTypes("books")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setQuery(QueryBuilders.matchAllQuery())
			.setFrom(0)
			.setSize(10)
			.setTimeout(TimeValue.timeValueMillis(100))
			.get(TimeValue.timeValueMillis(200));

		assertThat(response.getHits().hits())
			.withFailMessage("Expecting at least one book to be returned")
			.isNotEmpty();
	}

	@Test
	public void esSearch() {
		final QueryBuilder query = QueryBuilders
			.boolQuery()
			.must(
				QueryBuilders
					.rangeQuery("rating")
					.gte(4)
			)
			.must(
				QueryBuilders
					.nestedQuery(
						"categories", 
						QueryBuilders.matchQuery("categories.name", "analytics"),
						ScoreMode.Total
					)
			)
			.must(
				QueryBuilders
					.hasChildQuery(
						"authors", 
						QueryBuilders.termQuery("last_name", "Gormley"),
						ScoreMode.Total
					)
			);
		
		final SearchResponse response = client
			.prepareSearch("catalog")
			.setTypes("books")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setQuery(query)
			.setFrom(0)
			.setSize(10)
			.setFetchSource(
				new String[] { "title", "publisher" }, /* includes */ 
				new String[0] /* excludes */
			)
			.setTimeout(TimeValue.timeValueMillis(10000))
			.get(TimeValue.timeValueMillis(200));

		assertThat(response.getHits().hits())
			.withFailMessage("Expecting at least one book to be returned")
			.extracting("sourceAsString", String.class)
			.hasOnlyOneElementSatisfying(source -> {
				assertThat(source).contains("Elasticsearch: The Definitive Guide.");
			});
	}

	@Test
	public void esAggregations() {
		final AggregationBuilder aggregation = AggregationBuilders
			.terms("publishers")
			.field("publisher")
			.size(10);
		
		final SearchResponse response = client
			.prepareSearch("catalog")
			.setTypes("books")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setQuery(QueryBuilders.matchAllQuery())
			.addAggregation(aggregation)
			.setFrom(0)
			.setSize(10)
			.setTimeout(TimeValue.timeValueMillis(100))
			.get(TimeValue.timeValueMillis(200));

		final StringTerms publishers = response.getAggregations().get("publishers");
		assertThat(publishers.getBuckets())
			.extracting("keyAsString", String.class)
			.contains("O'Reilly");
	}

	private void esClusterCreateIndex() throws IOException {
		try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Streams.copy(index.getInputStream(), out);
			
			final CreateIndexResponse response = client
				.admin()
				.indices()
				.prepareCreate("catalog")
				.setTimeout(TimeValue.timeValueSeconds(1))
				.setSource(out.toByteArray())
				.get(TimeValue.timeValueSeconds(2));
	
			assertThat(response.isAcknowledged())
				.withFailMessage("The index creation has not been acknowledged")
				.isTrue();		
		}
	}
}
