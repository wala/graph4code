PREFIX sio: <http://semanticscience.org/resource/>
PREFIX graph4code: <http://purl.org/twc/graph4code/>
PREFIX graph4codeOntology: <http://purl.org/twc/graph4code/ontology/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX schema: <http://schema.org/>
PREFIX dc: <http://purl.org/dc/terms/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

select ?label ?method ?cls where {
  graph ?g {
    ?turtle rdf:type sio:SIO_000667 .
    ?turtle rdfs:label ?label .
    ?turtle schema:text ?text .

        ?turtle graph4code:flowsTo/graph4code:flowsTo ?called .
        ?turtle graph4code:flowsTo/sio:SIO_000230 ?anon .
        ?anon sio:SIO_000613  "0"^^xsd:int .
        ?anon prov:isSpecializationOf ?called .
        ?called schema:about ?method .

    graph graph4code:docstrings {
      ?s graph4codeOntology:name_end ?p2 ;
         dc:isPartOf ?cls .
    }

  }
}