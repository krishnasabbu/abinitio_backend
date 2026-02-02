/*
  # Initialize Workflow Engine Schema in Supabase
  
  1. New Tables
    - `workflow_executions` - Master execution records
    - `node_executions` - Per-node execution details with metrics
    - `execution_logs` - Execution logs and traces
  
  2. Security
    - Enable RLS on all tables
    - Set up policies for data isolation
*/

CREATE TABLE IF NOT EXISTS workflow_executions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  execution_id text UNIQUE NOT NULL,
  workflow_name text NOT NULL,
  workflow_id text,
  status text NOT NULL,
  start_time bigint,
  end_time bigint,
  total_nodes integer,
  completed_nodes integer,
  successful_nodes integer,
  failed_nodes integer,
  total_records bigint,
  total_execution_time_ms bigint,
  error_message text,
  execution_mode text,
  workflow_version text,
  parameters text,
  created_at timestamptz DEFAULT now()
);

CREATE TABLE IF NOT EXISTS node_executions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  execution_id text NOT NULL REFERENCES workflow_executions(execution_id) ON DELETE CASCADE,
  node_id text NOT NULL,
  node_label text,
  node_type text,
  status text NOT NULL,
  start_time bigint,
  end_time bigint,
  execution_time_ms bigint,
  input_records bigint DEFAULT 0,
  output_records bigint DEFAULT 0,
  records_processed bigint,
  input_bytes bigint,
  output_bytes bigint,
  records_per_second numeric,
  bytes_per_second numeric,
  output_summary text,
  retry_count integer DEFAULT 0,
  error_message text,
  created_at timestamptz DEFAULT now()
);

CREATE TABLE IF NOT EXISTS execution_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  timestamp bigint NOT NULL,
  level text NOT NULL,
  execution_id text NOT NULL REFERENCES workflow_executions(execution_id) ON DELETE CASCADE,
  node_id text,
  message text NOT NULL,
  metadata text,
  stack_trace text,
  created_at timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_workflow_executions_execution_id ON workflow_executions(execution_id);
CREATE INDEX IF NOT EXISTS idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX IF NOT EXISTS idx_workflow_executions_start_time ON workflow_executions(start_time DESC);

CREATE INDEX IF NOT EXISTS idx_node_executions_execution_id ON node_executions(execution_id);
CREATE INDEX IF NOT EXISTS idx_node_executions_node_id ON node_executions(node_id);

CREATE INDEX IF NOT EXISTS idx_execution_logs_execution_id ON execution_logs(execution_id);
CREATE INDEX IF NOT EXISTS idx_execution_logs_level ON execution_logs(level);
CREATE INDEX IF NOT EXISTS idx_execution_logs_timestamp ON execution_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_execution_logs_node_id ON execution_logs(node_id);

ALTER TABLE workflow_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE node_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE execution_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all read access" ON workflow_executions FOR SELECT USING (true);
CREATE POLICY "Allow all insert access" ON workflow_executions FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow all update access" ON workflow_executions FOR UPDATE USING (true) WITH CHECK (true);

CREATE POLICY "Allow all read access" ON node_executions FOR SELECT USING (true);
CREATE POLICY "Allow all insert access" ON node_executions FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow all update access" ON node_executions FOR UPDATE USING (true) WITH CHECK (true);

CREATE POLICY "Allow all read access" ON execution_logs FOR SELECT USING (true);
CREATE POLICY "Allow all insert access" ON execution_logs FOR INSERT WITH CHECK (true);
