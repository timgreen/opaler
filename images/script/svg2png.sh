#!/bin/bash

convert() {
  INPUT_FILE=$1
  OUTPUT_FILE=$2
  nw=$3

  gimp -i --batch-interpreter=python-fu-eval -b - << EOF
import gimpfu

def convert(filename):
    img = pdb.gimp_file_load(filename, filename)
    w = img.width
    h = img.height
    nw = $nw
    nh = nw * h / w
    new_name = '${OUTPUT_FILE}'
    layer = pdb.gimp_image_merge_visible_layers(img, 1)
    layer.scale(nw, nh)
    pdb.gimp_file_save(img, layer, new_name, new_name)
    pdb.gimp_image_delete(img)

convert('${INPUT_FILE}')

pdb.gimp_quit(1)
EOF
}

input="$1"
output="$2"
w="$3"
convert "$input" "$output" "$w"
