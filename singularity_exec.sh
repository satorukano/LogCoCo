#!/bin/bash
#SBATCH --mem=512G
module load singularity
singularity exec maven_3.9.9-eclipse-temurin-8.sif ./run.sh