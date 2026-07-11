package me.foesio.foJumpPads;

import me.foesio.core.material.MaterialTypes;
import org.bukkit.Material;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PadStorage implements AutoCloseable {

    private final FoJumpPads plugin;
    private final Connection connection;

    public PadStorage(FoJumpPads plugin) throws SQLException, ClassNotFoundException {
        this.plugin = plugin;
        Class.forName("org.sqlite.JDBC");
        File databaseFile = new File(plugin.getDataFolder(), "userdata.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS jump_pads (
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        preset TEXT NOT NULL,
                        material TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY (world, x, y, z)
                    )
                    """);
        }
        ensureMaterialColumn();
    }

    public Map<PadLocation, PadData> loadPads() throws SQLException {
        Map<PadLocation, PadData> pads = new HashMap<>();
        List<MaterialBackfill> backfills = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT world, x, y, z, preset, material FROM jump_pads");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                PadLocation location = new PadLocation(
                        resultSet.getString("world"),
                        resultSet.getInt("x"),
                        resultSet.getInt("y"),
                        resultSet.getInt("z")
                );
                String materialName = resultSet.getString("material");
                Material material = material(resultSet.getString("preset"), materialName);
                if (materialName == null || materialName.isBlank()) {
                    backfills.add(new MaterialBackfill(location, material));
                }
                pads.put(location, new PadData(resultSet.getString("preset"), material));
            }
        }
        for (MaterialBackfill backfill : backfills) {
            updateMaterial(backfill.location(), backfill.material());
        }
        return pads;
    }

    public void savePad(PadLocation location, PadData data) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO jump_pads(world, x, y, z, preset, material)
                VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(world, x, y, z) DO UPDATE SET preset = excluded.preset, material = excluded.material
                """)) {
            statement.setString(1, location.world());
            statement.setInt(2, location.x());
            statement.setInt(3, location.y());
            statement.setInt(4, location.z());
            statement.setString(5, data.presetId());
            statement.setString(6, data.material().name());
            statement.executeUpdate();
        }
    }

    public void deletePad(PadLocation location) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM jump_pads WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, location.world());
            statement.setInt(2, location.x());
            statement.setInt(3, location.y());
            statement.setInt(4, location.z());
            statement.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private void ensureMaterialColumn() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(jump_pads)");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if ("material".equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE jump_pads ADD COLUMN material TEXT NOT NULL DEFAULT ''");
        }
    }

    private Material material(String presetId, String materialName) {
        Material material = MaterialTypes.match(materialName);
        if (material != null && material.isBlock()) {
            return material;
        }
        JumpPadPreset preset = plugin.presets().get(presetId);
        return preset == null ? Material.LIGHT_WEIGHTED_PRESSURE_PLATE : preset.material();
    }

    private void updateMaterial(PadLocation location, Material material) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE jump_pads SET material = ? WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, material.name());
            statement.setString(2, location.world());
            statement.setInt(3, location.x());
            statement.setInt(4, location.y());
            statement.setInt(5, location.z());
            statement.executeUpdate();
        }
    }

    private record MaterialBackfill(PadLocation location, Material material) {
    }
}
