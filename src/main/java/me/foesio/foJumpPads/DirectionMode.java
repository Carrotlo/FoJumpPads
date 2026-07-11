package me.foesio.foJumpPads;

import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Locale;

public enum DirectionMode {
    PLAYER_FACING("Player Facing", null),
    NORTH("North", new Vector(0, 0, -1)),
    EAST("East", new Vector(1, 0, 0)),
    SOUTH("South", new Vector(0, 0, 1)),
    WEST("West", new Vector(-1, 0, 0)),
    UPWARD_ONLY("Upward Only", new Vector(0, 0, 0));

    private final String displayName;
    private final Vector fixedDirection;

    DirectionMode(String displayName, Vector fixedDirection) {
        this.displayName = displayName;
        this.fixedDirection = fixedDirection;
    }

    public String displayName() {
        return displayName;
    }

    public Vector fixedDirection() {
        return fixedDirection == null ? null : fixedDirection.clone();
    }

    public DirectionMode next() {
        DirectionMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DirectionMode parse(String value) {
        if (value == null || value.isBlank()) {
            return PLAYER_FACING;
        }
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
                .filter(mode -> mode.name().equals(normalized))
                .findFirst()
                .orElse(PLAYER_FACING);
    }
}
