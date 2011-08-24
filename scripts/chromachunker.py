#!/usr/bin/env python3
import argparse, os

def chunkfile(infile,outdir,length,overlap) :
  ''' divide a file into chunks '''
  #print('chunkfile called with %s %s %d %d' % (infile, outdir, length, overlap))
  lines = [line for line in open(infile)]
  hopsize = length-overlap
  chunkcounter = 1
  while len(lines) > hopsize:
    fout = open(os.path.join(outdir,'%s_chunck%05d' % (os.path.basename(infile),chunkcounter)),'w')
    for l in lines[:min(len(lines),length)] :
      fout.write(l)
    lines = lines[min(len(lines),hopsize):]
    chunkcounter += 1
  fout.close()

if __name__ == '__main__':
  parser = argparse.ArgumentParser(description='chunk chroma files into multiple segments')
  parser.add_argument('-l', type=int, help='chunk length', required=True)
  parser.add_argument('-o', type=int, help='chunk overlap', required=True)
  parser.add_argument('--input', nargs='+', help='input files (or directories)', required=True)
  parser.add_argument('--output', help='output directory', required=True)
  args = parser.parse_args()
  for ff in args.input:
    if os.path.isdir(ff) :
      for f in filter(lambda x : x.find('.') != 0, os.listdir(ff)) :
        chunkfile(f,args.output,args.l,args.o)
    else :
      chunkfile(ff,args.output,args.l,args.o)








