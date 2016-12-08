cd /opt/tempest_untagged/tempest
pip install -r .
testr init
stream_id=$(cat .testrepository/next-stream)
ostestr --no-slowest --regex smoke
mkdir -p subunit/smoke
cp .testrepository/${stream_id} subunit/smoke/results
