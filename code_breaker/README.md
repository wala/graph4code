The script `code_knowledge_graph/scripts/setup.sh` has the steps to build the analysis code for both Python 2 and Python 3.  In the parent directory of the repository, run `bash code_knowledge_graph/scripts/setup.sh` to build the code.

To run the code on a given python file:

 - For Python 3: in code_breaker_py3: `java -DquadFile=<nq file name> -DoutputDir=<dir for json files> -cp target/CodeBreaker_py3-0.0.1-SNAPSHOT.jar util.RunTurtleSingleAnalysis <dir containing files/single file> <repoPath> <path>` for running the analysis on a given python file.

 - For Python 2: In code_breaker_py2: `java -DquadFile=<nq file name> -DoutputDir=<dir for json files> -cp target/CodeBreaker_py2-0.0.1-SNAPSHOT.jar util.RunTurtleSingleAnalysis <dir containing files/single file> <repoPath> <path>` for running the analysis on a given python file.

To run summaries for data science pipelines:
In code_breaker_py3: `java -cp target/CodeBreaker_py3-0.0.1-SNAPSHOT.jar util.SummarizeDataScienceGraphsFromJSON <input JSON file from analysis> <output JSON file to store subgraphs>` for running the analysis on a given python file.
