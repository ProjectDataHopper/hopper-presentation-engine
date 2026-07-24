

docker run \
       --rm \
       -p 8080:8080 \
       --name hopper-presentation \
       -v "$(pwd)/hopper-presentation-rest/config:/hopper-data/config" \
       -v "$(pwd)/hopper-presentation-rest/config/metadata:/hopper-data/metadata" \
       -e HOPPER_REST_CONFIG_PATH=/hopper-data/config \
       -e HOPPER_METADATA_PATH=/hopper-data/metadata \
       projectdatahopper/presentation-engine:latest

