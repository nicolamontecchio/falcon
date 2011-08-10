#!/usr/bin/env python3
import argparse, random, os, subprocess, evaluation, io

''' Train the FALCON's query pruning strategy. '''

class FalconTrainingAlgo :
  def getRankingCmdLine(self, queryPruningStrategy, indexpath) :
    return 'java -jar dist/falcon.jar -b -l %d -o %d -p -P %s -s %d %s' % (self.segmentLength, self.segmentOverlap, 
                                                                           ','.join(str(x) for x in queryPruningStrategy),
                                                                           self.subSampling, indexpath)

  def getIndexingCmdLine(self) :
    # return the cmd line for indexing and the index path as a tuple;
    t = '-t %d' % self.transpositions if self.transpositions != None else ''
    ip = 'index_%s' % str(random.randint(0,1000000000))
    ip = 'index_732630974' # TODO remove
    return ('/usr/bin/java -jar dist/falcon.jar -i %s -s %d -l %d -o %d %s %s' % (self.chromapath, self.subSampling, self.segmentLength, 
                                                                        self.segmentOverlap, t, ip),ip)

  def __init__(self):
    self.segmentLength = 250
    self.segmentOverlap = 0
    self.subSampling = 3
    self.transpositions = 3
    self.chromapath = '../musiclef/classical/data/chroma'
    self.trainingsetfiles_complete = [line.strip() for line in open('trainingsetfiles_coverset_1.txt')]
    self.groundtruth = evaluation.parsegtfile(open('cover_info_set_1.csv'))
  
  def evaluate(self, indexpath, files, qps) :
    print('#evaluating')
    # perform ranking using evaluation script 
    print('#'+self.getRankingCmdLine(qps,indexpath))
    p = subprocess.Popen(self.getRankingCmdLine(qps,indexpath).split(' '),
        stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    out = p.communicate(bytes('\n'.join(files), 'UTF-8'))[0]
    print(type(out))
    res = evaluation.parseres(io.BytesIO(out))
    matches = evaluation.computeMatches(res,self.groundtruth,'.mp3.chroma')
    print('MRR', evaluation.computeMRR([matches[m] for m in matches]))
        


  def dotraining(self) :
    # fix a few files for evaluating the MAP
    trainingfiles_validation = random.sample(self.trainingsetfiles_complete,10)
    # perform indexing
    icmd,ip = self.getIndexingCmdLine()
    print('#indexing w/ %s' % icmd)
    #os.system(icmd)
    
    # initial 
    bestqps = [.34,0,1,.34,0,1,.34,0,1,.34,0,1]
    bestmap = self.evaluate(ip, trainingfiles_validation, bestqps)
    
    # TODO complete


if __name__ == '__main__' :
  algo = FalconTrainingAlgo()
  algo.dotraining()
  
