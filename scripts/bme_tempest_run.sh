if [[ -n $1 || -n $2 ]]; then
	echo "usage: bme_tempest_run.sh {smoke|persistent} <DIR>"
	exit
fi
cd ${2} 
pip install -r requirements.txt 
testr init
stream_id=$(cat .testrepository/next-stream)
ostestr --no-slowest --regex ${1}
mkdir -p subunit/smoke
cp .testrepository/${stream_id} subunit/${1}
