#!/usr/bin/python
import cPickle as pickle
import preprocessor.*
import databox.*
import predictor.*
import bottleneck as bn

class Model:
    def __init__(self, predictor, dataBox, preprocessor):
        self.predictor = predictor
        self.dataBox = dataBox
        self.preprocessor = preprocessor
        self.preppedData = False
    def loadData():
        self.dataBox.load()            
    def prepData():
        self.dataBox.data = self.preprocessor.preprocess(self.dataBox.getData())
        preppedData = True
    def predict(user, item):
        if not preppedData:
            prepData()
        return self.preprocessor.deprocess(user, item, self.predictor.predict(user, item))
    def getErrors(triples): #triples is expected to be a list of (user, item, value) 3-tuples
        if not self.predictor.stateless:
            return [value - self.predict(user, item) for (user, item, value) in triples]
        else:
            pass #parallel code will be here
    def getTopN(user, items, n): #look at me ma, I can write illegible code too!
        if not self.predictor.stateless:
            return [items[i] for i in bn.argpartsort([self.predict(user, item) for item in items], n)[:n]]
            #In all seriousness though: this takes the predictions for each of the items, argpartsort partially sorts the indicies of the list based on the values, fully sorting the first n, we trim the list to the fully sorted subset (ie the top n (we actually found the n smallest of the negatice of the list)), and returns the items for those indicies.
        else:
            pass #parallel code will be here
