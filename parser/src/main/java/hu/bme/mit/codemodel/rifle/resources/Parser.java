package hu.bme.mit.codemodel.rifle.resources;

import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.ParserWithLocation;
import com.shapesecurity.shift.scope.GlobalScope;
import com.shapesecurity.shift.scope.ScopeAnalyzer;

import hu.bme.mit.codemodel.rifle.utils.DbServices;
import hu.bme.mit.codemodel.rifle.utils.GraphIterator;
import org.neo4j.graphdb.Node;

/**
 * JS Parser.
 */
public class Parser {

    private final DbServices dbServices;

    public Parser(DbServices dbServices) {
        this.dbServices = dbServices;
    }

    public Node parseFile(String sessionid, String path, String content) throws JsError {
        ParserWithLocation parser = new ParserWithLocation();
        Module module = parser.parseModule(content);
        GlobalScope scope = ScopeAnalyzer.analyze(module);
        GraphIterator iterator = new GraphIterator(dbServices, path, parser);
        return iterator.iterate(scope, sessionid);
    }
}
