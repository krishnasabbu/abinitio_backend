-- Workflows Domain
CREATE TABLE IF NOT EXISTS workflows (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  workflow_data TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Executions Domain
CREATE TABLE IF NOT EXISTS workflow_executions (
  id VARCHAR(64) PRIMARY KEY,
  execution_id VARCHAR(64) UNIQUE NOT NULL,
  workflow_name VARCHAR(255) NOT NULL,
  workflow_id VARCHAR(64),
  status VARCHAR(20) NOT NULL,
  start_time BIGINT,
  end_time BIGINT,
  total_nodes INTEGER,
  completed_nodes INTEGER,
  successful_nodes INTEGER,
  failed_nodes INTEGER,
  total_records BIGINT,
  total_execution_time_ms BIGINT,
  error_message TEXT,
  execution_mode VARCHAR(50),
  workflow_version VARCHAR(50),
  parameters TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_executions_workflow_id ON workflow_executions(workflow_id);
CREATE INDEX IF NOT EXISTS idx_executions_status ON workflow_executions(status);
CREATE INDEX IF NOT EXISTS idx_executions_start_time ON workflow_executions(start_time DESC);

-- Node Executions Domain
CREATE TABLE IF NOT EXISTS node_executions (
  id VARCHAR(64) PRIMARY KEY,
  execution_id VARCHAR(64) NOT NULL,
  node_id VARCHAR(64) NOT NULL,
  node_label VARCHAR(255),
  node_type VARCHAR(100),
  status VARCHAR(20) NOT NULL,
  start_time BIGINT,
  end_time BIGINT,
  execution_time_ms BIGINT,
  records_processed BIGINT,
  retry_count INTEGER DEFAULT 0,
  error_message TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (execution_id) REFERENCES workflow_executions(execution_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_node_exec_execution_id ON node_executions(execution_id);
CREATE INDEX IF NOT EXISTS idx_node_exec_node_id ON node_executions(node_id);

-- Logs Domain
CREATE TABLE IF NOT EXISTS execution_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  timestamp BIGINT NOT NULL,
  datetime VARCHAR(50),
  level VARCHAR(20) NOT NULL,
  execution_id VARCHAR(64) NOT NULL,
  workflow_id VARCHAR(64),
  node_id VARCHAR(64),
  message TEXT NOT NULL,
  metadata TEXT,
  stack_trace TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_logs_execution_id ON execution_logs(execution_id);
CREATE INDEX IF NOT EXISTS idx_logs_level ON execution_logs(level);
CREATE INDEX IF NOT EXISTS idx_logs_timestamp ON execution_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_logs_node_id ON execution_logs(node_id);

-- System Resources Domain
CREATE TABLE IF NOT EXISTS system_resource_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  timestamp BIGINT NOT NULL,
  cpu_percent DECIMAL(5,2),
  cpu_count INTEGER,
  cpu_count_logical INTEGER,
  memory_total_mb BIGINT,
  memory_available_mb BIGINT,
  memory_used_mb BIGINT,
  memory_percent DECIMAL(5,2),
  disk_total_gb BIGINT,
  disk_used_gb BIGINT,
  disk_free_gb BIGINT,
  disk_percent DECIMAL(5,2),
  network_bytes_sent BIGINT,
  network_bytes_recv BIGINT,
  network_packets_sent BIGINT,
  network_packets_recv BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_snapshots_timestamp ON system_resource_snapshots(timestamp DESC);

-- Execution Resources Domain
CREATE TABLE IF NOT EXISTS execution_resource_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  execution_id VARCHAR(64) NOT NULL,
  timestamp BIGINT NOT NULL,
  cpu_percent DECIMAL(5,2),
  memory_mb BIGINT,
  disk_io_read_mb BIGINT,
  disk_io_write_mb BIGINT,
  network_bytes_sent BIGINT,
  network_bytes_recv BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (execution_id) REFERENCES workflow_executions(execution_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_exec_resource_execution_id ON execution_resource_snapshots(execution_id);
CREATE INDEX IF NOT EXISTS idx_exec_resource_timestamp ON execution_resource_snapshots(timestamp);

-- Node Resources Domain
CREATE TABLE IF NOT EXISTS node_resource_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  execution_id VARCHAR(64) NOT NULL,
  node_id VARCHAR(64) NOT NULL,
  timestamp BIGINT NOT NULL,
  cpu_percent DECIMAL(5,2),
  memory_mb BIGINT,
  disk_io_read_mb BIGINT,
  disk_io_write_mb BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_node_resource_execution_id ON node_resource_snapshots(execution_id);
CREATE INDEX IF NOT EXISTS idx_node_resource_node_id ON node_resource_snapshots(node_id);

-- Database Connections Domain
CREATE TABLE IF NOT EXISTS database_connections (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  connection_type VARCHAR(50) NOT NULL,
  host VARCHAR(255) NOT NULL,
  port INTEGER NOT NULL,
  database VARCHAR(255) NOT NULL,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(500) NOT NULL,
  ssl_enabled BOOLEAN DEFAULT FALSE,
  ssl_cert TEXT,
  additional_params TEXT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Kafka Connections Domain
CREATE TABLE IF NOT EXISTS kafka_connections (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  bootstrap_servers VARCHAR(1000) NOT NULL,
  security_protocol VARCHAR(50) NOT NULL,
  sasl_mechanism VARCHAR(50),
  sasl_username VARCHAR(255),
  sasl_password VARCHAR(500),
  ssl_cert TEXT,
  ssl_key TEXT,
  consumer_group VARCHAR(255),
  additional_config TEXT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- System Status Domain
CREATE TABLE IF NOT EXISTS system_status (
  id INTEGER PRIMARY KEY DEFAULT 1,
  engine_status VARCHAR(50) NOT NULL,
  scheduler_status VARCHAR(50) NOT NULL,
  timestamp BIGINT NOT NULL,
  last_heartbeat BIGINT NOT NULL,
  total_workflows INTEGER DEFAULT 0,
  running_executions INTEGER DEFAULT 0,
  supported_modes VARCHAR(500),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default system status
INSERT INTO system_status (id, engine_status, scheduler_status, timestamp, last_heartbeat, supported_modes)
VALUES (1, 'running', 'active', 0, 0, 'python,parallel,pyspark')
ON CONFLICT DO NOTHING;
