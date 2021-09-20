##### Query Example 1: Get documentation about a function or class

The first example query returns the documentation of a class or function, in this case *pandas.read\_csv*. It also returns parameter and return types, when known. One can expand these parameters (*?param*) further to get their labels, documentation, inferred types, and check if they are optional.

```
select ?doc ?param ?return where {
   graph <http://purl.org/twc/graph4code/docstrings> {
      ?s  rdfs:label "pandas.read_csv" ;
          skos:definition ?doc .
      optional { ?s g4c:param ?param . }
      optional { ?s g4c:return ?return . }
    }
}
```
##### Query Example 2: Search in forums posts for program code

The query below assumes that the user has a context in the program from which they are launching their search. ?f specifies a list of functions that represent the calling context. 

```
select ?q ?t ?q_content ?a_content ?c where {
   graph <https://stackoverflow.com/questions/> {
     {
        # gather questions that are about the list of functions, counting the number of hits to functions
        # per question.  Here we used values to specify that list as ?f
        select ?q (count(?q) as ?c) {
            values (?f) {
              (python:sklearn.model_selection.train_test_split)
              (python:sklearn.svm.SVC.fit)
            }
           ?q rdf:type  schema:Question;
              schema:about ?f ;

       } group by ?q
     }
        # gather the content and title of the question, its suggested answers and their content
        # ensuring the answer contains some phrase
       ?q schema:suggestedAnswer ?a ;
            sioc:content  ?q_content ;
            schema:name ?t.
       ?a rdf:type schema:Answer ;
            sioc:content ?a_content .
       filter(contains(?a_content, "memory issue"))
   }
} order by desc(?c)
```

##### Query Example 3: Understand how data scientists use functions or classes

Another use of Graph4Code is to understand how people use functions such as *pandas.read\_csv*. In particular, the query below shows when *pandas.read\_csv* is used, what are the *fit* functions that are typically applied on its output. 


```
select distinct ?label where {
   graph ?g {
        ?read rdfs:label "pandas.read_csv" .
        ?fit schema:about "fit" .
        ?read graph4code:flowsTo+ ?fit .
        ?fit rdfs:label ?label .
   }
}
```

More Example queries can be found [here](https://github.com/wala/graph4code/tree/master/usage_queries) 
