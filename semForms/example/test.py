import pandas as pd

def read_df():
  return pd.read_csv('houses.csv')

def manipulate_df(houses_df):
    houses_df['beds_to_total'] = houses_df['total_bedrooms'] / houses_df['total_rooms']
    houses_df['popdf'] = houses_df['population' ] / houses_df['households']

def main():
    h_df = read_df()
    manipulate_df(h_df)

main()


