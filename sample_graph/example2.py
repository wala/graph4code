import pandas as pd
import sklearn.model_selection.train_test_split
from sklearn.svm import SVC
from sklearn.gaussian_process.kernels import RBF

data = pd.read_csv("../input/chinese_liver_patient.csv", low_memory=False)

train, test  = train_test_split(data, test_size=0.3, random_state=0, stratify=data['Dataset'])

train_X = train[train.columns[:len(train.columns)-1]]
train_Y = train['Dataset']
test_X = test[test.columns[:len(test.columns)-1]]
test_Y = test['Dataset']

clf_svm_radial_basis=SVC(kernel=1.0 * RBF(1.0), random_state=0)
model = clf_svm_radial_basis.fit(train_X, train_Y)

model.predict(test_X, test_Y)
