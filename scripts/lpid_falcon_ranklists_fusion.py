#!/usr/bin/env python3
import sys
import numpy
import lpid_falcon_chunk_queries_as_matrix as lpidmatrix

def ranklist_score_using_ranks(l, LEN=4) :
    ll = list(filter(lambda x: len(x) == LEN, [l[i:min(len(l),i+LEN)] for i in range(len(l))]))
    ss = []
    for l in ll :
        s = 0
        for x in l :
            s += 1/(max(x,3)**2) if x else 1/(1000**2)
        ss.append(s)
    return max(ss) if len(ss) > 0 else 0

def toRankMatrixRepr(scorematrix) :
    rankmatrix = numpy.zeros(scorematrix.shape)
    for j in range(scorematrix.shape[1]) :
        s = scorematrix[:,j]                                     # scores for one lp chunk
        ii = numpy.argsort(-s)
        rankmatrix[ii,j] = numpy.array(range(len(s))) + 1
    return rankmatrix

if __name__ == '__main__':
    # falcon results on STDIN - assumes all files are numeric 
    rawRes = [line.strip() for line in sys.stdin]
    scorematrix = lpidmatrix.parseResults(rawRes,None)[0]
    ids = list(map(lambda x : str(int(x)), scorematrix[:,0]))
    scorematrix = scorematrix[:,2:]                              # keep only scores
    rankmatrix  = toRankMatrixRepr(scorematrix)
    fusedscores = list(map(lambda x : ranklist_score_using_ranks(x), [list(rankmatrix[i,:]) for i in range(rankmatrix.shape[0])]))
    fusedscores_sortindexes = list(numpy.argsort(-numpy.array(fusedscores)))
    ii = fusedscores_sortindexes
    for i in range(len(ii)) :
        print('[%6.4f] - %s' % (fusedscores[ii[i]], ids[ii[i]]))
