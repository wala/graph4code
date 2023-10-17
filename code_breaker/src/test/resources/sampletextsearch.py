from sklearn import decomposition

def foo():
     p = decomposition.PCA(n_components=3)  # Watson "memory efficient"
     y = 3