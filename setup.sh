#!/bin/bash

module load singularity
PROJECT="druid"
PROJECT_PATH="/work/satoru-k/projects/druid"
TARGET_ROOT_PATH="/work/satoru-k/projects/LogCoCo/output/druid44"
singularity exec LogCoCo_Java17.sif java -jar ./target/logcoco-findfileofclass-1.0-SNAPSHOT.jar "$PROJECT_PATH" "${TARGET_ROOT_PATH}/outputClass.txt" 
singularity exec LogCoCo_Java17.sif java -jar ./target/logcoco-extractmethodcallmap-1.0-SNAPSHOT.jar "$PROJECT_PATH" "${TARGET_ROOT_PATH}/outputClass.txt" "${TARGET_ROOT_PATH}/outputContainLogMethodList.txt" "${TARGET_ROOT_PATH}/invoke_method.txt" org.apache.druid