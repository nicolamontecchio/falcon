#!/usr/bin/env python3
import argparse, sys, re, os, csv
import numpy as np

# Read the output of lpid_falcon_do_chunk_queries.py from stdin.
# Optional argument: path to the groundtruth file.
# Output a matrix which is nfiles x (2 + nseg)
#   where nfiles is the total number of files that are referenced in the output of the previos script,
#         nseg it the total number of queries (LP chunks)
# the first column is the file id (numeric) of the LP
# the second is a 0/1 relevance value (-1 if no groundtruth file is specified)
# the rest is a matrix of scores

def getAllReferencedFileIds(rawRes) :
    refFileIds = []
    for line in rawRes :
        if line.find('rank') == 0:
            fid = os.path.basename(line[line.find(' - ')+3:])
            if fid.find('.') > 0 : fid = fid[:fid.find('.')]
            refFileIds.append(fid)
    refFileIds = list(set(refFileIds))
    return list(map(lambda x : str(x), sorted(map(lambda x : int(x), refFileIds))))

def getLpId(rawRes) :
    p = re.compile('lpid_tmp_(\d+)')
    for line in rawRes :
        if len(p.findall(line)) > 0 :
            return p.findall(line)[0]

def parseResults(rawRes, gtfilepath) :
    allFileIds = getAllReferencedFileIds(rawRes)
    nfiles = len(allFileIds)
    fileId2Pos = {}
    for i in range(nfiles) : fileId2Pos[allFileIds[i]] = i

    pq = re.compile('chunk(\d+)$')
    pr = re.compile('^rank +(\d+): +([-+]?[0-9]*\.?[0-9]+) - (.*$)')
    
    allscores = {}
    for line in rawRes :
        if len(pq.findall(line)) > 0 :
            chunkId = int(pq.findall(line)[0])
            allscores[chunkId] = []
        if len(pr.findall(line)) > 0 :
            rline = pr.findall(line)[0]
            fid = os.path.basename(rline[2])
            if fid.find('.') > 0 : fid = fid[:fid.find('.')]
            score = float(rline[1])
            allscores[chunkId].append((fid,score))
    
    nseg = len(allscores)
    scorematrix = np.zeros((nfiles,nseg+2))
    for i in range(nfiles) :                       # first column: file ids
        scorematrix[i,0] = allFileIds[i]
    for j in allscores :                           # big matrix: scores
        chunkscores = allscores[j]
        j = j-1+2         # chunk indexing started from 1, but first two columns are reserved
        for r in chunkscores :
            i = fileId2Pos[r[0]]
            s = r[1]
            scorematrix[i,j] = s
    
    relevantNotDetected = []
    if gtfilepath :
        lpId = getLpId(rawRes)
        reader = csv.reader(open(gtfilepath,'r'))
        for line in reader :
            if len(line) > 0 :
                if line[0] == lpId :
                    relevant = line[1:]
        for r in relevant :
            if r in fileId2Pos :
                i = fileId2Pos[r]
                scorematrix[i,1] = 1
            else :
                relevantNotDetected.append(r)
    return (scorematrix,relevantNotDetected)

if __name__ == '__main__':
    gtfilepath = sys.argv[1] if len(sys.argv) > 1 else None
    rawRes = [line.strip() for line in sys.stdin]
    (m,rnd) = parseResults(rawRes, gtfilepath)
    for l in m.tolist() :
        l[0] = str(int(l[0]))
        l[1] = str(int(l[1]))
        print(','.join(map( lambda x : str(x),l)))
        mcols = len(l)
    for r in rnd :
        print('%s,1,%s' % (r,','.join(['0' for i in range(mcols-2)])))
