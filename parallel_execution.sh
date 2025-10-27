#!/bin/bash

#SBATCH --time=4:00:00
#SBATCH --partition=cluster_short
#SBATCH --ntasks=1
#SBATCH --output=logs/make-list_%A_%a.out
#SBATCH --error=errors/make-list_%A_%a.err
#SBATCH --array=1-74
#SBATCH --mem=64G
#SBATCH --cpus-per-task=10

module load singularity
TARGET_ROOT_DIR="/work/satoru-k/projects/LogCoCo/output/druid44"
LEAF_DIRECTORY_FILE_PATH="$TARGET_ROOT_DIR/leaf_directories.txt"
TARGET_DIR=$(sed -n "${SLURM_ARRAY_TASK_ID}p" "$LEAF_DIRECTORY_FILE_PATH")
TARGET_MODULE="org.apache.druid"
PROJECT="druid"
singularity exec Java_17.sif java -Dlog4j2.debug=false -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -jar ./target/logcoco-preprocesslog-1.0-SNAPSHOT.jar "$PROJECT" "${TARGET_DIR}/log.txt" "${TARGET_DIR}" "${TARGET_ROOT_DIR}/outputClass.txt"
singularity exec Java_17.sif java -Xms8G -Xmx64G -Dlog4j2.debug=false -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -jar ./target/logcoco-mainparser-1.0-SNAPSHOT.jar "${TARGET_ROOT_DIR}/outputClass.txt" "${TARGET_DIR}/process_log.txt" "${TARGET_ROOT_DIR}/outputContainLogMethodList.txt" "${TARGET_DIR}/coverage.csv" "$TARGET_MODULE" "$PROJECT"
