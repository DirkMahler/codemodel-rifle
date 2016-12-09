package hu.bme.mit.codemodel.rifle.resources;

import org.neo4j.graphdb.RelationshipType;

public enum JavaScriptRelationshipType implements RelationshipType{

    contains,
    last,
    _end;

}
