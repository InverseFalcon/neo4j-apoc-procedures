package apoc.merge;

import apoc.util.MapUtil;
import apoc.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.util.Map;

import static apoc.util.TestUtil.testCall;
import static apoc.util.TestUtil.testResult;
import static org.junit.Assert.*;

public class MergeTest {

    private GraphDatabaseService db;

    @Before
    public void setUp() throws Exception {
        db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        TestUtil.registerProcedure(db, Merge.class);
    }

    @After
    public void tearDown() {
        db.shutdown();
    }

    @Test
    public void testMergeNode() throws Exception {
        testCall(db, "CALL apoc.merge.node(['Person','Bastard'],{ssid:'123'}, {name:'John'}) YIELD node RETURN node",
                (row) -> {
                    Node node = (Node) row.get("node");
                    assertEquals(true, node.hasLabel(Label.label("Person")));
                    assertEquals(true, node.hasLabel(Label.label("Bastard")));
                    assertEquals("John", node.getProperty("name"));
                    assertEquals("123", node.getProperty("ssid"));
                });
    }

    @Test
    public void testMergeNodeWithPreExisting() throws Exception {
        db.execute("CREATE (p:Person{ssid:'123', name:'Jim'})");
        testCall(db, "CALL apoc.merge.node(['Person'],{ssid:'123'}, {name:'John'}) YIELD node RETURN node",
                (row) -> {
                    Node node = (Node) row.get("node");
                    assertEquals(true, node.hasLabel(Label.label("Person")));
                    assertEquals("Jim", node.getProperty("name"));
                    assertEquals("123", node.getProperty("ssid"));
                });

        testResult(db, "match (p:Person) return count(*) as c", result ->
                assertEquals(1, (long)(Iterators.single(result.columnAs("c"))))
        );
    }

