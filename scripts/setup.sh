#!/usr/bin/env bash

##Author: Silvan Heller
## -b rebuilds the spark-base image
## -r is either WORKER or MASTER or SUBMIT
## -s and -h are in format ip:port and are used for the spark master and the hadoop namenode. use :7077 and :9000 please :)
## TODO Find better handling than getopts which only takes one char (Seriously?)

## This script assumes that in the folder where scripts/ is placed, you want to have a folder target/ where the conf-folder and the

#First check if the necessary things are installed
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
#sudo bash $DIR/install.sh

echo "Installing and updating done, cleaning existing images"

##########################
# Cleaning existing images
##########################
sudo rm -r target/
mkdir target/

#TODO Maybe not all should be deleted?

sudo docker stop spark-master
sudo docker rm spark-master
sudo docker rmi adampar/spark-master:1.6.2-hadoop2.6

sudo docker stop spark-submit
sudo docker rm spark-submit
sudo docker rmi adampar/spark-submit:1.6.2-hadoop2.6

sudo docker stop spark-worker
sudo docker rm spark-worker
sudo docker rmi adampar/spark-worker:1.6.2-hadoop2.6

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd ".." && pwd )"

echo "Parsing hadoop- and spark-vars"

#############################################
## Create Core-site.xml and SPARK_MASTER before building containers
## TODO If this is the way to go, how do we support multiple roles?
#############################################
while getopts ':h:s:r:b' option; do
  case $option in
    s)
      echo "-spark was triggered, Parameter: $OPTARG" >&2
      export SPARK_MASTER=$OPTARG
      ;;
    h)
        echo "-hadoop was triggered, Parameter: $OPTARG" >&2
        export HADOOP_NAMENODE=$OPTARG
      ;;

    r)
      echo "-role was triggered, Parameter: $OPTARG" >&2
      export ROLE=$OPTARG
      ;;
    b)
        echo "-base was triggered"
        export BASE="yes"
      ;;
    :)
      echo "Error, Option -$OPTARG requires an argument." >&2
      exit 1
      ;;
  esac
done

#####################
# Extract IP and Port
#####################
echo "Spark master should be set: $SPARK_MASTER, Hadoop Namenode should be set: $HADOOP_NAMENODE"
#TODO
IFS=':' read -r -a array <<< "$SPARK_MASTER"
SPARK_MASTER_IP=${array[0]}
SPARK_MASTER_PORT=${array[1]}

echo "Building Containers"

if [[ $BASE == "yes" ]]; then
    echo "rebuilding base container"
    sudo docker rmi adampar/spark-base:1.6.2-hadoop2.6
    sudo docker build -t adampar/spark-base:1.6.2-hadoop2.6 $DIR/scripts/docker-spark/base/
fi

## Builds Containers. The actual method call is below
## TODO Free ports based on MASTER_PORT
buildContainer() {
    echo "Container Building: $1"
    ROLE=$1

    if [ $ROLE == "MASTER" ]; then
        echo "building master containers, $SPARK_MASTER_IP:$SPARK_MASTER_PORT, $HADOOP_NAMENODE"
        #postgresql
        sudo docker stop postgresql
        sudo docker rm postgresql
        sudo docker run --net=host -p 5432:5432 -h postgresql --name postgresql -d orchardup/postgresql:latest

        # adampro
        sudo docker build -t adampar/spark-master:1.6.2-hadoop2.6 $DIR/scripts/docker-spark/master
        ##9000 is for HDFS
        # 8080, 7077 and 6066 are to listen to spark-submit / spark GUI
        # #8088, 8042 are legacy from the old code TODO are those necessary?
        #5890 is the grpc-port TODO Needed here?
        sudo docker run -d -e "SPARK_MASTER_PORT=$SPARK_MASTER_PORT" \
         -e "SPARK_MASTER_IP=$SPARK_MASTER_IP" \
         -e "HADOOP_NAMENODE=$HADOOP_NAMENODE" \
         -p 8080:8080 -p 9000:9000 -p 7077:7077 -p 6066:6066 -p 8088:8088 -p 8042:8042 \
         --net=host --hostname spark --name spark-master \
         adampar/spark-master:1.6.2-hadoop2.6
    fi

    if [ $ROLE == "WORKER" ]; then
        sudo docker build -t adampar/spark-worker:1.6.2-hadoop2.6 $DIR/scripts/docker-spark/worker
        #Ports: 8081 is the UI, 9000 the HDFS Port -> TODO Scale this based on Input
        # 7077, 6066 are for jobs, 4040 for the Job UI
        # 8088 and 8042 are legacy ports TODO
        sudo docker run -d \
         -e "SPARK_MASTER=spark://$SPARK_MASTER" \
         -e "HADOOP_NAMENODE=$HADOOP_NAMENODE" \
         -p 8081:8081 -p 9000:9000 -p 7077:7077 -p 6066:6066 -p 4040:4040 -p 8088:8088 -p 8042:8042 \
         --net=host --name spark-worker -h spark-worker \
         adampar/spark-worker:1.6.2-hadoop2.6
    fi

    if [ $ROLE == "SUBMIT" ]; then
        echo "building submit containers"
        sudo docker build -t adampar/spark-submit:1.6.2-hadoop2.6 $DIR/scripts/docker-spark/submit

        # TODO Try which ports are necessary
        # TODO Make spark-submit great again. Multiple things:
        # 1) extract from submit.sh to an exec-thing - that allows the dev to control deploy-mode and such in script
        # 5890 is the grpc-Port if you run the .jar on the submit-container
        # 50543, 8087, 8089 and 47957 are needed to communicate with the workers
        # 4040 is the Apllication UI
        sudo docker run -d -v $DIR/target:/target -v $DIR/target/conf/log4j.properties:/usr/local/spark/conf/log4j.properties \
        -e "HADOOP_NAMENODE=$HADOOP_NAMENODE" \
         -p 50543:50543 -p 8087:8087 -p 47957:47957 -p 4040:4040  -p 8089:8089 -p 5890:5890 \
         --net=host --name spark-submit -h spark-submit \
         adampar/spark-submit:1.6.2-hadoop2.6
    fi

}


buildContainer $ROLE