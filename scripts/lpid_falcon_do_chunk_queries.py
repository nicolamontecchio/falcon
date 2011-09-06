#!/usr/bin/env python3
'''
Given a chroma file containing the contents of an LP,
use falcon to query overlapping segments of the LP
against an already indexed collection.
Parameters:
- path to the LP chroma file
- path to the collection chroma files OR an index
- length/overlap of LP chunks
- length/overlap of track segments (the usual for coverID)
- transpositions
'''
import argparse, random, os, functools, shutil
import chromachunker, evaluation

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='divide LP into segments and query each chunk')
    parser.add_argument('--lp-chroma-file', help='path to chroma file for LP', required=True)
    parser.add_argument('--collection-index', help='path to the already indexed collection', required=True)
    parser.add_argument('--lp-chunk-length', type=int, help='LP chunk length', default=1800)
    parser.add_argument('--lp-chunk-overlap', type=int, help='LP chunk overlap', default=1200)
    parser.add_argument('--segment-length', help='segment length', default=300)
    parser.add_argument('--segment-overlap', help='segment overlap', default=200)
    collgroup2 = parser.add_mutually_exclusive_group(required=False)
    collgroup2.add_argument('--transpositions', help='number of transpositions', default=None)
    collgroup2.add_argument('--force-transposition', help='force a specific transposition in semitones', default=None)
    parser.add_argument('--subsampling', help='subsampling of chroma files', default=2)
    args = parser.parse_args()
    # working directory ends with the lp id
    working_directory = 'lpid_tmp_%s' % os.path.basename(args.lp_chroma_file)
    if working_directory.find('.') > 0 : working_directory = working_directory[:working_directory.find('.')] # rm extension from dir 
    if not os.path.exists(working_directory): os.makedirs(working_directory) # make if needed
    # divide chroma file into chunks
    chromachunker.chunkfile(args.lp_chroma_file,working_directory,args.lp_chunk_length,args.lp_chunk_overlap)
    # do all queries
    qlistfile_path = os.path.join(working_directory, 'querylist.txt')
    qlistfile = open(qlistfile_path,'w')
    for qf in filter(lambda x : x.find('_chunk') > 0, os.listdir(working_directory)) :
        qlistfile.write('%s\n' % os.path.join(working_directory,qf))
    qlistfile.close()
    allqueriesres_path = os.path.join(working_directory, 'allqueries_res.txt')
    transp_arg = ''
    if args.transpositions :
        transp_arg = '-t %d' % args.transpositions 
    elif args.force_transposition :
        transp_arg = '-f %s' % args.force_transposition 
    querying_cmd = 'java -jar dist/falcon.jar -l %d -o %d %s -s %d %s -b < %s' % (
        args.segment_length, args.segment_overlap, transp_arg,
        args.subsampling, args.collection_index, qlistfile_path)
    os.system(querying_cmd)
    shutil.rmtree(working_directory)

