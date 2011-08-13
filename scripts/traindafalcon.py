#!/usr/bin/env python3
import argparse, random, os, subprocess, evaluation, io, math, copy

''' Train the FALCON's query pruning strategy. '''

rangesqps = [0.34,0,0.46, 
             0.34,0.014,0.738, 
             0.34,0.000057,0.015, 
             0.01,0,1]     # not caring about max
initialqps = [0.6864673544176777, 0, 0.26711385301342294, 
             0.4338822710306313, 0.04433397558577018, 0.6933435235907854, 
             0.24611271771351032, 5.7e-05, 0.015, 0.01, 0, 1]                   # taken from previous training
randmultipliers = [0.1,0.01,0.1,
                   0.1,0.01,0.2,
                   0.1,0.00003,0.004,
                   0,0,1]

def sigmoid(x) :
  # sigmoid function
  return 1. / (1. + math.exp(-x))

def perturbateQps(q) :
  # return a perturbated copy of query pruning strategy q
  qps = copy.deepcopy(q)
  while qps == q :
    # intervals
    qpscopy = copy.deepcopy(qps)
    # move only ONE interval at a time
    j = random.randint(0,2)      #should be (0,3), but last is unused
    for k in range(3) : 
      for i in range(k*j,k*j+3) :
        #print('oldqps[%d] = %f' % (i,qps[i]))
        qps[i] = qps[i] + (random.random()-0.5)*randmultipliers[i]
        #print('newqps[%d] = %f' % (i,qps[i]))
        #if   i%3 == 1 : qps[i] = max(0.3,qps[i])   # never too low
        if i%3 == 1 : qps[i] = max(rangesqps[i],qps[i])
        elif i%3 == 2 : qps[i] = min(rangesqps[i],qps[i])
    for i in range(4) :
      if qps[3*i+1] >= qps[3*i+2] : 
        qps[3*i+1] = qpscopy[3*i+1]
        qps[3*i+2] = qpscopy[3*i+2]
  return qps

class FalconTrainingAlgo :
  def getRankingCmdLine(self, queryPruningStrategy, indexpath) :
    return 'java -jar dist/falcon.jar -v -b -l %d -o %d -p -P %s -s %d %s%s' % (self.segmentLength, self.segmentOverlap, 
                                                                                 ','.join(('%20.18f' % x) for x in queryPruningStrategy), self.subSampling, 
                                                                                 ('-t %d ' % self.transpositions) if self.transpositions else '',
                                                                                 indexpath)

  def getIndexingCmdLine(self) :
    # return the cmd line for indexing and the index path as a tuple;
    t = '-t 1' if self.transpositions != None else ''
    ip = 'index_%s' % str(random.randint(0,1000000000))
    return ('/usr/bin/java -jar dist/falcon.jar -i %s -s %d -l %d -o %d %s %s' % (self.chromapath, self.subSampling, self.segmentLength, 
                                                                                  self.segmentOverlap, t, ip),ip)
  
  def __init__(self):
    self.segmentLength = 300
    self.segmentOverlap = 100
    self.subSampling = 3
    self.transpositions = 3
    self.chromapath = '../musiclef/classical/data/chroma'
    self.trainingsetfiles_complete = [line.strip() for line in open('trainingsetfiles_coverset_1.txt')]
    self.groundtruth = evaluation.parsegtfile(open('cover_info_set_1.csv'))
  
  def objectiveFunction(mrr, pruned):
    # return the value of the objective function
    l = 0.2
    a = 20.
    b = 10.
    mrrmin = 0.6
    prunedmin = 0.5
    return (1 - l) * sigmoid(a * (mrr - mrrmin)) + l * sigmoid(b * (pruned - prunedmin))

  def evaluate(self, indexpath, files, qps, verbose=False) :
    # perform ranking using evaluation script and return the value of the objective function
    if verbose :
      print('#evaluating w/ '+self.getRankingCmdLine(qps,indexpath))
    p = subprocess.Popen(self.getRankingCmdLine(qps,indexpath).split(' '),
        stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    out = p.communicate(bytes('\n'.join(files), 'UTF-8'))[0].decode('UTF-8')
    #print('queries: %s' % ' '.join([os.path.basename(q) for q in files]))
    res = evaluation.parseres(io.StringIO(out))
    #import pdb; pdb.set_trace()
    matches = evaluation.computeMatches(res,self.groundtruth,'.mp3.chroma')
    #for m in matches : print(matches[m])
    evalMap = evaluation.computeMAP([matches[m] for m in matches])
    if verbose:
      print('#  MAP', evalMap)
    pruned = 0
    total = 0
    for line in out.split('\n') :
      if line.find('pruned') == 0 :
        pruned += int(line.split(' ')[1])
        total += int(line.split(' ')[2])
    ofval = FalconTrainingAlgo.objectiveFunction(evalMap, pruned/total)
    if verbose :
      print('#  pruned/total ratio: %f' % (pruned/total))
      print('#  objective function value: %f' % ofval)
    return ofval
      
  def dotraining(self) :
    # fix a few files for evaluating the MAP
    # trainingfiles_validation = random.sample(self.trainingsetfiles_complete,50)
    trainingfiles_validation = self.trainingsetfiles_complete[:100]
    # perform indexing
    #icmd,ip = self.getIndexingCmdLine()
    #print('#indexing w/ %s' % icmd)
    #os.system(icmd)                               
    ip = 'index_300_150_s2_t3'
    # initial strategy, hand-crafted according to index w/ 3 transp, l=300 o=100 ss=3
    bestqps = initialqps
    bestmap = self.evaluate(ip, trainingfiles_validation, bestqps,True)
    # main loop
    for it in range(100) :
      # choose some evaluation files at random for each iteration
      evalfiles = random.sample(self.trainingsetfiles_complete,10)
      # get moveset and evaluate
      moveset = [perturbateQps(bestqps) for i in range(20)]
      moveset_scores = [self.evaluate(ip, evalfiles, qps) for qps in moveset]
      # select the best and test on the validation set
      moveset_bestindex = moveset_scores.index(max(moveset_scores))
      validationscore = self.evaluate(ip,trainingfiles_validation,moveset[moveset_bestindex],True)
      if validationscore > bestmap :
        bestmap = validationscore
        bestqps = moveset[moveset_bestindex]
        print('new best found: %10.8f %s' % (bestmap,str(bestqps)))
    

if __name__ == '__main__' :
  algo = FalconTrainingAlgo()
  algo.dotraining()
  
