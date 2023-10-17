import pandas as pd

def foo(x):
    x = x.dropna()
    return x.head(5)
    
x = foo(pd.read_csv("/tmp/junk"))
y = x.to_csv()
x["y"] = foo(pd.DataFrame(x))
