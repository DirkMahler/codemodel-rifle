package hu.bme.mit.codemodel.rifle.jqassistant.scanner;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.ScannerPlugin.Requires;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import com.buschmais.xo.api.CompositeObject;
import com.shapesecurity.shift.parser.JsError;

import hu.bme.mit.codemodel.rifle.jqassistant.model.JavaScriptFileDescriptor;
import hu.bme.mit.codemodel.rifle.resources.Parser;
import hu.bme.mit.codemodel.rifle.utils.DbServices;

@Requires(FileDescriptor.class)
public class JavaScriptFileScannerPlugin extends AbstractScannerPlugin<FileResource, JavaScriptFileDescriptor> {

    @Override
    public boolean accepts(FileResource fileResource, String path, Scope scope) throws IOException {
        return path.toLowerCase().endsWith(".js");
    }

    @Override
    public JavaScriptFileDescriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        Store store = scanner.getContext().getStore();
        FileDescriptor fileDescriptor = scanner.getContext().getCurrentDescriptor();
        JavaScriptFileDescriptor jsFileDescriptor = store.addDescriptorType(fileDescriptor, JavaScriptFileDescriptor.class);
        Node node = parse(fileResource, path, store);
        Node jsFileNode = ((CompositeObject) jsFileDescriptor).getDelegate();
        jsFileNode.createRelationshipTo(node, DynamicRelationshipType.withName("CONTAINS_COMPILATION_UNIT"));
        return jsFileDescriptor;
    }

    private Node parse(FileResource fileResource, String path, Store store) throws IOException {
        GraphDatabaseService graphDatabaseService = store.getGraphDatabaseService();
        Parser parser = new Parser(new DbServices(graphDatabaseService));
        Node node;
        try (InputStream stream = fileResource.createStream()) {
            try {
                store.commitTransaction();
                node = parser.parseFile(null, path, IOUtils.toString(stream));
                store.beginTransaction();
            } catch (JsError jsError) {
                throw new IOException("Cannot parse file " + path, jsError);
            }
        }
        return node;
    }
}
