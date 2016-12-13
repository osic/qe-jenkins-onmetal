set -x 
if [[ -z $1 || -z $2 ]]; then
	echo "usage: bme_testsuite_run.sh [smoke|persistent-{clean,verify}] [DIR]"
	exit 1
fi

if [[ $1 = "smoke" ]]; then
  # smoke requires tempest to be installed, which doesn't follow persistent tests
	cd ${2}/..
	temp_dir=$(pwd)
  	keys="admin_password image_ref image_ref_alt uri uri_v3 public_network_id"
	for key in $keys
	do
		a="${key} ="
		sed -ir "s|${a}.*|${a}|g" ${temp_dir}/etc/tempest.conf.osa
		b=$(grep """${a}""" ${temp_dir}/etc/tempest.conf)
		sed -ir "s|${a}|${b}|g" ${temp_dir}/etc/tempest.conf.osa
	done

else
	cd ${2}
fi

pip install -r requirements.txt
if [[ ! -e .testrepository ]]; then
	testr init
fi
stream_id=$(cat .testrepository/next-stream)
ostestr --no-slowest --regex ${1}
mkdir -p subunit/${1}
if [[ -e .testrepository/${stream_id} ]]; then
	cp .testrepository/${stream_id} subunit/${1}/${1}.$(date +%s).results
fi
