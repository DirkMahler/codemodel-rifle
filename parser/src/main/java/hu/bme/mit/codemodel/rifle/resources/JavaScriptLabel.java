package hu.bme.mit.codemodel.rifle.resources;

import org.neo4j.graphdb.Label;

public enum JavaScriptLabel implements Label {
    Temp, List, Map, HashTable, Literal, End, CompilationUnit
}
