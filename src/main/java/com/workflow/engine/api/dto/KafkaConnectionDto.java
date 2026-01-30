package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaConnectionDto {
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("bootstrap_servers")
    private String bootstrapServers;

    @JsonProperty("security_protocol")
    private String securityProtocol;

    @JsonProperty("sasl_mechanism")
    private String saslMechanism;

    @JsonProperty("sasl_username")
    private String saslUsername;

    @JsonProperty("sasl_password")
    private String saslPassword;

    @JsonProperty("ssl_cert")
    private String sslCert;

    @JsonProperty("ssl_key")
    private String sslKey;

    @JsonProperty("consumer_group")
    private String consumerGroup;

    @JsonProperty("additional_config")
    private Object additionalConfig;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    public KafkaConnectionDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }

    public String getSecurityProtocol() { return securityProtocol; }
    public void setSecurityProtocol(String securityProtocol) { this.securityProtocol = securityProtocol; }

    public String getSaslMechanism() { return saslMechanism; }
    public void setSaslMechanism(String saslMechanism) { this.saslMechanism = saslMechanism; }

    public String getSaslUsername() { return saslUsername; }
    public void setSaslUsername(String saslUsername) { this.saslUsername = saslUsername; }

    public String getSaslPassword() { return saslPassword; }
    public void setSaslPassword(String saslPassword) { this.saslPassword = saslPassword; }

    public String getSslCert() { return sslCert; }
    public void setSslCert(String sslCert) { this.sslCert = sslCert; }

    public String getSslKey() { return sslKey; }
    public void setSslKey(String sslKey) { this.sslKey = sslKey; }

    public String getConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }

    public Object getAdditionalConfig() { return additionalConfig; }
    public void setAdditionalConfig(Object additionalConfig) { this.additionalConfig = additionalConfig; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
