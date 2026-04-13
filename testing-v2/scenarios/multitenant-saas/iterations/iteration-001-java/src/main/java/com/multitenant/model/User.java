package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User entity within a tenant.
 * Stored with type = "user" in multitenant-data container.
 */
public class User extends BaseDocument {

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    @JsonProperty("role")
    private String role;

    public User() {
        setType("user");
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
