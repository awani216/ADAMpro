#!/bin/bash

export SPARK_MASTER_IP=10.34.58.136

bash /usr/local/spark/sbin/spark-config.sh

bash /usr/local/spark/bin/load-spark-env.sh

mkdir -p $SPARK_MASTER_LOG

/usr/local/spark/sbin/../bin/spark-class org.apache.spark.deploy.master.Master \
    --ip $SPARK_MASTER_IP --port $SPARK_MASTER_PORT --webui-port $SPARK_MASTER_WEBUI_PORT >> $SPARK_MASTER_LOG/spark-master.out
