
Knowledge graphs have proven extremely useful in powering diverse applications in semantic search and natural language understanding. Graph4Code is a knowledge graph about program code that can similarly power diverse applications such as program search, code understanding, refactoring, bug detection, and code automation.  The graph uses generic techniques to capture the semantics of Python code: the key nodes in the graph are classes, functions and methods in popular Python modules.  Edges indicate function usage (e.g., how data flows through function calls, as derived from program analysis of real code), and documentation about functions (e.g., code documentation, usage documentation, or forum discussions such as StackOverflow).  We make extensive use of named graphs in RDF to make the knowledge graph extensible by the community.  We describe a set of generic extraction techniques that we applied to over 1.3M Python files drawn from GitHub, over 2,300 Python modules, as well as 47M forum posts to generate a graph with over 2 billion triples. We also provide a number of initial use cases of the knowledge graph in code assistance, enforcing best practices, debugging and type inference. The graph and all its artifacts are available to the community for use. 

Download Graph4Code dataset as nquads [here](https://archive.org/download/graph4codev1).


### Table of Contents
1. [Schema and Query By Example](./query_by_example.md#schema)
2. [How Graph4Code is created?](#pipeline)
3. [Example Use Cases](./use_cases.md#uses)
    * [Recommendation engine for developers](./use_cases.md#case1)
    * [Enforcing best practices](./use_cases.md#case2)  
    * [Learning from big code](./use_cases.md#case3) 
4. [Publications](#papers)

### Schema and Example Queries:

The following shows a concept map of Graph4Code's overall schema, across the code analysis, Stack Exchange, and Docstrings extractions.

<p align="center">
<img align="center" src="figures/graph4code-relationships-v2.png" width="90%"/>
</p>
<br><br>

#### Query Example 1: Get documentation about a function

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

### How Graph4Code is created?<a name="pipeline"></a>

<!---![](figures//graph4code_pipeline2.png)-->
<p align="center">
<img align="center" src="figures//graph4code_pipeline2.png" width="90%"/>
</p>
<br><br>


### Publications<a name="papers"></a>
* If you use [Graph4Code](https://arxiv.org/abs/2002.09440) in your research, please cite our paper:

 ```
 @article{srinivas2020graph4code,
  title={Graph4Code: A Machine Interpretable Knowledge Graph for Code},
  author={Abdelaziz, Ibrahim and Dolby, Julian and  McCusker, James P and Srinivas, Kavitha},
  journal={arXiv preprint arXiv:2002.09440},
  year={2020}
}
```

