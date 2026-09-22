package com.integrate.crm.model;

public enum Role {

    THERAPIST(1),
    CLIENT(0),
    ADMIN(2);

    private final int roleScore;

    Role(int roleScore) {
        this.roleScore = roleScore;
    }

    public int getRoleScore() {
        return roleScore;
    }

    public static Role fromRoleScore(int roleScore) {
        for (Role role : Role.values()) {
            if (role.getRoleScore() == roleScore) {
                return role;
                // Found the matching role, return it
            }
        }
        throw new IllegalArgumentException("Invalid role score: " + roleScore);
    }
}
