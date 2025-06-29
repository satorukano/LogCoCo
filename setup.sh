#!/bin/bash

module load singularity
PROJECT_PATH="/work/satoru-k/projects/hbase"
TARGET_ROOT_PATH="/work/satoru-k/projects/LogCoCo/output/hbase29"
singularity exec LogCoCo_Java8.sif java -jar ./target/logcoco-findfileofclass-1.0-SNAPSHOT.jar "$PROJECT_PATH" "${TARGET_ROOT_PATH}/outputClass.txt" /opt/java/openjdk/jre/lib
singularity exec LogCoCo_Java8.sif java -jar ./target/logcoco-extractmethodcalmap-1.0-SNAPSHOT.jar "$PROJECT_PATH" "${TARGET_ROOT_PATH}/outputClass.txt" /opt/java/openjdk/jre/lib "${TARGET_ROOT_PATH}/outputContainLogMethodList.txt" "${TARGET_ROOT_PATH}/invoke_method.txt"