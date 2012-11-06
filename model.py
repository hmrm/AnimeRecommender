#!/usr/bin/python

class Model:
    def __init__(self, dataLoader, preprocessor, predictor):
        self.dataLoader = dataLoader #should be a DataBox
        self.preprocessor = preprocessor
        self.predictor = predictor


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
    def __init__(self, function, inverse):
        self.function = function #expected to return a tuple: (processed data, info for inverse to do deprocessing)
        self.inverse = inverse #expected to work on subsets of the columns of function
        self.deprocessdata = None
    def preprocess(data):
        (ret, self.deprocessdata) = function(data)
        return ret
    def deprocess(data):
        if self.deprocessdata = None:
            raise Exception("Requested deprocess when there was no evidence of preprocessing")
        return inverse(deprocessdata)

class Predictor:
    def __init__(self, predictor):
        self.predictor = predictor #expected to be a function from ((user, item), data) tuples to (prediction, data modified with new information) tuples
        self.data = None
    def predict(user, item):
        (ret, data) = predictor((user, item), data)
        return ret

def sequencedPreprocessor(preprocessors): #expects an ordered list of preprocessors, from first applied to last (ie f(g(z(m(x)))) would be [m,z,g,f]
    def function(data):
        for i in xrange(len(preprocessors)):
            (data, _) = preprocessors[i].function(data)
        return (data, True)
    def deprocess(data):
        for i in xrange(len(preprocessors) - 1, -1, -1): #looping backwards
            data = preprocessors[i].deprocess(data)
        return data
    return Preprocessor(function, deprocess)

def mixedPredictor(predictors, mixfunction):
    def predictor(user, item):
        return((mixfunction([p.predict(user, item) for p in predictors]), True))
    return Predictor(predictor)
