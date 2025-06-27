#!/bin/bash

module load singularity
singularity exec LogCoCo.sif java -jar ./target/logcoco-findfileofclass-1.0-SNAPSHOT.jar "$PROJECT_PATH" "${TARGET_ROOT_PATH}/outputClass.txt" /opt/java/openjdk/jre/lib
singularity exec LogCoCo.sif java -jar ./target/logcoco-fextractmethodcalmap-1.0-SNAPSHOT.jar "$PROJECT_PATH" "${TARGET_ROOT_PATH}/outputClass.txt" /opt/java/openjdk/jre/lib "${TARGET_ROOT_PATH}/outputContainLogMethodList.txt" "${TARGET_ROOT_PATH}/invoke_method.txt"