#!/bin/bash

# Create models directory if it doesn't exist
mkdir -p vosk-model-cn

# Download small Chinese model
wget https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip

# Unzip the model
unzip vosk-model-small-cn-0.22.zip -d vosk-model-cn

# Clean up
rm vosk-model-small-cn-0.22.zip
