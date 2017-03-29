package apoc.index;

import apoc.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;

import static apoc.util.TestUtil.*;
import static org.junit.Assert.*;

public class IndexUpdateTransactionEventHandlerTest {

    private GraphDatabaseService db;

    @Before
    public void setUp() throws Exception {
        db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        db.registerTransactionEventHandler(new IndexUpdateTransactionEventHandler((GraphDatabaseAPI) db, false));
        TestUtil.registerProcedure(db, FreeTextSearch.class);
    }

    @After
    public void tearDown() {
        db.shutdown();
    }

    @Test
    public void shouldDeletingIndexedNodesSucceed() {
        // setup: create index, add a node
        testCallEmpty(db, "call apoc.index.addAllNodesExtended('search_index',{City:['name']},{autoUpdate:true})", null);
        testCallEmpty(db, "create (c:City{name:\"Made Up City\",url:\"/places/nowhere/made-up-city\"})", null);

        // check if we find the node
        testCallCount(db, "start n=node:search_index('name:\"Made Up\"') return n", null, 1);

        // when
        TestUtil.testCall(db, "match (c:City{name:'Made Up City'}) delete c return count(c) as count", map -> assertEquals(1L, map.get("count")));

        // nothing found in the index after deletion
        testCallCount(db, "start n=node:search_index('name:\"Made Up\"') return n", null, 0);

    }
}
