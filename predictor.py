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

def mixedPredictor(predictors, mixfunction):
    def predictor(user, item):
        return((mixfunction([p.predict(user, item) for p in predictors]), True))
    return Predictor(predictor)
