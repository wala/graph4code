# Graph4Code
Knowledge graphs have proven extremely useful in powering diverse applications in semantic search and natural language understanding. Graph4Code is a knowledge graph about program code that can similarly power diverse applications such as program search, code understanding, refactoring, bug detection, and code automation. The graph uses generic techniques to capture the semantics of Python code: the key nodes in the graph are classes, functions and methods in popular Python modules. Edges indicate function usage (e.g., how data flows through function calls, as derived from program analysis of real code), and documentation about functions (e.g., code documentation, usage documentation, or forum discussions such as StackOverflow). We make extensive use of named graphs in RDF to make the knowledge graph extensible by the community. We describe a set of generic extraction techniques that we applied to over 1.3M Python files drawn from GitHub, over 2,300 Python modules, as well as 47M forum posts to generate a graph with over 2 billion triples. We also provide a number of initial use cases of the knowledge graph in code assistance, enforcing best practices, debugging and type inference. The graph and all its artifacts are available to the community for use.



# Deploying and using Graph4Code

The graph files are available [here](https://archive.org/download/graph4codev1). 

To load and query this data, please follow the instructions here: https://github.com/wala/graph4code/blob/master/docs/load_graph.md. We also provide scripts for creating a docker image with the graph database ready to use. 


# Processing new scripts

If you have a directory tree of Python scripts (i.e. .py files) that you would like to build a graph for, you can create the nquads file for those script by running kaggle_proc.sh in the scripts directory.

The usage is `bash kaggle_proc.sh <scripts dir> <out dir>`


# Questions
For any question, please contact us via email: ibrahim.abdelaziz1@ibm.com, kavitha.srinivas@ibm.com, dolby@us.ibm.com
