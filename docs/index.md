# Datasets
Download the datasets as nquads here [here](http://graph4code.whyis.io/download/).

# Uses

* Recommendation engine for developers.  CodeBreaker is a coding assistant built on top of Graph4Code to help daat scientists write code.  The coding assistant helps users find the most plausible next coding step, finds relevant stack overflow posts based purely on the users' code, and allows users to see what sorts of models other people have constructed for data flows similar to their own.  For a detailed description of the use cases, as well as the SPARQL queries used in this case, see [here](http://graph4code.whyis.io/download/CodeAssistanceDemo.pdf).

* Best practice encoding.  Many best practices for API frameworks can be encoded into query templates over data flow and control flow.  Here we give three such examples for data science code, along with queries which can be templatized.

  * Check that users developing data science code use cross-validation to build models
  * Check that users developing data science code create multiple models on the same dataset, since machine learning algorithms vary greatly in terms of performance on different datasets.
  * Check that the users use some sort of libraries for hyper-parameter optimization when building their models.
  * Check that users developing data science code create the model with a different dataset than the ones they use to validate the model.
  
* Automated Code Generation.  Automation does not need to be in terms of generating entire programs.
  * As an example, again, from a data science use case, the arguments flowing into constructors of models govern the behavior of a model to a large extent.   These so-called hyperparameters are often optimized by some sort of search technique over the space of parameters.  Hyperparameter optimization can be seeded with the appropriate values using query here.

