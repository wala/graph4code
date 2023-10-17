
import pandas as pd
import numpy as np

from datetime import datetime,timedelta
from scipy.integrate import odeint

# get_ipython().system('pip install xtlearn')
from xtlearn.feature_selection import FeatureSelector
from sklearn.linear_model import LinearRegression
from sklearn.preprocessing import PolynomialFeatures
from sklearn.pipeline import Pipeline
from sklearn.metrics import r2_score
from sklearn.base import BaseEstimator,TransformerMixin
from sklearn.metrics import r2_score,mean_squared_log_error,mean_absolute_error

from sklearn.base import BaseEstimator,TransformerMixin


# Setting Pandas float format
pd.options.display.float_format = '{:,.1f}'.format

SEED = 42   #The Answer to the Ultimate Question of Life, The Universe, and Everything
np.random.seed(SEED)


class RollingMean(BaseEstimator,TransformerMixin):
    '''
    Description
    ----------
    Provide rolling window calculations.
   
    Arguments
    ----------
    window: int
        Size of the moving window. This is the number of observations used for calculating the statistic.
        
    min_periods: int, default None
        Minimum number of observations in window required to have a value 
        (otherwise result is NA). For a window that is specified by an offset, 
        min_periods will default to 1. Otherwise, min_periods will default 
        to the size of the window.
        
    center: bool, default False
        Set the labels at the center of the window.
        

    active: boolean
        This parameter controls if the selection will occour. This is useful in hyperparameters searchs to test the contribution
        in the final score
        
    '''
    
    def __init__(self,window,
                 min_periods = None,
                 center = False,
                 active=True,
                 columns = 'all'
                ):
        self.columns = columns
        self.active = active
        self.window = window
        self.min_periods = min_periods
        self.center = center

        
    def fit(self,X,y=None):
        return self
        
    def transform(self,X):
        if not self.active:
            return X
        else:
            return self.__transformation(X)

    def __transformation(self,X_in):
        X = X_in.copy()
        
        if type(self.columns) == str:
            if self.columns == 'all':
                self.columns = list(X.columns)
        
        for col in self.columns:  
            X[col] = X[col].fillna(0).rolling(window = self.window,
                                              min_periods = self.min_periods,
                                              center = self.center
                                             ).mean()
        return X.dropna()
        
    def inverse_transform(self,X):
        return X


# This class allows the application of the logarithm in the attributes to be one of the steps in the pipeline.

# In[ ]:


class ApplyLog1p(BaseEstimator,TransformerMixin):
    '''
    Description
    ----------
    Apply numpy.log1p to specified features.
   
    Arguments
    ----------
        
    columns: list, default False
        Column names to apply numpy.log1p.
        

    active: boolean
        This parameter controls if the selection will occour. This is useful in hyperparameters searchs to test the contribution
        in the final score
        
    '''
    
    def __init__(self,active=True,columns = 'all'):
        self.columns = columns
        self.active = active
        
    def fit(self,X,y=None):
        return self
        
    def transform(self,X):
        if not self.active:
            return X
        else:
            return self.__transformation(X)

    def __transformation(self,X_in):
        X = X_in.copy()
        
        if type(self.columns) == str:
            if self.columns == 'all':
                self.columns = list(X.columns)
        
        for col in self.columns:  
            X[col] = np.log1p(X[col])
            
        return X
        
    def inverse_transform(self,X):
        if not self.active:
            return X
        else:
            return self.__inverse_transformation(X)

    def __inverse_transformation(self,X_in):
        X = X_in.copy()
        
        if type(self.columns) == str:
            if self.columns == 'all':
                self.columns = list(X.columns)
        
        for col in self.columns:  
            X[col] = np.expm1(X[col])
            
        return X


country = 'Russia'
df = pd.read_csv(
    'https://raw.githubusercontent.com/microsoft/Bing-COVID-19-Data/master/data/Bing-COVID19-Data.csv',
parse_dates=['Updated'])
filter_ = True
filter_ &= df['Country_Region'] == country
filter_ &= df['AdminRegion1'].isna()

