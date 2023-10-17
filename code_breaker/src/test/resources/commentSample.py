from sklearn.preprocessing import PolynomialFeatures        # import
from sklearn.linear_model import LinearRegression       # import 2
import numpy as np          # baz

poly = PolynomialFeatures(degree = 1, include_bias = False)  # test for comments
lm = LinearRegression()   # Watson do something different here


# In[ ]:

X = np.sort(np.random.rand(20))  # abc
func = lambda x: np.cos(1.5 * np.pi * x) # def
y = np.array([func(x) for x in X]) # ghi

from sklearn.pipeline import Pipeline # jkl

pipeline = Pipeline([("polynomial_features", poly),
                         ("linear_regression", lm)])    # foo
pipeline.fit(X[:, np.newaxis], y)   # bar


X_test = np.linspace(0, 1, 100)     # silly code

y_pred = pipeline.predict(X_test[:, np.newaxis])  # ABC
