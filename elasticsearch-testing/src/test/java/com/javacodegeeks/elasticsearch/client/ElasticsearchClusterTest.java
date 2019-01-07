package com.javacodegeeks.elasticsearch.client;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@ClusterScope(numDataNodes = 3)
public class ElasticsearchClusterTest extends ESIntegTestCase {
	@Before
	public void setUpCatalog() throws IOException {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Streams.copy(getClass().getResourceAsStream("/catalog-index.json"), out);
			
			final CreateIndexResponse response = admin()
				.indices()
				.prepareCreate("catalog")
				.setSource(out.toByteArray())
				.get();
			
			assertAcked(response);
			ensureGreen("catalog");
        }
	}

	@After
	public void tearDownCatalog() throws IOException, InterruptedException {
		cluster().wipeIndices("catalog");
	}

	@Test
	public void testEmptyCatalogHasNoBooks() {
		final SearchResponse response = client()
			.prepareSearch("catalog")
			.setTypes("books")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setQuery(QueryBuilders.matchAllQuery())
			.setFetchSource(false)
			.get();

		assertNoSearchHits(response);	
	}

	@Test
	public void testClusterNodeIsDown() throws IOException {
		internalCluster().stopRandomDataNode();
        
		final SearchResponse response = client()
			.prepareSearch("catalog")
			.setTypes("books")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setQuery(QueryBuilders.matchAllQuery())
			.setFetchSource(false)
			.get();

		assertNoSearchHits(response);
	}

	@Test
	public void testInsertAndSearchForBook() throws IOException {
		final XContentBuilder source = JsonXContent
			.contentBuilder()
			.startObject()
			.field("title", randomAsciiOfLength(100))
			.startArray("categories")
				.startObject().field("name", "analytics").endObject()
				.startObject().field("name", "search").endObject()
				.startObject().field("name", "database store").endObject()
	        .endArray()
			.field("publisher", randomAsciiOfLength(20))
			.field("description", randomAsciiOfLength(200))
			.field("published_date", new LocalDate(2015, 02, 07).toDate())
			.field("isbn", "978-1449358549")
			.field("rating", randomInt(5))
			.endObject();
		
		index("catalog", "books", "978-1449358549", source);
		refresh("catalog");
		
    	final QueryBuilder query = QueryBuilders
			.nestedQuery(
				"categories", 
				QueryBuilders.matchQuery("categories.name", "analytics"),
				ScoreMode.Total
			);
    	
    	final SearchResponse response = client()
			.prepareSearch("catalog")
			.setTypes("books")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setQuery(query)
			.setFetchSource(false)
			.get();

    	assertSearchHits(response, "978-1449358549");		
	}
}
