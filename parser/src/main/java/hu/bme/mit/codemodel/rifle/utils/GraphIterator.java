package hu.bme.mit.codemodel.rifle.utils;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.*;
import com.shapesecurity.shift.ast.SourceSpan;
import com.shapesecurity.shift.parser.ParserWithLocation;
import com.shapesecurity.shift.scope.Scope;
import hu.bme.mit.codemodel.rifle.resources.JavaScriptLabel;
import hu.bme.mit.codemodel.rifle.resources.JavaScriptRelationshipType;
import org.neo4j.graphdb.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by steindani on 2/26/16.
 */
public class GraphIterator {

    protected final String path;
    protected final ParserWithLocation parserWithLocation;
    protected final DbServices dbServices;
    protected final IdentityHashMap<Object, Object> done = new IdentityHashMap<>();
    protected final Map<Object, org.neo4j.graphdb.Node> nodes = new IdentityHashMap<>();

    protected final BlockingQueue<QueueItem> queue = new LinkedBlockingQueue<>();

    public GraphIterator(DbServices dbServices, String path, ParserWithLocation parserWithLocation) {
        this.dbServices = dbServices;
        this.path = path;
        this.parserWithLocation = parserWithLocation;
    }

    public Node iterate(Scope scope, String sessionId) {
        Node pathNode = null;
        try (Transaction tx = dbServices.beginTx()) {

            pathNode = createPathNode(sessionId);
            queue.add(new QueueItem(null, null, scope));

            while (!queue.isEmpty()) {
                QueueItem queueItem = queue.take();
                process(queueItem, sessionId);
            }

            tx.success();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return pathNode;
    }

    protected Node createPathNode(String sessionId) {
        Node compilationUnit = storeType(path, JavaScriptLabel.CompilationUnit);
        storeProperty(path, "path", path);
        storeProperty(path, "session", sessionId);
        return compilationUnit;
    }

    protected void process(QueueItem queueItem, String sessionId) {
        Object parent = queueItem.parent;
        String predicate = queueItem.predicate;
        Object node = queueItem.node;

        // TODO for presentation reasons the Nil node is hidden
        if (node == null || node instanceof Nil) {
            return;
        }

        if (isPrimitive(node.getClass())) {
            storeProperty(parent, predicate, node);
        } else if (isCollection(node)) {
            handleCollection(parent, predicate, node, sessionId);
        } else if (node instanceof Maybe || node instanceof Either) {
            handleFunctional(parent, predicate, node);
        } else if (node.getClass().getName().startsWith("com.shapesecurity")) {
            handleAstNode(parent, predicate, node, sessionId);
        } else {
            System.err.println("WTF");
        }

    }

    protected static boolean isCollection(Object node) {
        return node instanceof ImmutableList
                || node instanceof Map
                || node instanceof HashTable
                || node instanceof ConcatList;
    }

    private void handleFunctional(Object parent, String predicate, Object node) {
        if (done.containsKey(node)) {
            return;
        }
        done.put(node, node);

        if (node instanceof Maybe) {
            Maybe el = (Maybe) node;
            if (el.isJust()) {
                queue.add(new QueueItem(parent, predicate, el.just()));
//            } else {
//                queue.add(new QueueItem(node, fieldName, "null"));
            }
        } else if (node instanceof Either) {
            Either el = (Either) node;
            if (el.isLeft()) {
                handleFunctional(parent, predicate, el.left());
            }
            if (el.isRight()) {
                handleFunctional(parent, predicate, el.right());
            }
        }
    }

    /**
     * If the sessionId is set, mark the node temporal and store the id.
     * Since we are only iterating one AST, there is no way a Node from another graph is mislabeled.
     *
     * @param sessionId
     * @param node
     */
    protected void handleIfInSession(String sessionId, Object node) {
        if (sessionId != null) {
            storeType(node, JavaScriptLabel.Temp);
            storeProperty(node, "session", sessionId);
        }
    }

    protected void handleCollection(Object parent, String predicate, Object node, String sessionId) {
        if (done.containsKey(node)) {
            return;
        }
        done.put(node, node);

        if (node instanceof ImmutableList || node instanceof ConcatList) {

            Iterable list = (Iterable) node;

            // id -- [field] -> list
            storeReference(parent, predicate, list);
            storeType(list, JavaScriptLabel.List);
            storeReference(path, JavaScriptRelationshipType.contains, list);
            handleIfInSession(sessionId, list);

            final Iterator iterator = list.iterator();
            int i = 0;

            Object prev = null;

            while (iterator.hasNext()) {
                Object el = iterator.next();
                queue.add(new QueueItem(list, Integer.toString(i), el));

                if (prev != null) {
                    queue.add(new QueueItem(prev, "_next", el));
                }
                prev = el;

                // connect the children directly
                queue.add(new QueueItem(parent, predicate, el));

                i++;
            }

            storeReference(list, JavaScriptRelationshipType.last, prev);
            createEndNode(list, sessionId);

        } else if (node instanceof Map) {

            Map map = (Map) node;

            if (!map.isEmpty()) {
                // id -- [field] -> table
                storeReference(parent, predicate, map);
//                storeType(tx, map, node.getClass().getSimpleName());
                storeType(map, JavaScriptLabel.Map);
                storeReference(path, JavaScriptRelationshipType.contains, map);
                handleIfInSession(sessionId, map);

                for (Object el : map.entrySet()) {
                    Map.Entry entry = (Map.Entry) el;
                    queue.add(new QueueItem(map, entry.getKey().toString(), entry.getValue()));
                }
            }

        } else if (node instanceof HashTable) {

            HashTable table = (HashTable) node;

            if (table.length > 0) {
                // id -- [field] -> table
                storeReference(parent, predicate, table);
                storeType(table, JavaScriptLabel.HashTable);
                storeReference(path, JavaScriptRelationshipType.contains, table);
                handleIfInSession(sessionId, table);

                for (Object el : table.entries()) {
                    Pair pair = (Pair) el;
                    queue.add(new QueueItem(table, pair.a.toString(), pair.b));
                }
            }

        }
    }

    protected void handleAstNode(Object parent, String predicate, Object node, String sessionId) {

        if (parent != null) {
            storeReference(parent, predicate, node);
        }

        if (done.containsKey(node)) {
            return;
        }
        done.put(node, node);

        createEndNode(node, sessionId);

        storeReference(path, JavaScriptRelationshipType.contains, node);
        handleIfInSession(sessionId, node);
        storeLocation(node);

        Class<?> nodeType = node.getClass();

        storeType(node, nodeType.getSimpleName());
        // list superclasses, interfaces
        List<Class<?>> interfaces = Arrays.asList(nodeType.getInterfaces());
        interfaces.forEach(elem -> {
            final String interfaceName = elem.getSimpleName();
            storeType(node, interfaceName);
            
            if (interfaceName.startsWith("Literal")) {
                storeType(node, JavaScriptLabel.Literal);
            }
        });

        Class<?> superclass = nodeType.getSuperclass();
        while (superclass != Object.class) {
            storeType(node, superclass.getSimpleName());
            superclass = superclass.getSuperclass();
        }


        getAllFields(nodeType).forEach(
                field -> {
                    field.setAccessible(true);

                    Class<?> fieldType = field.getType();
                    String fieldName = field.getName();


                    try {
                        Object o = field.get(node);
                        if (fieldType.isEnum()) {

                            // queue.add(new QueueItem(node, fieldName, o.toString()));
                            storeProperty(node, fieldName, o.toString());

                        } else if (o instanceof Node) {

                            queue.add(new QueueItem(node, fieldName, o));

                        } else if (fieldType.getName().startsWith("com.shapesecurity.functional")) {

                            // TODO
                            queue.add(new QueueItem(node, fieldName, o));

                        } else {

                            // TODO
                            queue.add(new QueueItem(node, fieldName, o));

                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
        );
    }

    protected void createEndNode(Object node, String sessionId) {
        Object end = new Object();
        storeType(end, JavaScriptLabel.End);
        storeReference(node, JavaScriptRelationshipType._end, end);
        storeProperty(node, "session", sessionId);
        storeReference(path, JavaScriptRelationshipType.contains, end);
    }

    protected void storeLocation(Object node) {
        if (node instanceof com.shapesecurity.shift.ast.Node) {
            Maybe<SourceSpan> location = parserWithLocation.getLocation((com.shapesecurity.shift.ast.Node) node);
            if (location.isJust()) {
                queue.add(new QueueItem(node, "location", location.just()));
            }
        }
    }

    // http://stackoverflow.com/questions/209366/how-can-i-generically-tell-if-a-java-class-is-a-primitive-type
    public static boolean isPrimitive(Class c) {
        if (c.isPrimitive()) {
            return true;
        } else if (c == Byte.class
                || c == Short.class
                || c == Integer.class
                || c == Long.class
                || c == Float.class
                || c == Double.class
                || c == Boolean.class
                || c == Character.class) {
            return true;
        } else if (c == String.class) {
            return true;
        } else {
            return false;
        }
    }

    protected Node findOrCreate(Object subject) {
        if (nodes.containsKey(subject)) {
            return nodes.get(subject);
        } else {
            Node node = dbServices.createNode();
            nodes.put(subject, node);
            return node;
        }
    }

    public void storeReference(Object subject, String predicate, Object object) {
        storeReference(subject, dbServices.getRelationshipType(predicate), object);
    }

    public void storeReference(Object subject, RelationshipType predicate, Object object) {
        if (subject == null || object == null) {
            return;
        }
        Node a = findOrCreate(subject);
        Node b = findOrCreate(object);
        a.createRelationshipTo(b, predicate);
    }

    public void storeProperty(Object subject, String predicate, Object object) {
        if (object == null) {
            return;
        }
        Node node = findOrCreate(subject);
        node.setProperty(predicate, object);
    }

    public Node storeType(Object subject, String type) {
        if (type == null || type.length() == 0) {
            return null;
        }
        return storeType(subject, dbServices.getLabel(type));
    }

    public Node storeType(Object subject, Label type) {
        Node node = findOrCreate(subject);
        node.addLabel(type);
        return node;
    }

    // http://stackoverflow.com/questions/3567372/access-to-private-inherited-fields-via-reflection-in-java
    protected List<Field> getAllFields(Class clazz) {
        List<Field> fields = new ArrayList<Field>();

        fields.addAll(Arrays.asList(clazz.getDeclaredFields()));

        Class superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            fields.addAll(getAllFields(superClazz));
        }

        return fields;
    }


    protected class QueueItem {
        public Object parent;
        public String predicate;
        public Object node;

        public QueueItem(Object parent, String predicate, Object node) {
            this.parent = parent;
            this.predicate = predicate;
            this.node = node;
        }
    }
}
