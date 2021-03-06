package hu.bme.mit.codemodel.rifle.resources.utils;

import hu.bme.mit.codemodel.rifle.utils.DbServices;
import org.neo4j.graphdb.*;
import org.neo4j.walk.Visitor;
import org.neo4j.walk.Walker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by steindani on 7/5/16.
 */
// based on org.neo4j.walk.Walker.crosscut()
public class SubgraphWalker extends Walker {

    private final List<Node> nodes = new ArrayList<>();
    private final boolean simple;
    private final boolean cfg;

    public SubgraphWalker(DbServices dbServices, long rootId, boolean simple, boolean cfg) {
        this.simple = simple;
        this.cfg = cfg;

        final Node root = dbServices.graphDb.getNodeById(rootId);
        nodes.add(root);

        final Result result = dbServices.graphDb.execute(
                "MATCH (root)-[*]->(n) WHERE id(root) = {rootid} RETURN id(n) as id",
                new HashMap<String, Object>() {{
                    put("rootid", rootId);
                }});

        while (result.hasNext()) {
            final Map<String, Object> next = result.next();
            nodes.add(dbServices.graphDb.getNodeById(Long.valueOf(next.get("id").toString())));
        }
//            nodes = dbServices.graphDb
//                    .traversalDescription()
//                    .breadthFirst()
//                    .traverse(root)
//                    .nodes()
//                    .stream().map(node -> node).collect(Collectors.toList());
    }

    @Override
    public <R, E extends Throwable> R accept(Visitor<R, E> visitor) throws E {
        //filternodes:
        for (Node node : nodes) {

            if (simple) {
                if (node.hasLabel(DynamicLabel.label("CompilationUnit"))) {
                    continue; // filternodes;
                }
                if (node.hasLabel(DynamicLabel.label("SourceSpan"))) {
                    continue; // filternodes;
                }
                if (node.hasLabel(DynamicLabel.label("SourceLocation"))) {
                    continue; // filternodes;
                }
            }

            if (!cfg) {
                if (node.hasLabel(DynamicLabel.label("End"))) {
                    continue; // filternodes;
                }
            }

            visitor.visitNode(node);
            for (Relationship relationship : node.getRelationships(Direction.OUTGOING)) {
                if (nodes.contains(relationship.getOtherNode(node))) {
                    if (relationship.isType(DynamicRelationshipType.withName("location"))) {
                        continue;
                    }

                    if (!cfg) {
                        if (relationship.isType(DynamicRelationshipType.withName("_end"))) {
                            continue;
                        }
                        if (relationship.isType(DynamicRelationshipType.withName("_next"))) {
                            continue;
                        }
                        if (relationship.isType(DynamicRelationshipType.withName("_true"))) {
                            continue;
                        }
                        if (relationship.isType(DynamicRelationshipType.withName("_false"))) {
                            continue;
                        }
                        if (relationship.isType(DynamicRelationshipType.withName("_normal"))) {
                            continue;
                        }
                    }

                    visitor.visitRelationship(relationship);
                }
            }
        }
        return visitor.done();
    }
}
