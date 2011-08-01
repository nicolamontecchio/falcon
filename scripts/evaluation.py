#!/usr/bin/env python3
import csv, sys, os

def computeMRR(mpr) :
  '''
  Compute mean reciprocal rank for 
  list-of-rank-lists mpr.
  '''
  mrr = 0
  for q in mpr :
    mrr += 1./q[0]
  mrr /= len(mpr)
  return mrr

def computeAP(rl) :
  '''
  Compute average precision for rank list rl.
  rl is a list of non-decreasing integers,
  each denoting the position of a relevant match
  (counting from 1).
  '''
  ap = 0.
  c = 0
  for r in rl :
    c += 1.
    ap += c/r
  ap /= len(rl)
  return ap

def computeMAP(mpr) :
  '''
  Compute mean average precision for
  list-of-rank-lists mpr. See computeAP()
  for details.
  '''
  m_ap = 0
  for q in mpr :
    m_ap += computeAP(mpr[q])
  m_ap /= len(mpr)
  return m_ap

def parseres(f) :
  '''
  Parse output of a falcon query (including multiple queries).
  Return a dictionary, from the query file name to a sorted list
  containing a tuple sequence (filename,rank).
  '''
  # each group begins with "query: "
  querygroups = []          
  for line in f :
    line = line.strip()
    if line.find('query:') == 0 :
      querygroups.append([line])
    elif line.find('rank') == 0 :
      querygroups[-1].append(line)
  # process each group
  res = {}
  for group in querygroups :
    # first line is query file
    queryfilename = group[0][len('query: '):].strip()
    # subsequent lines are the matches
    matches = []
    for line in group[1:] :
      matchscore = float( line[line.find(':')+1:line.find('-')].strip() )
      matchfilename = line[line.find('-')+1:].strip()
      matches.append((matchfilename,matchscore))
    res[queryfilename] = matches
  return res

def parsegtfile(f) :
  '''
  Parse groundtruth file for the cover set.
  Return a list-of-lists, where each inner 
  list contains the file ids for a cover set.
  '''
  reader = csv.reader(f)
  # construct as a map for convenience
  coversets = {}
  for line in reader :
    if len(line) == 3 :
      if not line[1] in coversets :
        coversets[line[1]] = []
      coversets[line[1]].append(line[0])
  # convert to a list
  res = [coversets[cs] for cs in coversets] 
  return res

def computeMatches(res, gt, appendext='') :
  '''
  For each query in res, compute a list of matching
  positions according to the groundtruth gt.
  Return as a map[queryfile->matchlist], that can 
  be processed by compute{MRR,MAP} with little modification.
  appendext represents the (optional) extension at 
  the end of each file (e.g., '.mp3.chroma')
  '''
  matches = {}
  for query in res :
    qid = os.path.basename(query)[:-len(appendext)]
    for cs in gt : # for each coverset
      if qid in cs : # found the coverset
        ranklist = res[query]
        linecounter = 1
        qmatches = []
        for rline in ranklist :
          mid = os.path.basename(rline[0])[:-len(appendext)]
          if mid != qid and mid in cs :
            qmatches.append(linecounter)
          if mid != qid :
            linecounter += 1
        matches[query] = qmatches
  return matches

if __name__ == '__main__':
  gt = parsegtfile(open(sys.argv[1]))
  res = parseres(sys.stdin)
  matches = computeMatches(res,gt,'.mp3.chroma')
  for m in matches :
    print(m, matches[m])
