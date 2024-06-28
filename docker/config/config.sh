#/bin/bash

RS_NAME=rs00
MASTER=mongo11
REPLICA_1=mongo22
REPLICA_2=mongo33

until mongosh --host $MASTER --port 27117 --quiet <<EOF
exit
EOF
do
  sleep 5
done

mongosh --host $MASTER --port 27117 --quiet <<EOF
rs.initiate(
  {
     _id: "$RS_NAME",
     version: 1,
     members: [
        { _id: 0, host : "${MASTER}:27117", priority: 2 },
        { _id: 1, host : "${REPLICA_1}:27217", priority: 1 },
        { _id: 2, host : "${REPLICA_2}:27317", priority: 1 }
     ]
  }
)
EOF
