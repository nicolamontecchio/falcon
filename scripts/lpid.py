#!/usr/bin/env python3
'''
Given a chroma file containing the contents of an LP,
identify all the tracks therein using Falcon.
Parameters:
- path to the LP chroma file
- path to the collection chroma files OR an index
- length/overlap of LP chunks
- length/overlap of track segments (the usual for coverID)
- transpositions
'''
import argparse, random, os, functools
import chromachunker, evaluation

def ranklist_score(l) :
    LEN = 4
    ll = list(filter(lambda x: len(x) == LEN, [l[i:min(len(l),i+LEN)] for i in range(len(l))]))
    ss = []
    for l in ll :
        s = 0
        for x in l :
            s += 1/(max(x,3)**2) if x else 1/(1000**2)
        ss.append(s)
    return max(ss)


if __name__ == '__main__':
    # PARSE COMMAND LINE
    parser = argparse.ArgumentParser(description='identify tracks in an LP disc')
    parser.add_argument('--lp-chroma-file', help='path to chroma file for LP', required=True)
    collgroup = parser.add_mutually_exclusive_group(required=True)
    collgroup.add_argument('--collection-index', help='path to the already indexed collection')
    collgroup.add_argument('--collection-chromas', help='path to the chroma collection (to be indexed)')
    parser.add_argument('--lp-chunk-length', type=int, help='LP chunk length', default=1800)
    parser.add_argument('--lp-chunk-overlap', type=int, help='LP chunk overlap', default=1200)
    parser.add_argument('--segment-length', help='segment length', default=300)
    parser.add_argument('--segment-overlap', help='segment overlap', default=150)
    parser.add_argument('--transpositions', help='number of transpositions', default=None)
    parser.add_argument('--subsampling', help='subsampling of chroma files', default=2)
    args = parser.parse_args()
    # working directory
    working_directory = 'lpid_tmp_%d' % random.randint(0,1000000)
    #working_directory = 'lpid_tmp_185518'
    if not os.path.exists(working_directory):
        os.makedirs(working_directory)
    # divide chroma file into chunks
    chromachunker.chunkfile(args.lp_chroma_file,working_directory,args.lp_chunk_length,args.lp_chunk_overlap)
    # make index if necessary
    collindex = args.collection_index if args.collection_index else os.path.join(working_directory,'collection_index')
    if not args.collection_index :
        indexing_cmd = 'java -jar dist/falcon.jar -i %s -l %d -o %d %s -s %d %s' % (
            args.collection_chromas, args.segment_length, args.segment_overlap, 
            ('-t %d' % args.transpositions if args.transpositions  else ''),
            args.subsampling, collindex)
        #print(indexing_cmd)
        os.system(indexing_cmd)
        # do all queries
    qlistfile_path = os.path.join(working_directory, 'querylist.txt')
    qlistfile = open(qlistfile_path,'w')
    for qf in filter(lambda x : x.find('_chunk') > 0, os.listdir(working_directory)) :
        qlistfile.write('%s\n' % os.path.join(working_directory,qf))
    qlistfile.close()
    allqueriesres_path = os.path.join(working_directory, 'allqueries_res.txt')
    querying_cmd = 'java -jar dist/falcon.jar -l %d -o %d %s -s %d %s -b < %s > %s' % (
        args.segment_length, args.segment_overlap, 
        ('-t %d' % args.transpositions if args.transpositions  else ''),
        args.subsampling, collindex, qlistfile_path, allqueriesres_path)
    #print(querying_cmd)
    os.system(querying_cmd)
    # parse query results and produce final rank list
    res = evaluation.parseres(open(allqueriesres_path))
    queries = res.keys()
    allres = set(map(lambda x:x[0],functools.reduce(lambda x,y : x+y, [res[q] for q in queries])))
    restorank = {}
    for r in allres : restorank[r] = []
    for q in queries :
        usedres = []
        rank = 0
        for r in res[q] :
            usedres.append(r[0])
            rank += 1
            restorank[r[0]].append(rank)
        usedres=set(usedres)
        for r in allres :
            if r not in usedres :
                restorank[r].append(None)
    sortedallres = sorted(allres,key=lambda x : ranklist_score(restorank[x]), reverse=True)
    for res in sortedallres :
        print('[%6.4f] - %s' % (ranklist_score(restorank[res]),res))
