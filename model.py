#!/usr/bin/python

#arguments:
#          1: Training File
#          2: normalization: item, user, none, ui, or iu 
#          3: model algorithm: item-item-knn
#          4: Number of nearest neighbors, if applicable, else expects "-"
# sys.argv = ["", "0_training_re_pickle.dat", "ui", "item-item-knn", 15]
import cPickle as pickle
import sys
from sklearn.preprocessing import normalize
import numpy as np
import scipy.sparse as sparse
from sklearn.neighbors import NearestNeighbors

#get data#
with open(sys.argv[1], "rb") as infile:
    training_data = pickle.load(infile) #expected sparse csr format

#normalize data#
if sys.argv[2] == "user":
    normalized_data = normalize(training_data.copy())
elif sys.argv[2] == "item":
    normalized_data = normalize(training_data.copy(), axis = 0)
elif sys.argv[2] == "iu": #item then user
    normalized_data = normalize(training_data.copy(), axis = 0)
    normalize(normalized_data, copy = False)
elif sys.argv[2] == "ui":
    normalized_data = normalize(training_data.copy())
    normalize(normalized_data, axis = 0, copy = False)
else: 
    normalized_data = training_data.copy()

#build model#
if True:#sys.argv[3] == "item-item-knn":
    model = NearestNeighbors(n_neighbors = sys.argv[4])
    model.fit(normalized_data.transpose())

