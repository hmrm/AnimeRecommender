from scipy import sparse
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

#TODO: needs to be tested for logic
#Implementation may not be efficient, definite opportunity for optimization
def getMeansCenteringPreprocessor(axis=0): #0 is column mean, 1 is row mean
    def proc(data): #makes a copy, may be needed to do inplace if there are memory issues
        if (not sparse.isspmatrix_csc(data)) and axis == 0: #change to the right format
            data = data.tocsc()
        elif (not sparse.isspmatrix_csr(data)) and axis == 1:
            data = data.tocsr()
        else:
            data = data.copy()
        for i in xrange(data.shape[(axis + 1) % 2]): #number of columns/rows
            if axis == 0: #get the relevant row/column
                submat = data.getcol(i)
            else:
                submat = data.getrow(i)
            if not submat.nnz == 0:
                m = submat.mean() * (float(data.shape[axis]) / float(submat.nnz)) #get the mean of the non-missing values by getting the general mean then multiplying by n/n_non_missing
            else:
                m = 0 #placeholder value
            means.append(m)
            x = i #indicies for change
            y = i
            for j in xrange(data.shape[(axis + 1) % 2]): #this loops over all the entries in the row/column
                if axis == 0:
                    x = j
                else:
                    y = j
                if not data(x,y) == 0:
                    data(x,y) -= m
        return (data, (axis, means)):

    def deproc((user, item, value), deprocdata): #assuming user, item corresponds to (user, item) position (ie user is row, item is column
        if deprocdata[0] == 0:
            return value + deprocdata[1][item]
        else:
            return value + deprocdata[1][user]
