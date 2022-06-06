import pandas
import sklearn

df = pandas.read_csv('foo.csv')

df = df.drop(['target'])
from sklearn import svm
clf = svm.SVC(gamma=0.001, C=100.)
clf.fit(df, df['target'])
