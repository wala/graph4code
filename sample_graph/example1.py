import pandas as pd
import sklearn.model_selection.train_test_split
from sklearn import svm

data = pd.read_csv("../input/indian_liver_patient.csv", low_memory=False)
data.fillna(method="ffill")

X = data[data.columns[:len(data.columns)-1]]
Y = data['Dataset']

train_X, test_X, train_Y, test_Y = train_test_split(X, y, test_size=0.3, random_state=0, stratify=data['Dataset'])

model=svm.SVC(kernel='rbf', random_state=0)
model.fit(train_X, train_Y)

model.predict(test_X, test_Y)
