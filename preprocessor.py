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

def getIdentityPreprocessor():
    def proc(data):
        return (data, True)
    def deproc((_,_, value) _):
        return value
    return Preprocessor(proc, deproc)
