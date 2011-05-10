#!/usr/bin/env python
# evaluation of falcon ranking results
import sys, csv, re

def loadGroundtruthFile(fpath) :
    reader = csv.reader(open(fpath,'r'))
    gt = {}
    for line in reader :
        gt[line[0]] = line[1:]
    return gt

def parseResultsFile(fpath, excludeself=True) :
    pq = re.compile('^QUERY - (.*)$')
    pr = re.compile('^rank +\d+: [^-]+ - (.*)$')
    res = {}
    for line in open(fpath,'r') :
        mq = pq.match(line) 
        if mq :
            q = mq.group(1)
            res[q] = []
        else :
            mr = pr.match(line)
            if mr :
                if (excludeself and mr.group(1) != q) or (not excludeself) :
                    res[q].append(mr.group(1))
    return res

def computeGoodRanks(res,gt) :
    # compute ranks of matching positions
    mpr = {}
    for q in gt :
        mpr[q] = []
        for i in range(len(res[q])) :
            if res[q][i] in gt[q] :
                mpr[q].append(i+1)
    return mpr

def computeMRR(mpr) :
    mrr = 0
    for q in mpr :
        mrr += 1./mpr[q][0]
    mrr /= len(mpr)
    return mrr

def computeAP(rl) :
    ap = 0.
    c = 0
    for r in rl :
        c += 1.
        ap += c/r
    ap /= len(rl)
    return ap

def computeMAP(mpr) :
    m_ap = 0
    for q in mpr :
        m_ap += computeAP(mpr[q])
    m_ap /= len(mpr)
    return m_ap
    
if __name__ == '__main__' :
    # exactly 2 args: results and groundtruth files
    if len(sys.argv) != 3 :
        print 'invalid arguments'
        print 'must be: evaluate.py results.txt groundtruth.txt'
        exit(1)
    gt = loadGroundtruthFile(sys.argv[2])
    res = parseResultsFile(sys.argv[1])
    mpr = computeGoodRanks(res,gt)
    for q in mpr :
        print 'query %s' % q
        print 'matching ranks: %s' % ''.join(`mpr[q]`)
        print
    print 'MRR: %f' % computeMRR(mpr)
    print 'MAP: %f' % computeMAP(mpr)