    @Test
    public void testMergeRelationships() throws Exception {
        db.execute("create (:Person{name:'Foo'}), (:Person{name:'Bar'})");

        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship(s, 'KNOWS', {rid:123}, {since:'Thu'}, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("KNOWS", rel.getType().name());
                    assertEquals(123l, rel.getProperty("rid"));
                    assertEquals("Thu", rel.getProperty("since"));
                });

        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship(s, 'KNOWS', {rid:123}, {since:'Fri'}, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("KNOWS", rel.getType().name());
                    assertEquals(123l, rel.getProperty("rid"));
                    assertEquals("Thu", rel.getProperty("since"));
                });
        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship(s, 'OTHER', null, null, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("OTHER", rel.getType().name());
                    assertTrue(rel.getAllProperties().isEmpty());
                });
    }

    @Test
    public void testMergeWithEmptyIdentityPropertiesShouldFail() {
        for (String idProps: new String[]{"null", "{}"}) {
            try {
                testCall(db, "CALL apoc.merge.node(['Person']," + idProps +", {name:'John'}) YIELD node RETURN node",
                        row -> assertTrue(row.get("node") instanceof Node));
                fail();
            } catch (QueryExecutionException e) {
                assertTrue(e.getMessage().contains("you need to supply at least one identifying property for a merge"));
            }
        }
    }

    @Test
    public void testLabelsWithSpecialCharactersShouldWork() {
        for (String label: new String[]{"Label with spaces", ":LabelWithColon", "label-with-dash", "LabelWithUmlautsÄÖÜ"}) {
            Map<String, Object> params = MapUtil.map("label", label);
            testCall(db, "CALL apoc.merge.node([$label],{id:1}, {name:'John'}) YIELD node RETURN node", params,
                    row -> assertTrue(row.get("node") instanceof Node));
        }
    }

    @Test
    public void testRelationshipTypesWithSpecialCharactersShouldWork() {
        for (String relType: new String[]{"Reltype with space", ":ReltypeWithCOlon", "rel-type-with-dash"}) {
            Map<String, Object> params = MapUtil.map("relType", relType);
            testCall(db, "CREATE (a), (b) WITH a,b CALL apoc.merge.relationship(a, $relType, null, null, b) YIELD rel RETURN rel", params,
                    row -> assertTrue(row.get("rel") instanceof Relationship));
        }
    }


    // MERGE EAGER TESTS


    @Test
    public void testMergeEagerNode() throws Exception {
        testCall(db, "CALL apoc.merge.node.eager(['Person','Bastard'],{ssid:'123'}, {onCreateProps:{name:'John'}}) YIELD node RETURN node",
                (row) -> {
                    Node node = (Node) row.get("node");
                    assertEquals(true, node.hasLabel(Label.label("Person")));
                    assertEquals(true, node.hasLabel(Label.label("Bastard")));
                    assertEquals("John", node.getProperty("name"));
                    assertEquals("123", node.getProperty("ssid"));
                });
    }

    @Test
    public void testMergeEagerNodeWithOnCreate() throws Exception {
        testCall(db, "CALL apoc.merge.node.eager(['Person','Bastard'],{ssid:'123'}, {onCreateProps:{name:'John'}, onMatchProps:{occupation:'juggler'}}) YIELD node RETURN node",
                (row) -> {
                    Node node = (Node) row.get("node");
                    assertEquals(true, node.hasLabel(Label.label("Person")));
                    assertEquals(true, node.hasLabel(Label.label("Bastard")));
                    assertEquals("John", node.getProperty("name"));
                    assertEquals("123", node.getProperty("ssid"));
                    assertFalse(node.hasProperty("occupation"));
                });
    }

    @Test
    public void testMergeEagerNodeWithOnMatch() throws Exception {
        db.execute("CREATE (p:Person:Bastard {ssid:'123'})");
        testCall(db, "CALL apoc.merge.node.eager(['Person','Bastard'],{ssid:'123'}, {onCreateProps:{name:'John'}, onMatchProps:{occupation:'juggler'}}) YIELD node RETURN node",
                (row) -> {
                    Node node = (Node) row.get("node");
                    assertEquals(true, node.hasLabel(Label.label("Person")));
                    assertEquals(true, node.hasLabel(Label.label("Bastard")));
                    assertEquals("juggler", node.getProperty("occupation"));
                    assertEquals("123", node.getProperty("ssid"));
                    assertFalse(node.hasProperty("name"));
                });
    }

    @Test
    public void testMergeEagerNodesWithOnMatchCanMergeOnMultipleMatches() throws Exception {
        db.execute("UNWIND range(1,5) as index MERGE (:Person:`Bastard Man`{ssid:'123', index:index})");

        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("CALL apoc.merge.node.eager(['Person','Bastard Man'],{ssid:'123'}, {onCreateProps:{name:'John'}, onMatchProps:{occupation:'juggler'}}) YIELD node RETURN node");

            for (long index = 1; index <= 5; index++) {
                Node node = (Node) result.next().get("node");
                assertEquals(true, node.hasLabel(Label.label("Person")));
                assertEquals(true, node.hasLabel(Label.label("Bastard Man")));
                assertEquals("123", node.getProperty("ssid"));
                assertEquals(index, node.getProperty("index"));
                assertEquals("juggler", node.getProperty("occupation"));
                assertFalse(node.hasProperty("name"));
            }
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testMergeEagerRelationships() throws Exception {
        db.execute("create (:Person{name:'Foo'}), (:Person{name:'Bar'})");

        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship.eager(s, 'KNOWS', {rid:123}, {onCreateProps:{since:'Thu'}}, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("KNOWS", rel.getType().name());
                    assertEquals(123l, rel.getProperty("rid"));
                    assertEquals("Thu", rel.getProperty("since"));
                });

        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship.eager(s, 'KNOWS', {rid:123}, {onCreateProps:{since:'Fri'}}, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("KNOWS", rel.getType().name());
                    assertEquals(123l, rel.getProperty("rid"));
                    assertEquals("Thu", rel.getProperty("since"));
                });
        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship(s, 'OTHER', null, null, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("OTHER", rel.getType().name());
                    assertTrue(rel.getAllProperties().isEmpty());
                });
    }

    @Test
    public void testMergeEagerRelationshipsWithOnMatch() throws Exception {
        db.execute("create (:Person{name:'Foo'}), (:Person{name:'Bar'})");

        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship.eager(s, 'KNOWS', {rid:123}, {onCreateProps:{since:'Thu'}, onMatchProps:{until:'Saturday'}}, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("KNOWS", rel.getType().name());
                    assertEquals(123l, rel.getProperty("rid"));
                    assertEquals("Thu", rel.getProperty("since"));
                    assertFalse(rel.hasProperty("until"));
                });

        testCall(db, "MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship.eager(s, 'KNOWS', {rid:123}, {onMatchProps:{since:'Fri'}}, e) YIELD rel RETURN rel",
                (row) -> {
                    Relationship rel = (Relationship) row.get("rel");
                    assertEquals("KNOWS", rel.getType().name());
                    assertEquals(123l, rel.getProperty("rid"));
                    assertEquals("Fri", rel.getProperty("since"));
                });
    }

    @Test
    public void testMergeEagerRelationshipsWithOnMatchCanMergeOnMultipleMatches() throws Exception {
        db.execute("CREATE (foo:Person{name:'Foo'}), (bar:Person{name:'Bar'}) WITH foo, bar UNWIND range(1,3) as index CREATE (foo)-[:KNOWS {rid:123}]->(bar)");

        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("MERGE (s:Person{name:'Foo'}) MERGE (e:Person{name:'Bar'}) WITH s,e CALL apoc.merge.relationship.eager(s, 'KNOWS', {rid:123}, {onMatchProps:{since:'Fri'}}, e) YIELD rel RETURN rel");

            for (long index = 1; index <= 3; index++) {
                Relationship rel = (Relationship) result.next().get("rel");
                assertEquals("KNOWS", rel.getType().name());
                assertEquals(123l, rel.getProperty("rid"));
                assertEquals("Fri", rel.getProperty("since"));
            }
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testMergeEagerWithEmptyIdentityPropertiesShouldFail() {
        for (String idProps: new String[]{"null", "{}"}) {
            try {
                testCall(db, "CALL apoc.merge.node(['Person']," + idProps +", {name:'John'}) YIELD node RETURN node",
                        row -> assertTrue(row.get("node") instanceof Node));
                fail();
            } catch (QueryExecutionException e) {
                assertTrue(e.getMessage().contains("you need to supply at least one identifying property for a merge"));
            }
        }
    }
}
