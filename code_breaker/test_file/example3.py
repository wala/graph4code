
import pandas as pd
from sklearn.linear_model import LinearRegression
from sklearn.preprocessing import PolynomialFeatures
from sklearn.pipeline import Pipeline


df = pd.read_csv(
    'https://raw.githubusercontent.com/microsoft/Bing-COVID-19-Data/master/data/Bing-COVID19-Data.csv')
dataset = df.rename(columns={
    "Updated": "date",
    "Country_Region": "country",
    "Confirmed": "cases",
    "ConfirmedChange": "change_cases",
    "Deaths": "deaths",
    "DeathsChange": "change_deaths",
    "Recovered": "recovered",
    "RecoveredChange": "change_recovered",
})[["date","country","cases","deaths",
   "recovered","change_cases","change_deaths","change_recovered"]].fillna(0)

dataset['change_cases'] = (dataset['cases']-dataset['cases'].shift())
dataset['change_deaths'] = (dataset['deaths']-dataset['deaths'].shift())
dataset['change_recovered'] = (dataset['recovered']-dataset['recovered'].shift())

# Pipeline for regression
regression_c = Pipeline(steps = [
    ('polinomial',PolynomialFeatures(degree = 3)),
    ('regressor',LinearRegression()),
])

train_size = 0.99
index_split = int(round(train_size*len(dataset)))

X_trn  = dataset.iloc[:index_split]
yr_trn = dataset['change_recovered'].iloc[:index_split]
regression_c.fit(X_trn,yr_trn)
