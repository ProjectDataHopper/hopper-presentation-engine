
PROJECT_FOLDER="$(pwd)/../hopper-presentation-project/demo"

docker run \
       --rm \
       -p 8080:8080 \
       --name hopper-presentation \
       -v "${PROJECT_FOLDER}:/hopper-data/config" \
       -v "${PROJECT_FOLDER}/metadata:/hopper-data/metadata" \
       -e HOPPER_REST_CONFIG_PATH=/hopper-data/config \
       -e HOPPER_METADATA_PATH=/hopper-data/metadata \
       projectdatahopper/presentation-engine:latest

