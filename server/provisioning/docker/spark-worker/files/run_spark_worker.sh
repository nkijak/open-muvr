#!/bin/bash
. /opt/spark-1.2.0/conf/spark-env.sh
${SPARK_HOME}/bin/spark-class org.apache.spark.deploy.worker.Worker spark://$SPARKMASTER_PORT_7077_TCP_ADDR:$SPARKMASTER_PORT_7077_TCP_PORT