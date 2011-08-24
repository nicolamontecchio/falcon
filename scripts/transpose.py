#!/usr/bin/env python3
# Transpose chroma matrix by nTransp semitones up (right rotation) where nTransp is 1st argument.
# If two additional arguments are present, those are input and output file paths, respectively.
# Otherwise, read/write on STDIN
import sys, csv

if __name__ == '__main__':
  ntransp = (int(sys.argv[1]) + 12) % 12
  instream = sys.stdin
  outstream = sys.stdout
  if len(sys.argv) == 4 :
    instream = open(sys.argv[2],'r')
    outstream = open(sys.argv[3],'w')
  reader = csv.reader(instream)
  writer = csv.writer(outstream)
  for line in reader :
    outline = line[-ntransp:] + line[:-ntransp]
    writer.writerow(outline)
  if len(sys.argv) == 4 :
    outstream.close()
