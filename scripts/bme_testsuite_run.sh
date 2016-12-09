if [[ -n $1 || -n $2 ]]; then
	echo "usage: bme_testsuite_run.sh [smoke|persistent-{clean,verify}] [DIR]"
	exit
fi
cd ${2}
pip install -r requirements.txt 
testr init
stream_id=$(cat .testrepository/next-stream)
ostestr --no-slowest --regex ${1}
mkdir -p subunit/${1} 
cp .testrepository/${stream_id} subunit/${1}.results
