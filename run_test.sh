#!/bin/bash

echo "================================================================================"
echo "DYNAMIC JOB BUILDER - WORKFLOW TEST RUNNER"
echo "================================================================================"
echo ""

if [ -z "$1" ]; then
    echo "Usage: ./run_test.sh <workflow-json-file>"
    echo "Example: ./run_test.sh test1_simple.json"
    exit 1
fi

WORKFLOW_FILE="$1"

if [ ! -f "$WORKFLOW_FILE" ]; then
    echo "ERROR: File not found: $WORKFLOW_FILE"
    exit 1
fi

echo "Workflow File: $WORKFLOW_FILE"
echo "================================================================================\n"

curl -X POST http://localhost:8080/api/workflows/execute \
  -H "Content-Type: application/json" \
  -d "@$WORKFLOW_FILE"

echo "\n\n================================================================================"
echo "Test execution completed"
echo "================================================================================"
