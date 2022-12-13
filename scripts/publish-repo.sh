#!/bin/bash

if [[ -z "$1" ]]; then
  echo "Script to copy files from current directory to the open-source repo directory."
  echo
  echo "Usage: publish-repo.sh [destination-dir]"
  echo
  exit 1
fi
# from https://stackoverflow.com/a/63438492/100752
rsync -vhra . $1 --include='**.gitignore' --exclude='/.git' --exclude='/.github' --filter=':- .gitignore' --delete-after
