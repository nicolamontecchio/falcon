#!/usr/bin/env python3
import argparse, random

'''
Train the FALCON.
Parameters trained (for now):
- segment length
- segment overlap
Required parameters are:
- the cover-sets description file (in musiclef format),
- the path to a directory containing the collection chroma files
Optional parameters :
- subsampling
- size of training subset
- life of training subset in epochs
- moveset size for each epoch
'''

class FalconParams :
  def __init__(self) :
    self.segmentLength = 250
    self.segmentOverlap = 125
    self.queryPruningStrategy = [.34,0,1,.34,0,1,.34,0,1,.34,0,1]
  def perturbate(self) :
    self.segmentLength = max(50,self.segmentLength + random.randint(0,50) - 25)
    self.segmentOverlap = max(0,min((self.segmentLength*3)//4, self.segmentOverlap + random.randint(0,50) - 25 ))
    for i in range(len(self.queryPruningStrategy)) : 
      self.queryPruningStrategy[i] = max(min(self.queryPruningStrategy[i] + (random.random()-.5) * 0.4 ,1),0)

class FalconTrainingAlgo :
  def getRankingCmdLine(self, params) :
    return 'java -jar dist/falcon.jar -b -l %d -o %d -p -P %s -s %d %s' % (params.segmentLength, params.segmentOverlap, 
                                                                           ','.join(str(x) for x in params.queryPruningStrategy),
                                                                           self.subSampling, self.indexpath)

  def getIndexingCmdLine(self, params) :
    return 'java -jar dist/falcon.jar -i %s -s %d -l %d -o %d %s' % (self.chromapath, self.subSampling, params.segmentLength, 
                                                                        params.segmentOverlap, self.indexpath)

  def __init__(self, ss, tss, tsl, mss) :
    self.subSampling = ss
    self.trainingSetSize = tss
    self.trainingSetLife = tsl
    self.moveSetSize = mss
    self.indexpath = 'index_training'
    self.chromapath = '../../.................'
  
if __name__ == '__main__' :
  parser = argparse.ArgumentParser(description='Train the FALCON')
  parser.add_argument('--subsampling', type=int, help='subsampling')
  parser.add_argument('--training-set-size', type=int, help='size of training subset')
  parser.add_argument('--training-set-life', type=int, help='life (in epochs) of training subset')
  parser.add_argument('--moveset-size', type=int, help='size of moveset for each training epoch')
  
  args = parser.parse_args()
  
  
