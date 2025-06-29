#!/bin/bash
#SBATCH --job-name=LogCoCo
#SBATCH --output=logs/make-list_%A_%a.out
#SBATCH --error=errors/make-list_%A_%a.err
#SBATCH --array=1-589
#SBATCH --time=4:00:00
#SBATCH --partition=cluster_short
#SBATCH --ntasks=1
#SBATCH --mem=64G
#SBATCH --cpus-per-task=10

module load singularity
TARGET_ROOT_DIR="/work/satoru-k/projects/LogCoCo/output/hbase29"
LEAF_DIRECTORY_FILE_PATH="$TARGET_ROOT_DIR/leaf_directories.txt"
TARGET_DIR=$(sed -n "${SLURM_ARRAY_TASK_ID}p" "$LEAF_DIRECTORY_FILE_PATH")
TARGET_MODULE="org.apache.hadoop"
RT_JAR="/opt/java/openjdk/jre/lib"
singularity exec LogCoCo_Java8.sif java -jar ./target/logcoco-preprocesslog-1.0-SNAPSHOT.jar "$TARGET_MODULE" "${TARGET_DIR}/log.txt" "${TARGET_DIR}"
singularity exec LogCoCo_Java8.sif java -Xms8G -Xmx64G -jar ./target/logcoco-mainparser-1.0-SNAPSHOT.jar "${TARGET_ROOT_DIR}/outputClass.txt" "${TARGET_DIR}/process_log.txt" "${TARGET_ROOT_DIR}/outputContainLogMethodList.txt" "${TARGET_DIR}/coverage.csv" "$RT_JAR" "$TARGET_MODULE"
