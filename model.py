#!/usr/bin/python
import cPickle as pickle
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


class DataBox:
    def __init__(self, dataLocation, loadFunction):
        self.dataLocation = dataLocation #argument to the load function, typically where the data is
        self.loadFunction = loadFunction #the function used to load the data, should return the data
        self.data = None
    def load():
        self.data = loadFunction(dataLocation)
    def getData(): #look at me, look at me, I can be lazy too!
        if self.data == None:
            self.load()
            if self.data == None:
                raise Exception("Data load failure")
        return self.data

class Preprocessor:
    def __init__(self, proc, deproc):
        self.proc = proc #expected to return a tuple: (processed data, info for inverse to do deprocessing)
        self.deproc = deproc #expected to take a ((user, item, value), deprocessdata) and return a value
        self.deprocessdata == None
    def preprocess(data):
        (ret, self.deprocessdata) = proc(data)
        return ret
    def deprocess(user, item, value):
        if self.deprocessdata == None:
            raise Exception("Requested deprocess when there was no evidence of preprocessing")
        return deproc((user, item, value), deprocessdata)

class Predictor:
    def __init__(self, predictor, stateless=False, init=None): #stateless parallelism not implemented yet
        self.predictor = predictor #expected to be a function from ((user, item), data) tuples to (prediction, data modified with new information) tuples
        self.stateless = stateless
        if stateless:
            if init=None:
                raise Exception("stateless but no initialization function in predictor")
            self.init = init
        self.data = None
    def init(self, data): #for use with parallel predictors
        self.data = self.init(data)
    def predict(self, user, item):
        if not stateless:
            (ret, self.data) = predictor((user, item), self.data)
            return ret
        else:
            return predictor((user, item), self.data)[0]

def sequencedPreprocessor(preprocessors): #expects an ordered list of preprocessors, from first applied to last (ie f(g(z(m(x)))) would be [m,z,g,f]
    def proc(data):
        for i in xrange(len(preprocessors)):
            (data, _) = preprocessors[i].function(data)
        return (data, True)
    def deproc((user, item, value), _):
        for i in xrange(len(preprocessors) - 1, -1, -1): #looping backwards
            value = preprocessors[i].deprocess(user, item, value)
        return value
    return Preprocessor(proc, deproc)

def mixedPredictor(predictors, mixfunction):
    def predictor(user, item):
        return((mixfunction([p.predict(user, item) for p in predictors]), True))
    return Predictor(predictor)
