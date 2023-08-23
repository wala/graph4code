#!/usr/bin/env python
# coding: utf-8

# In[1]:


# This Python 3 environment comes with many helpful analytics libraries installed
# It is defined by the kaggle/python Docker image: https://github.com/kaggle/docker-python
# For example, here's several helpful packages to load

import numpy as np # linear algebra
import pandas as pd # data processing, CSV file I/O (e.g. pd.read_csv)

# Input data files are available in the read-only "../input/" directory
# For example, running this (by clicking run or pressing Shift+Enter) will list all files under the input directory

import os
for dirname, _, filenames in os.walk('/kaggle/input'):
    for filename in filenames:
        print(os.path.join(dirname, filename))

# You can write up to 20GB to the current directory (/kaggle/working/) that gets preserved as output when you create a version using "Save & Run All" 
# You can also write temporary files to /kaggle/temp/, but they won't be saved outside of the current session


# In[2]:


# importing libraries and creating a dataFrame
import matplotlib.pyplot as plt
import seaborn as sns
get_ipython().run_line_magic('matplotlib', 'inline')
df = pd.read_csv('/kaggle/input/titanic/train.csv')
df


# ## Data Cleaning

# In[3]:


df.isnull()


# In[4]:


# checking null values using the heatmap for better results
sns.heatmap(df.isnull(), yticklabels = False, cbar = False, cmap = 'viridis')


# As we can see that age and cabin have missing values and we can adjust the age values by guessing the age
# as enough data is availabe to make predictions but in the cabin major data is missing so we will drop this

# In[5]:


# to make age predcition first we need to find the Age pattern by plotting the Age on the charts
sns.boxplot(x = 'Pclass', y = 'Age', data = df, palette='winter')


# In[6]:


# as we can see that in Pclass 1 the age is approx 37 acc to 50th percentile, 29 in Pclass 2 and 24 in Pclass 3.
# so according to that we will make this estimation and put the age respectively
def impute_age(cols):
    Age = cols[0]
    Pclass = cols[1]
    
    if pd.isnull(Age):
        if Pclass == 1:
            return 37
        elif Pclass == 2:
            return 29
        else:
            return 24
    else:
        return Age
        
# now apply this function to make changes
df["Age"] = df[['Age', 'Pclass']].apply(impute_age, axis = 1)
df


# In[7]:


# heat map checked after dropping the Cabin col in the next line of code
sns.heatmap(df.isnull(), yticklabels=False, cbar=False, cmap='viridis')


# In[8]:


#  as age is not null and we will not drop the cabin column
df.drop('Cabin', axis=1, inplace=True)
df


# In[9]:


df.info()
# as we can see that all the columns are filled and there is no null values

