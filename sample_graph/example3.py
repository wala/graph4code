import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn import svm
data = pd.read_csv("../input/indian_liver_patient.csv", low_memory=False)
data = data.where(pd.notnull(data), data.median(), axis='columns')
train, test = train_test_split(data,
                            test_size = 0.3,
                            random_state = 0,
                            stratify = my_df['Dataset'])
train_X = train[train.columns[:len(train.columns)-1]]
test_X = test[test.columns[:len(test.columns)-1]]
train_Y = train['Dataset']
test_Y = test['Dataset']
model = svm.SVC(kernel=i, random_state=0)
model.fit(train_X,train_Y)
prediction = model.predict(test_X)