dataset = df[filter_].rename(columns={
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


first_day = dataset.iloc[0]['date']
dataset['days'] = (dataset['date']-first_day).dt.days

dataset['change_cases'] = (dataset['cases']-dataset['cases'].shift())
dataset['change_deaths'] = (dataset['deaths']-dataset['deaths'].shift())
dataset['change_recovered'] = (dataset['recovered']-dataset['recovered'].shift())


# The steps for pre-processing the data consist of:
# 1. Apply the 14-day rolling mean
# 2. Select only the attributes * ('days', 'cases', 'deaths', 'recovered', 'change_cases', 'change_deaths', 'change_recovered') *
# 3. Application of the Logarithmic Scale in the Attributes
# 
# To make the rolling mean and the logarithmic scale, I created two classes in the 'Classes and Functions' Section

# Pipeline for preprocessing
preproc = Pipeline(steps = [
    ('rolling_mean',RollingMean(window = 8,columns = [
        'cases', 'deaths', 'recovered',
        'change_cases','change_deaths','change_recovered'],center = True)),
    
    ('select',FeatureSelector(features = ['days','cases', 'deaths',
        'recovered','change_cases','change_deaths','change_recovered'])
    ),
])

# Log Scalling
log_apply = ApplyLog1p(columns = 'all')

# Applying pre-processing pipeline
df = log_apply.transform(preproc.transform(dataset))

# full dataset
X = df[['days','cases', 'deaths', 'recovered']]
yc = df['change_cases'].fillna(0)
yd = df['change_deaths'].fillna(0)
yr = df['change_recovered'].fillna(0)

train_size = 0.99
index_split = int(round(train_size*len(X)))

# training dataset
X_trn  = X.iloc[:index_split]
yc_trn = yc.iloc[:index_split]
yd_trn = yd.iloc[:index_split]
yr_trn = yr.iloc[:index_split]

#test dataset
X_tst  = X.iloc[index_split:]
yc_tst = yc.iloc[index_split:]
yd_tst = yd.iloc[index_split:]
yr_tst = yr.iloc[index_split:]


# ### Pipelines

# In[ ]:


# Pipeline for regression
regression_c = Pipeline(steps = [
    ('polinomial',PolynomialFeatures(degree = 3)),
    ('regressor',LinearRegression()),
])
regression_d = Pipeline(steps = [
    ('polinomial',PolynomialFeatures(degree = 3)),
    ('regressor',LinearRegression()),
])
regression_r = Pipeline(steps = [
    ('polinomial',PolynomialFeatures(degree = 3)),
    ('regressor',LinearRegression()),
])



regression_c.fit(X_trn,yc_trn)

regression_d.fit(X_trn,yd_trn)

regression_r.fit(X_trn,yr_trn)


predictions = log_apply.inverse_transform(pd.concat([
    X.reset_index(drop=True),
    pd.DataFrame(regression_c.predict(X),columns = ['change_cases']),
    pd.DataFrame(regression_d.predict(X),columns = ['change_deaths']),
    pd.DataFrame(regression_r.predict(X),columns = ['change_recovered']),
],1))


# Now that we have used the test dataset to check the model, we can train the model with the entire dataset to make future predictions:

# In[ ]:


regression_c.fit(X,yc)
regression_d.fit(X,yd)
regression_r.fit(X,yr)

predictions = log_apply.inverse_transform(pd.concat([
    X.reset_index(drop=True),
    pd.DataFrame(regression_c.predict(X),columns = ['change_cases']),
    pd.DataFrame(regression_d.predict(X),columns = ['change_deaths']),
    pd.DataFrame(regression_r.predict(X),columns = ['change_recovered']),
],1))


# With regressions, we have a machine learning model for the following system of differential equations:
# 
# $\\\\$
# 
# 
# <center>
# $ \frac{dC}{dt} = f_c(t,C(t),D(t),R(t)), $
# </center>
# 
# $\\\\$
# 
# <center>
# $ \frac{dD}{dt} = f_d(t,C(t),D(t),R(t)), $
# </center>
# 
# $\\\\$
# 
# <center>
# $ \frac{dR}{dt} = f_r(t,C(t),D(t),R(t)),$
# </center>
# 
# $\\\\$
# This can be solved numerically with the `scipy` library.
# 
# First, the system of differential equations is defined as:

def diff_eq(x,t):
    """
    Function returning the differential equations of the model

    """
    # setting the functions
    c,r,d = x
    lnt = np.log1p(t)
    lnx = np.log1p(x)
    
    
    # mathematical equations
    DiffC = np.expm1(regression_c.predict([[lnt]+list(lnx)]))[0]
    DiffD = np.expm1(regression_d.predict([[lnt]+list(lnx)]))[0]
    DiffR = np.expm1(regression_r.predict([[lnt]+list(lnx)]))[0]

    return np.array([DiffC,DiffD,DiffR])

def neg_diff_eq(x,t):
    return -diff_eq(x,-t)


# In the previous sections, each step of the process was carried out in detail. However, to search for the best hyperparameters of the model, you must run the entire sequence of steps again for each attempt. Instead, it is better to automate the process and the best way to do this is by defining a new class:

# In[ ]:


class Covid19Regressor(BaseEstimator,TransformerMixin):
    '''
    Description
    ----------
    Arguments
    ----------
    active: boolean
        This parameter controls if the selection will occour. This is useful in hyperparameters searchs to test the contribution
        in the final score
        
    '''
    
    def __init__(self,
                 confirmed = 'cases', 
                 deaths = 'deaths',
                 recovered = 'recovered',
                 
                 confirmed_rate = 'change_cases', 
                 deaths_rate = 'change_deaths',
                 recovered_rate = 'change_recovered',
                 
                 time = 'days',
                 window = 7,
                 min_periods = None,
                 center = True,
                 polynomial_degree = 2,
                 regressor = LinearRegression,
                 regressor_parameters = {},
                 t_initial = 'last',
                 t_min = 20,
                 t_max = 500,
                 n_points = 500
                 
                ):
        
        self.confirmed = confirmed
        self.confirmed_rate = confirmed_rate
        self.deaths = deaths
        self.deaths_rate = deaths_rate
        self.recovered = recovered
        self.recovered_rate = recovered_rate
        self.time = time
        self.window = window
        self.min_periods = min_periods
        self.center = center
        self.polynomial_degree = polynomial_degree
        self.regressor = regressor
        self.regressor_parameters = regressor_parameters
        self.t_initial = t_initial
        self.t_min = t_min
        self.t_max = t_max
        self.n_points = 1+n_points
        
        
    def fit(self,X,y):
        
        # Receiving the data
        self.X = X[[self.time,self.confirmed,self.deaths,self.recovered]].copy()
        self.y = y[[self.confirmed_rate,self.deaths_rate,self.recovered_rate]].copy()
        
        # Evaluating the rolling mean for X
        for col in [self.confirmed,self.deaths,self.recovered]:  
            self.X[col] = self.X[col].fillna(0).rolling(window = self.window,
                                              min_periods = self.min_periods,
                                              center = self.center
                                             ).mean()
            
        # Evaluating the rolling mean for y    
        for col in [self.confirmed_rate,self.deaths_rate,self.recovered_rate]:  
            self.y[col] = self.y[col].fillna(0).rolling(window = self.window,
                                              min_periods = self.min_periods,
                                              center = self.center
                                             ).mean()
            
        # Applying the log scale
        self.X[self.time] = np.log1p(self.X[self.time])

        for col in [self.confirmed,self.deaths,self.recovered]: 
            self.X[col] = np.log1p(self.X[col])
            
        for col in [self.confirmed_rate,self.deaths_rate,self.recovered_rate]: 
            self.y[col] = np.log1p(self.y[col])
        
        # Dropping NaN
        temp = pd.concat([self.X,self.y],1).dropna()
        self.X = temp[[self.time,self.confirmed,self.deaths,self.recovered]]
        self.y = temp[[self.confirmed_rate,self.deaths_rate,self.recovered_rate]]
            
        # Pipeline for regression
        regression_c = Pipeline(steps = [
            ('polinomial',PolynomialFeatures(degree = self.polynomial_degree)),
            ('regressor',self.regressor(**self.regressor_parameters)),
        ])
        regression_d = Pipeline(steps = [
            ('polinomial',PolynomialFeatures(degree = self.polynomial_degree)),
            ('regressor',self.regressor(**self.regressor_parameters)),
        ])
        regression_r = Pipeline(steps = [
            ('polinomial',PolynomialFeatures(degree = self.polynomial_degree)),
            ('regressor',self.regressor(**self.regressor_parameters)),
        ])
        
        # Fitting model
        regression_c.fit(self.X,self.y[self.confirmed_rate])
        regression_d.fit(self.X,self.y[self.deaths_rate])
        regression_r.fit(self.X,self.y[self.recovered_rate])
        
        
        # Predicted Rates
        self.predicted_rate = pd.concat([
            self.X.reset_index(drop=True),
            pd.DataFrame(regression_c.predict(self.X),columns = ['pred_'+self.confirmed_rate]),
            pd.DataFrame(regression_d.predict(self.X),columns = ['pred_'+self.deaths_rate]),
            pd.DataFrame(regression_r.predict(self.X),columns = ['pred_'+self.recovered_rate]),
        ],1)
        
        for col in self.predicted_rate.columns:
            self.predicted_rate[col] = np.expm1(self.predicted_rate[col])
        
        
        # Defining the diferential equations
        def diff_eq(x,t):
            """
            Function resturning the differential equations of the model

            """
            # setting the functions
            c,r,d = x
            lnt = np.log1p(t)
            lnx = np.log1p(x)


            # mathematical equations
            DiffC = np.expm1(regression_c.predict([[lnt]+list(lnx)]))[0]
            DiffD = np.expm1(regression_d.predict([[lnt]+list(lnx)]))[0]
            DiffR = np.expm1(regression_r.predict([[lnt]+list(lnx)]))[0]

            return np.array([DiffC,DiffD,DiffR])

        def neg_diff_eq(x,t):
            return -diff_eq(x,-t)
        
        if type(self.t_initial) == str:
            if self.t_initial == 'last':
                t_initial = int(round(list(cov19.predicted_rate[self.time])[-1]))
        else:
            t_initial = self.t_initial
        
        
        ind_ref = self.predicted_rate[self.time][
            round(self.predicted_rate[self.time]).astype(int) == t_initial].index[0]
        
        # initial conditions
        t0,*x0 = np.expm1(list(self.X.iloc[ind_ref]))

        n_points_right = int(round(self.n_points*(self.t_max-t0) / (self.t_max-self.t_min)))
        n_points_left = int(round(self.n_points*(t0-self.t_min) / (self.t_max-self.t_min)))


        # right integrate
        days_list = np.linspace(t0,self.t_max,n_points_right)
        x = odeint(diff_eq,x0,days_list)

        # left integrate
        neg_days_list = np.linspace(-t0,-self.t_min,n_points_left)
        neg_x = odeint(neg_diff_eq,x0,neg_days_list)

        #joinning solution
        self.t_ode = np.concatenate((-neg_days_list[::-1], days_list))
        self.x_ode = np.concatenate((neg_x[::-1], x))
        
        self.predictions = pd.concat([
            pd.DataFrame(self.t_ode,columns = [self.time]),
            pd.DataFrame(self.x_ode,columns = [self.confirmed,self.deaths,self.recovered])]
        ,1)

        return self
        
    def transform(self,X):
        return X
    
    def predict(self,X):
        
       
        return np.array([
            np.interp(X, self.t_ode, self.x_ode[:,0]),
            np.interp(X, self.t_ode, self.x_ode[:,1]),
            np.interp(X, self.t_ode, self.x_ode[:,2]),
        ])
        


# In[ ]:


cov19 = Covid19Regressor(window = 8,polynomial_degree = 3,t_initial = 250)
cov19.fit(dataset[['days','cases','deaths','recovered']],
    dataset[['change_cases','change_deaths','change_recovered']])






