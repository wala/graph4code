package com.ibm.wala.cast.lsp.codeBreaker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.tdb.TDBFactory;

import com.ibm.wala.util.collections.Pair;

public class CreateSkLearnModelParamHistogram {

    String[] sklearnModels = new String[]{"ARDRegression.bayes.linear_model.sklearn",
            "AdaBoostClassifier.weight_boosting.ensemble.sklearn",
            "AdaBoostRegressor.weight_boosting.ensemble.sklearn",
            "AdditiveChi2Sampler.kernel_approximation.sklearn",
            "AffinityPropagation.affinity_propagation_.cluster.sklearn",
            "AgglomerativeClustering.hierarchical.cluster.sklearn",
            "BaggingClassifier.bagging.ensemble.sklearn",
            "BaggingRegressor.bagging.ensemble.sklearn",
            "BayesianGaussianMixture.bayesian_mixture.mixture.sklearn",
            "BayesianRidge.bayes.linear_model.sklearn",
            "BernoulliNB.naive_bayes.sklearn",
            "BernoulliRBM.rbm.neural_network.sklearn",
            "Binarizer.data.preprocessing.sklearn",
            "Birch.birch.cluster.sklearn",
            "CCA.cca_.cross_decomposition.sklearn",
            "CalibratedClassifierCV.calibration.sklearn",
            "CheckingClassifier.mocking.utils.sklearn",
            "ClassifierChain.multioutput.sklearn",
            "ColumnTransformer._column_transformer.compose.sklearn",
            "ComplementNB.naive_bayes.sklearn",
            "CountVectorizer.text.feature_extraction.sklearn",
            "DBSCAN.dbscan_.cluster.sklearn",
            "DecisionTreeClassifier.tree.tree.sklearn",
            "DecisionTreeRegressor.tree.tree.sklearn",
            "DictVectorizer.dict_vectorizer.feature_extraction.sklearn",
            "DictionaryLearning.dict_learning.decomposition.sklearn",
            "DummyClassifier.dummy.sklearn",
            "DummyRegressor.dummy.sklearn",
            "ElasticNet.coordinate_descent.linear_model.sklearn",
            "ElasticNetCV.coordinate_descent.linear_model.sklearn",
            "EllipticEnvelope.elliptic_envelope.covariance.sklearn",
            "EmpiricalCovariance.empirical_covariance_.covariance.sklearn",
            "ExtraTreeClassifier.tree.tree.sklearn",
            "ExtraTreeRegressor.tree.tree.sklearn",
            "ExtraTreesClassifier.forest.ensemble.sklearn",
            "ExtraTreesRegressor.forest.ensemble.sklearn",
            "FactorAnalysis.factor_analysis.decomposition.sklearn",
            "FastICA.fastica_.decomposition.sklearn",
            "FeatureAgglomeration.hierarchical.cluster.sklearn",
            "FeatureHasher.hashing.feature_extraction.sklearn",
            "FeatureUnion.pipeline.sklearn",
            "FunctionTransformer._function_transformer.preprocessing.sklearn",
            "GaussianMixture.gaussian_mixture.mixture.sklearn",
            "GaussianNB.naive_bayes.sklearn",
            "GaussianProcessClassifier.gpc.gaussian_process.sklearn",
            "GaussianProcessRegressor.gpr.gaussian_process.sklearn",
            "GaussianRandomProjection.random_projection.sklearn",
            "GaussianRandomProjectionHash.approximate.neighbors.sklearn",
            "GenericUnivariateSelect.univariate_selection.feature_selection.sklearn",
            "GradientBoostingClassifier.gradient_boosting.ensemble.sklearn",
            "GradientBoostingRegressor.gradient_boosting.ensemble.sklearn",
            "GraphLasso.graph_lasso_.covariance.sklearn",
            "GraphLassoCV.graph_lasso_.covariance.sklearn",
            "GraphicalLasso.graph_lasso_.covariance.sklearn",
            "GraphicalLassoCV.graph_lasso_.covariance.sklearn",
            "GridSearchCV._search.model_selection.sklearn",
            "HashingVectorizer.text.feature_extraction.sklearn",
            "HuberRegressor.huber.linear_model.sklearn",
            "Imputer.imputation.preprocessing.sklearn",
            "IncrementalPCA.incremental_pca.decomposition.sklearn",
            "IsolationForest.iforest.ensemble.sklearn",
            "Isomap.isomap.manifold.sklearn",
            "IsotonicRegression.isotonic.sklearn",
            "KBinsDiscretizer._discretization.preprocessing.sklearn",
            "KMeans.k_means_.cluster.sklearn",
            "KNeighborsClassifier.classification.neighbors.sklearn",
            "KNeighborsRegressor.regression.neighbors.sklearn",
            "KernelCenterer.data.preprocessing.sklearn",
            "KernelDensity.kde.neighbors.sklearn",
            "KernelPCA.kernel_pca.decomposition.sklearn",
            "KernelRidge.kernel_ridge.sklearn",
            "LSHForest.approximate.neighbors.sklearn",
            "LabelBinarizer.label.preprocessing.sklearn",
            "LabelEncoder.label.preprocessing.sklearn",
            "LabelPropagation.label_propagation.semi_supervised.sklearn",
            "LabelSpreading.label_propagation.semi_supervised.sklearn",
            "Lars.least_angle.linear_model.sklearn",
            "LarsCV.least_angle.linear_model.sklearn",
            "Lasso.coordinate_descent.linear_model.sklearn",
            "LassoCV.coordinate_descent.linear_model.sklearn",
            "LassoLars.least_angle.linear_model.sklearn",
            "LassoLarsCV.least_angle.linear_model.sklearn",
            "LassoLarsIC.least_angle.linear_model.sklearn",
            "LatentDirichletAllocation.online_lda.decomposition.sklearn",
            "LedoitWolf.shrunk_covariance_.covariance.sklearn",
            "LinearDiscriminantAnalysis.discriminant_analysis.sklearn",
            "LinearRegression.base.linear_model.sklearn",
            "LinearSVC.classes.svm.sklearn",
            "LinearSVR.classes.svm.sklearn",
            "LocalOutlierFactor.lof.neighbors.sklearn",
            "LocallyLinearEmbedding.locally_linear.manifold.sklearn",
            "LogisticRegression.logistic.linear_model.sklearn",
            "LogisticRegressionCV.logistic.linear_model.sklearn",
            "MDS.mds.manifold.sklearn",
            "MLPClassifier.multilayer_perceptron.neural_network.sklearn",
            "MLPRegressor.multilayer_perceptron.neural_network.sklearn",
            "MaxAbsScaler.data.preprocessing.sklearn",
            "MeanShift.mean_shift_.cluster.sklearn",
            "MinCovDet.robust_covariance.covariance.sklearn",
            "MinMaxScaler.data.preprocessing.sklearn",
            "MiniBatchDictionaryLearning.dict_learning.decomposition.sklearn",
            "MiniBatchKMeans.k_means_.cluster.sklearn",
            "MiniBatchSparsePCA.sparse_pca.decomposition.sklearn",
            "MissingIndicator.impute.sklearn",
            "MultiLabelBinarizer.label.preprocessing.sklearn",
            "MultiOutputClassifier.multioutput.sklearn",
            "MultiOutputRegressor.multioutput.sklearn",
            "MultiTaskElasticNet.coordinate_descent.linear_model.sklearn",
            "MultiTaskElasticNetCV.coordinate_descent.linear_model.sklearn",
            "MultiTaskLasso.coordinate_descent.linear_model.sklearn",
            "MultiTaskLassoCV.coordinate_descent.linear_model.sklearn",
            "MultinomialNB.naive_bayes.sklearn",
            "NMF.nmf.decomposition.sklearn",
            "NearestCentroid.nearest_centroid.neighbors.sklearn",
            "NearestNeighbors.unsupervised.neighbors.sklearn",
            "Normalizer.data.preprocessing.sklearn",
            "NuSVC.classes.svm.sklearn",
            "NuSVR.classes.svm.sklearn",
            "Nystroem.kernel_approximation.sklearn",
            "OAS.shrunk_covariance_.covariance.sklearn",
            "OneClassSVM.classes.svm.sklearn",
            "OneHotEncoder._encoders.preprocessing.sklearn",
            "OneVsOneClassifier.multiclass.sklearn",
            "OneVsRestClassifier.multiclass.sklearn",
            "OrdinalEncoder._encoders.preprocessing.sklearn",
            "OrthogonalMatchingPursuit.omp.linear_model.sklearn",
            "OrthogonalMatchingPursuitCV.omp.linear_model.sklearn",
            "OutputCodeClassifier.multiclass.sklearn",
            "PCA.pca.decomposition.sklearn",
            "PLSCanonical.pls_.cross_decomposition.sklearn",
            "PLSRegression.pls_.cross_decomposition.sklearn",
            "PLSSVD.pls_.cross_decomposition.sklearn",
            "PassiveAggressiveClassifier.passive_aggressive.linear_model.sklearn",
            "PassiveAggressiveRegressor.passive_aggressive.linear_model.sklearn",
            "PatchExtractor.image.feature_extraction.sklearn",
            "Perceptron.perceptron.linear_model.sklearn",
            "Pipeline.pipeline.sklearn",
            "PolynomialFeatures.data.preprocessing.sklearn",
            "PowerTransformer.data.preprocessing.sklearn",
            "QuadraticDiscriminantAnalysis.discriminant_analysis.sklearn",
            "QuantileTransformer.data.preprocessing.sklearn",
            "RANSACRegressor.ransac.linear_model.sklearn",
            "RBFSampler.kernel_approximation.sklearn",
            "RFE.rfe.feature_selection.sklearn",
            "RFECV.rfe.feature_selection.sklearn",
            "RadiusNeighborsClassifier.classification.neighbors.sklearn",
            "RadiusNeighborsRegressor.regression.neighbors.sklearn",
            "RandomForestClassifier.forest.ensemble.sklearn",
            "RandomForestRegressor.forest.ensemble.sklearn",
            "RandomTreesEmbedding.forest.ensemble.sklearn",
            "RandomizedLasso.randomized_l1.linear_model.sklearn",
            "RandomizedLogisticRegression.randomized_l1.linear_model.sklearn",
            "RandomizedSearchCV._search.model_selection.sklearn",
            "RegressorChain.multioutput.sklearn",
            "Ridge.ridge.linear_model.sklearn",
            "RidgeCV.ridge.linear_model.sklearn",
            "RidgeClassifier.ridge.linear_model.sklearn",
            "RidgeClassifierCV.ridge.linear_model.sklearn",
            "RobustScaler.data.preprocessing.sklearn",
            "SGDClassifier.stochastic_gradient.linear_model.sklearn",
            "SGDRegressor.stochastic_gradient.linear_model.sklearn",
            "SVC.classes.svm.sklearn",
            "SVR.classes.svm.sklearn",
            "SelectFdr.univariate_selection.feature_selection.sklearn",
            "SelectFpr.univariate_selection.feature_selection.sklearn",
            "SelectFromModel.from_model.feature_selection.sklearn",
            "SelectFwe.univariate_selection.feature_selection.sklearn",
            "SelectKBest.univariate_selection.feature_selection.sklearn",
            "SelectPercentile.univariate_selection.feature_selection.sklearn",
            "ShrunkCovariance.shrunk_covariance_.covariance.sklearn",
            "SimpleImputer.impute.sklearn",
            "SkewedChi2Sampler.kernel_approximation.sklearn",
            "SparseCoder.dict_learning.decomposition.sklearn",
            "SparsePCA.sparse_pca.decomposition.sklearn",
            "SparseRandomProjection.random_projection.sklearn",
            "SpectralBiclustering.bicluster.cluster.sklearn",
            "SpectralClustering.spectral.cluster.sklearn",
            "SpectralCoclustering.bicluster.cluster.sklearn",
            "SpectralEmbedding.spectral_embedding_.manifold.sklearn",
            "StandardScaler.data.preprocessing.sklearn",
            "TSNE.t_sne.manifold.sklearn",
            "TfidfTransformer.text.feature_extraction.sklearn",
            "TfidfVectorizer.text.feature_extraction.sklearn",
            "TheilSenRegressor.theil_sen.linear_model.sklearn",
            "TransformedTargetRegressor._target.compose.sklearn",
            "TruncatedSVD.truncated_svd.decomposition.sklearn",
            "VarianceThreshold.variance_threshold.feature_selection.sklearn",
            "VotingClassifier.voting_classifier.ensemble.sklearn",
            "_BaseEncoder._encoders.preprocessing.sklearn",
            "_BaseRidgeCV.ridge.linear_model.sklearn",
            "_BinaryGaussianProcessClassifierLaplace.gpc.gaussian_process.sklearn",
            "_ConstantPredictor.multiclass.sklearn",
            "_RidgeGCV.ridge.linear_model.sklearn",
            "_SigmoidCalibration.calibration.sklearn"
    };

