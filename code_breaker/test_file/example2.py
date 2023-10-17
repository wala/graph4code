import pandas as pd

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
