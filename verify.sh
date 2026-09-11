#!/usr/bin/env bash
#
# Everything CI would check, on your own machine, for free.
#
# Run this before you push and before you cut a release. It is the same set of checks the
# GitHub workflow runs, in the same order, so nothing is a surprise later:
#
#   1. the library's own tests
#   2. the three artifacts JitPack will build and serve
#   3. the tests for the version the next release will use
#
#   ./verify.sh
#
set -euo pipefail
cd "$(dirname "$0")"

echo "==> 1/3  Tests"
./mvnw --batch-mode --no-transfer-progress verify

echo
echo "==> 2/3  The artifacts JitPack will build"
./mvnw --batch-mode --no-transfer-progress -Prelease -DskipTests package -q
version=$(./mvnw --batch-mode --no-transfer-progress help:evaluate -Dexpression=project.version -q -DforceStdout)
for suffix in "" "-sources" "-javadoc"; do
  file="target/saddad-common-${version}${suffix}.jar"
  if [ -f "$file" ]; then
    echo "    built   ${file}"
  else
    echo "    MISSING ${file}"
    exit 1
  fi
done

echo
echo "==> 3/3  Release automation tests"
python3 .github/scripts/test_next_version.py 2>&1 | tail -3

echo
echo "Done. Push to main and this publishes itself - the commit messages decide the version."
