#!/usr/bin/env sh

# command sequence

# create directories
mkdir evaluation/falconhashes
mkdir evaluation/falconindex

# chroma conversion
java -jar dist/falcon.jar --conversion --dp evaluation/falconchroma --cd evaluation/falconhashes --nk

# index creation
java -jar dist/falcon.jar --indexing --dp evaluation/falconhashes/0/ --ip evaluation/falconindex --hps 150 --overlap 75

# querying
java -jar dist/falcon.jar --ip evaluation/falconindex --hps 150 --overlap 75 --qp evaluation/falconhashes/ --ranking --qfl evaluation/qlist.txt --nk > evaluation/results.txt

# evaluation
./scripts/evaluate.py evaluation/results.txt evaluation/gt.txt