    private String storeLocation = System.getProperty("RDF_STORE_LOCATION", "../data/static_analysis_data");
    private String standardQueryLocation = System.getProperty("STATIC_QUERY_LOCATION", "../static_analysis_queries/query_constants_parameterized.sparql");
    protected Dataset dataset;
    private RDFConnection connection;
    protected String standardQuery;


    public CreateSkLearnModelParamHistogram() {
        this.dataset = TDBFactory.createDataset(storeLocation);
        this.connection = RDFConnectionFactory.connect(dataset);
        try {
            standardQuery = new String(Files.readAllBytes(Paths.get(standardQueryLocation)));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Number getNumber(String value) {
        Number ret = null;
        try {
            ret = Integer.parseInt(value);
        } catch(NumberFormatException e) {
            try {
                ret = Double.parseDouble(value);
            } catch (NumberFormatException n) {
            }
        }
        return ret;
    }

    public Map<Pair<String, String>, List<Object>> getSortedResults() {
        // System.err.println(this.standardQuery.toString());
        Map<Pair<String, String>, List<Object>> results = new HashMap<>();
        for (String s : sklearnModels) {
            StringTokenizer tokenizer = new StringTokenizer(s, ".");
            // TBD remove after the fix to the paths in WALA
            String clazz = tokenizer.nextToken();
            String str = this.standardQuery.replaceAll("FIXME", clazz);
            System.out.println(str);
            Query query = QueryFactory.create(str);
            ResultSet resultset = connection.query(query).execSelect();
            while (resultset.hasNext()) {
                QuerySolution solution = resultset.nextSolution();
                String model = solution.get("p").asLiteral().getString();
                String parameter = solution.get("z").asLiteral().getString();
                Pair<String, String> key = Pair.make(model, parameter);
                if (!results.containsKey(key)) {
                    results.put(key, new LinkedList<>());
                }
                String value = solution.get("v").asLiteral().getString();
                System.out.println(key + " :" + value);
                // try to parse stuff as a number
                Number num = getNumber(value);
                List<Object> values = results.get(key);

                if (num != null) {
                    values.add(num);
                } else {
                    values.add(value);
                }
            }
        }
        return results;
    }

    public static void main(String[] args) {
        CreateSkLearnModelParamHistogram store = new CreateSkLearnModelParamHistogram();
        Map<Pair<String, String>, List<Object>>  results = store.getSortedResults();
        for (Pair<String, String> key : results.keySet()) {
            System.out.println("Key:" + key);
            System.out.println(results.get(key));
            System.out.println("Length:" + results.get(key).size());

        }
        System.out.println("number of keys:" + results.keySet().size());
    }

}
