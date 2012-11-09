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

