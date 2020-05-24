
Knowledge graphs have proven to be extremely useful in powering diverse applications in semantic search and natural language understanding. [Graph4code](https://arxiv.org/abs/2002.09440) is an effort to build a knowledge graph about program code to similarly power diverse applications such as program search, code understanding, refactoring, bug detection, and code automation.  The graph uses generic techniques to capture the semantics of Python code. The key nodes in the graph are classes, functions and methods in popular Python modules.  Edges indicate *function usage* (e.g., how data flows through function calls, as derived from program analysis of real code), and *documentation* about functions (e.g., code documentation, usage documentation, or forum discussions such as StackOverflow).  We make extensive use of named graphs in RDF to make the knowledge graph extensible by the community. We describe a set of generic extraction techniques that we applied to over 1.3M Python files drawn from GitHub, over 2,300 Python modules, as well as 47M forum posts to generate a graph with almost 2 billion triples. We also provided a number of initial use cases of the knowledge graph in code assistance, enforcing best practices, debugging and type inference. The graph and all its artifacts are available to the larger community for use. 


### Table of Contents
1. [How Graph4Code is created?](#pipeline)
2. [Graph4Code Schema?](#schema)
3. [Download Graph4Code](#datasets)
3. [Example Use Cases](./use_cases.md#uses)
    * [Recommendation engine for developers](./use_cases.md#case1)
    * [Enforcing best practices](./use_cases.md#case2)  
    * [Learning from big code](./use_cases.md#case3) 
4. [Publications](#papers)



### How Graph4Code is created?<a name="pipeline"></a>

<!---![](figures//graph4code_pipeline2.png)-->
<p align="center">
<img align="center" src="figures//graph4code_pipeline2.png" width="90%"/>
</p>
<br><br>

### Graph4Code Schema<a name="schema"></a>

<p align="center">
<img align="center" src="figures/graph4code-relationships-v2.png" width="90%"/>
</p>
<br><br>

### Download Graph4Code<a name="datasets"></a>
* Download the datasets as nquads [here](https://archive.org/download/graph4codev1).


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

