package ru.ap4uuk.coreprotect.storage;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.ap4uuk.coreprotect.Coreprotect;
import ru.ap4uuk.coreprotect.model.BlockAction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class DatabaseManager {

    private static DatabaseManager INSTANCE;

    private final Connection connection;
    private final PreparedStatement insertBlockStmt;
    private final BlockingQueue<BlockAction> queue = new LinkedBlockingQueue<>(5000);
    private Thread writerThread;
    private volatile boolean running = true;

    private DatabaseManager(Connection connection) throws SQLException {
        this.connection = connection;
        this.connection.setAutoCommit(false);
        createTables();
        this.insertBlockStmt = prepareInsertStatement();
        startWriterThread();
    }

    public static synchronized void initSQLite(Path dbFile) {
        if (INSTANCE != null) {
            Coreprotect.LOGGER.warn("[Coreprotect] DatabaseManager уже инициализирован.");
            return;
        }

        try {
            if (dbFile.getParent() != null) {
                Files.createDirectories(dbFile.getParent());
            }

            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            Coreprotect.LOGGER.info("[Coreprotect] Подключаемся к SQLite по URL: {}", url);

            initSql(List.of(
                    "ru.ap4uuk.coreprotect.shaded.org.sqlite.JDBC",
                    "org.sqlite.JDBC"
            ), url, null, null, 1);

            if (INSTANCE != null) {
                Coreprotect.LOGGER.info("[Coreprotect] SQLite инициализирован: {}", dbFile.toAbsolutePath());
            }
        } catch (Exception e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка инициализации SQLite", e);
            INSTANCE = null;
        }
    }

    public static synchronized void initSql(List<String> driverClassNames,
                                            String jdbcUrl,
                                            String username,
                                            String password,
                                            int connectionPoolSize) {
        if (INSTANCE != null) {
            Coreprotect.LOGGER.warn("[Coreprotect] DatabaseManager уже инициализирован.");
            return;
        }

        try {
            boolean driverOk = false;
            String activeDriver = null;

            for (String driverClassName : driverClassNames) {
                if (driverClassName == null || driverClassName.isBlank()) continue;
                try {
                    Class.forName(driverClassName.trim());
                    driverOk = true;
                    activeDriver = driverClassName.trim();
                    Coreprotect.LOGGER.info("[Coreprotect] Драйвер найден: {}", activeDriver);
                    break;
                } catch (ClassNotFoundException e) {
                    Coreprotect.LOGGER.debug("[Coreprotect] Драйвер {} не найден в classpath.", driverClassName);
                }
            }

            if (!driverOk) {
                Coreprotect.LOGGER.error("[Coreprotect] Ни один из указанных JDBC-драйверов не найден: {}", driverClassNames);
                return;
            }

            Connection conn;
            if (username != null && !username.isBlank()) {
                conn = DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
            } else {
                conn = DriverManager.getConnection(jdbcUrl);
            }

            if (conn == null) {
                Coreprotect.LOGGER.error("[Coreprotect] DriverManager вернул null при подключении.");
                return;
            }

            Coreprotect.LOGGER.info("[Coreprotect] Размер пула подключений: {}", connectionPoolSize);

            INSTANCE = new DatabaseManager(conn);
            Coreprotect.LOGGER.info("[Coreprotect] SQL подключение установлено по URL: {} (driver: {})", jdbcUrl, activeDriver);
        } catch (Exception e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка инициализации SQL подключения", e);
            INSTANCE = null;
        }
    }

    public static DatabaseManager get() {
        return INSTANCE;
    }

    public static synchronized void shutdown() {
        if (INSTANCE == null) return;
        try {
            INSTANCE.stopWriterThread();
            INSTANCE.close();
        } catch (Exception e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка при закрытии БД", e);
        } finally {
            INSTANCE = null;
        }
    }

    private void startWriterThread() {
        writerThread = new Thread(() -> {
            Coreprotect.LOGGER.info("[Coreprotect] Writer thread запущен.");
            final int batchSize = 100;

            while (running) {
                try {
                    BlockAction first = queue.take();
                    int count = 0;
                    do {
                        writeBlockActionInternal(first);
                        count++;
                        if (count >= batchSize) break;

                        BlockAction next = queue.poll();
                        if (next == null) break;
                        first = next;
                    } while (true);

                    connection.commit();
                } catch (InterruptedException e) {
                    if (!running) break;
                } catch (SQLException e) {
                    Coreprotect.LOGGER.error("[Coreprotect] Ошибка batch-записи событий в БД", e);
                    try {
                        connection.rollback();
                    } catch (SQLException ex) {
                        Coreprotect.LOGGER.error("[Coreprotect] Ошибка rollback в writer thread", ex);
                    }
                }
            }

            try {
                while (!queue.isEmpty()) {
                    BlockAction action = queue.poll();
                    if (action == null) break;
                    writeBlockActionInternal(action);
                }
                connection.commit();
            } catch (Exception e) {
                Coreprotect.LOGGER.error("[Coreprotect] Ошибка финальной записи очереди", e);
            }

            Coreprotect.LOGGER.info("[Coreprotect] Writer thread остановлен.");
        }, "Coreprotect-DB-Writer");

        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void stopWriterThread() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(5000);
            } catch (InterruptedException ignored) {}
        }
    }

    private void writeBlockActionInternal(BlockAction action) throws SQLException {
        insertBlockStmt.clearParameters();

        long epochSeconds = action.time.toEpochMilli() / 1000L;

        insertBlockStmt.setLong(1, epochSeconds);
        insertBlockStmt.setString(2, action.playerUuid.toString());
        insertBlockStmt.setString(3, action.playerName);
        insertBlockStmt.setString(4, action.dimension.location().toString());
        insertBlockStmt.setInt(5, action.pos.getX());
        insertBlockStmt.setInt(6, action.pos.getY());
        insertBlockStmt.setInt(7, action.pos.getZ());
        insertBlockStmt.setString(8, action.type.name());

        insertBlockStmt.setString(9, serializeActionState(action.oldState, action.oldStateText));
        insertBlockStmt.setString(10, serializeActionState(action.newState, action.newStateText));

        insertBlockStmt.executeUpdate();
    }

    public static final class DbBlockAction {
        public final long timeEpoch;
        public final String playerName;
        public final String actionType;
        public final String oldBlock;
        public final String newBlock;

        public DbBlockAction(long timeEpoch, String playerName, String actionType, String oldBlock, String newBlock) {
            this.timeEpoch = timeEpoch;
            this.playerName = playerName;
            this.actionType = actionType;
            this.oldBlock = oldBlock;
            this.newBlock = newBlock;
        }
    }

    public record DbLookupResult(long timeEpoch,
                                 String playerName,
                                 String actionType,
                                 String oldBlock,
                                 String newBlock,
                                 ResourceKey<Level> dimension,
                                 int x,
                                 int y,
                                 int z) {}

    public synchronized List<DbBlockAction> getBlockHistory(ResourceKey<Level> dimension,
                                                            BlockPos pos,
                                                            int limit,
                                                            int offset) {
        List<DbBlockAction> result = new ArrayList<>();
        if (connection == null) return result;

        String sql = """
            SELECT time_epoch, player_name, action_type, old_block, new_block
            FROM block_actions
            WHERE dimension = ? AND x = ? AND y = ? AND z = ?
            ORDER BY time_epoch DESC
            LIMIT ? OFFSET ?;
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, dimension.location().toString());
            ps.setInt(2, pos.getX());
            ps.setInt(3, pos.getY());
            ps.setInt(4, pos.getZ());
            ps.setInt(5, limit);
            ps.setInt(6, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long timeEpoch = rs.getLong("time_epoch");
                    String playerName = rs.getString("player_name");
                    String actionType = rs.getString("action_type");
                    String oldBlock = rs.getString("old_block");
                    String newBlock = rs.getString("new_block");

                    result.add(new DbBlockAction(timeEpoch, playerName, actionType, oldBlock, newBlock));
                }
            }
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка чтения истории блока из БД", e);
        }

        return result;
    }

    public synchronized int getBlockHistoryCount(ResourceKey<Level> dimension, BlockPos pos) {
        if (connection == null) return 0;

        String sql = """
            SELECT COUNT(*) AS total
            FROM block_actions
            WHERE dimension = ? AND x = ? AND y = ? AND z = ?;
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, dimension.location().toString());
            ps.setInt(2, pos.getX());
            ps.setInt(3, pos.getY());
            ps.setInt(4, pos.getZ());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка подсчёта истории блока в БД", e);
        }

        return 0;
    }

    public synchronized List<DbLookupResult> getLookupHistory(ResourceKey<Level> dimension,
                                                              BlockPos center,
                                                              int radius,
                                                              Long sinceEpoch,
                                                              String playerName,
                                                              int limit,
                                                              int offset) {
        List<DbLookupResult> result = new ArrayList<>();
        if (connection == null) return result;

        StringBuilder sql = new StringBuilder("""
            SELECT time_epoch, player_name, action_type, old_block, new_block, x, y, z
            FROM block_actions
            WHERE dimension = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?
        """);

        if (sinceEpoch != null) {
            sql.append(" AND time_epoch >= ?");
        }

        if (playerName != null) {
            sql.append(" AND LOWER(player_name) = LOWER(?)");
        }

        sql.append(" ORDER BY time_epoch DESC LIMIT ? OFFSET ?;");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, dimension.location().toString());
            ps.setInt(idx++, center.getX() - radius);
            ps.setInt(idx++, center.getX() + radius);
            ps.setInt(idx++, center.getY() - radius);
            ps.setInt(idx++, center.getY() + radius);
            ps.setInt(idx++, center.getZ() - radius);
            ps.setInt(idx++, center.getZ() + radius);

            if (sinceEpoch != null) {
                ps.setLong(idx++, sinceEpoch);
            }

            if (playerName != null) {
                ps.setString(idx++, playerName);
            }

            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);

            try (ResultSet rs = ps.executeQuery()) {
                long radiusSq = (long) radius * radius;
                while (rs.next()) {
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");

                    long dx = x - center.getX();
                    long dy = y - center.getY();
                    long dz = z - center.getZ();

                    long distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > radiusSq) continue;

                    long timeEpoch = rs.getLong("time_epoch");
                    String dbPlayerName = rs.getString("player_name");
                    String actionType = rs.getString("action_type");
                    String oldBlock = rs.getString("old_block");
                    String newBlock = rs.getString("new_block");

                    result.add(new DbLookupResult(timeEpoch, dbPlayerName, actionType, oldBlock, newBlock, dimension, x, y, z));
                }
            }
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка чтения истории из БД", e);
        }

        return result;
    }

    private void close() throws SQLException {
        try {
            if (insertBlockStmt != null) insertBlockStmt.close();
        } finally {
            if (connection != null && !connection.isClosed()) {
                connection.commit();
                connection.close();
            }
        }
        Coreprotect.LOGGER.info("[Coreprotect] Подключение к БД закрыто.");
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS block_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    time_epoch INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    dimension TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    action_type TEXT NOT NULL,
                    old_block TEXT,
                    new_block TEXT
                );
                """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_block_actions_pos
                ON block_actions (dimension, x, y, z);
                """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_block_actions_player_time
                ON block_actions (player_uuid, time_epoch);
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS rollback_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    time_epoch INTEGER NOT NULL,
                    executor TEXT NOT NULL,
                    params TEXT NOT NULL,
                    restored INTEGER NOT NULL DEFAULT 0
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS rollback_session_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL,
                    dimension TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    before_block TEXT NOT NULL,
                    after_block TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES rollback_sessions(id)
                );
                """);
        }
        connection.commit();
    }

    private PreparedStatement prepareInsertStatement() throws SQLException {
        return connection.prepareStatement("""
            INSERT INTO block_actions (
                time_epoch, player_uuid, player_name, dimension,
                x, y, z, action_type, old_block, new_block
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """);
    }

    public void logBlockAction(BlockAction action) {
        if (connection == null) return;
        if (!running) return;

        boolean offered = queue.offer(action);
        if (!offered) {
            Coreprotect.LOGGER.warn("[Coreprotect] Очередь БД переполнена, событие будет записано синхронно.");
            synchronized (this) {
                try {
                    writeBlockActionInternal(action);
                    connection.commit();
                } catch (SQLException e) {
                    Coreprotect.LOGGER.error("[Coreprotect] Ошибка синхронной записи при переполнении очереди", e);
                    try {
                        connection.rollback();
                    } catch (SQLException ex) {
                        Coreprotect.LOGGER.error("[Coreprotect] Ошибка rollback при переполнении очереди", ex);
                    }
                }
            }
        }
    }

    public BlockState deserializeBlockState(String serialized) {
        if (serialized == null) {
            return Blocks.AIR.defaultBlockState();
        }

        // Новое хранение — SNBT через BlockState.CODEC
        try {
            Tag tag = TagParser.parseTag(serialized);
            DataResult<BlockState> parsed = BlockState.CODEC.parse(NbtOps.INSTANCE, tag);
            if (parsed.result().isPresent()) {
                return parsed.result().get();
            }
            parsed.error().ifPresent(err -> Coreprotect.LOGGER.warn(
                    "[Coreprotect] Ошибка декодирования BlockState из NBT '{}': {}",
                    serialized, err.message()));
        } catch (CommandSyntaxException ignored) {}

        // Совместимость: "minecraft:wool[color=red]"
        try {
            var reader = new StringReader(serialized);
            var parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, false);
            return parsed.blockState();
        } catch (CommandSyntaxException ignored) {}

        // Legacy: "minecraft:stone"
        try {
            ResourceLocation rl = new ResourceLocation(serialized);
            Block block = BuiltInRegistries.BLOCK.get(rl);
            if (block != null) {
                return block.defaultBlockState();
            }
        } catch (Exception e) {
            Coreprotect.LOGGER.warn("[Coreprotect] Не удалось десериализовать BlockState из '{}'", serialized, e);
        }

        return Blocks.AIR.defaultBlockState();
    }

    private String serializeActionState(BlockState state, String explicit) {
        if (explicit != null) {
            return explicit; // контейнеры/тексты
        }
        return serializeBlockState(state);
    }

    private String serializeBlockState(BlockState state) {
        if (state == null) {
            return null;
        }

        DataResult<Tag> encoded = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state);
        if (encoded.result().isPresent()) {
            return encoded.result().get().toString();
        }

        encoded.error().ifPresent(err -> Coreprotect.LOGGER.warn(
                "[Coreprotect] Ошибка сериализации BlockState '{}': {}",
                state, err.message()));

        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null ? key.toString() : state.toString();
    }

    // ---- остальная часть (rollback/restore/purge) у тебя уже ок ----

    public static final class DbRollbackAction {
        public final long timeEpoch;
        public final int x, y, z;
        public final String actionType;
        public final String oldBlock;

        public DbRollbackAction(long timeEpoch, int x, int y, int z, String actionType, String oldBlock) {
            this.timeEpoch = timeEpoch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.actionType = actionType;
            this.oldBlock = oldBlock;
        }
    }

    public synchronized List<DbRollbackAction> getActionsForRollback(ResourceKey<Level> dimension,
                                                                     BlockPos center,
                                                                     int radius,
                                                                     long sinceEpochSeconds) {
        List<DbRollbackAction> result = new ArrayList<>();
        if (connection == null) return result;

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        String sql = """
            SELECT time_epoch, x, y, z, action_type, old_block
            FROM block_actions
            WHERE dimension = ?
              AND time_epoch >= ?
              AND x BETWEEN ? AND ?
              AND y BETWEEN ? AND ?
              AND z BETWEEN ? AND ?
            ORDER BY time_epoch DESC;
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, dimension.location().toString());
            ps.setLong(2, sinceEpochSeconds);
            ps.setInt(3, minX);
            ps.setInt(4, maxX);
            ps.setInt(5, minY);
            ps.setInt(6, maxY);
            ps.setInt(7, minZ);
            ps.setInt(8, maxZ);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long timeEpoch = rs.getLong("time_epoch");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    String actionType = rs.getString("action_type");
                    String oldBlock = rs.getString("old_block");

                    result.add(new DbRollbackAction(timeEpoch, x, y, z, actionType, oldBlock));
                }
            }
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка выборки действий для rollback", e);
        }

        return result;
    }

    public static final class DbForwardAction {
        public final long timeEpoch;
        public final int x, y, z;
        public final String actionType;
        public final String newBlock;

        public DbForwardAction(long timeEpoch, int x, int y, int z, String actionType, String newBlock) {
            this.timeEpoch = timeEpoch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.actionType = actionType;
            this.newBlock = newBlock;
        }
    }

    public synchronized List<DbRollbackAction> getActionsForRollback(ResourceKey<Level> dimension,
                                                                     BlockPos center,
                                                                     int radius,
                                                                     long sinceEpochSeconds,
                                                                     String playerNameFilter) {
        List<DbRollbackAction> result = new ArrayList<>();
        if (connection == null) return result;

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        StringBuilder sql = new StringBuilder("""
            SELECT time_epoch, x, y, z, action_type, old_block
            FROM block_actions
            WHERE dimension = ?
              AND time_epoch >= ?
              AND x BETWEEN ? AND ?
              AND y BETWEEN ? AND ?
              AND z BETWEEN ? AND ?
            """);

        if (playerNameFilter != null && !playerNameFilter.isBlank()) {
            sql.append(" AND player_name = ? ");
        }

        sql.append(" ORDER BY time_epoch DESC;");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, dimension.location().toString());
            ps.setLong(idx++, sinceEpochSeconds);
            ps.setInt(idx++, minX);
            ps.setInt(idx++, maxX);
            ps.setInt(idx++, minY);
            ps.setInt(idx++, maxY);
            ps.setInt(idx++, minZ);
            ps.setInt(idx++, maxZ);

            if (playerNameFilter != null && !playerNameFilter.isBlank()) {
                ps.setString(idx++, playerNameFilter);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long timeEpoch = rs.getLong("time_epoch");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    String actionType = rs.getString("action_type");
                    String oldBlock = rs.getString("old_block");

                    result.add(new DbRollbackAction(timeEpoch, x, y, z, actionType, oldBlock));
                }
            }
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка выборки действий для rollback", e);
        }

        return result;
    }

    public synchronized List<DbForwardAction> getActionsForRestore(ResourceKey<Level> dimension,
                                                                   BlockPos center,
                                                                   int radius,
                                                                   long sinceEpochSeconds,
                                                                   String playerNameFilter) {
        List<DbForwardAction> result = new ArrayList<>();
        if (connection == null) return result;

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        StringBuilder sql = new StringBuilder("""
            SELECT time_epoch, x, y, z, action_type, new_block
            FROM block_actions
            WHERE dimension = ?
              AND time_epoch >= ?
              AND x BETWEEN ? AND ?
              AND y BETWEEN ? AND ?
              AND z BETWEEN ? AND ?
            """);

        if (playerNameFilter != null && !playerNameFilter.isBlank()) {
            sql.append(" AND player_name = ? ");
        }

        sql.append(" ORDER BY time_epoch ASC;");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, dimension.location().toString());
            ps.setLong(idx++, sinceEpochSeconds);
            ps.setInt(idx++, minX);
            ps.setInt(idx++, maxX);
            ps.setInt(idx++, minY);
            ps.setInt(idx++, maxY);
            ps.setInt(idx++, minZ);
            ps.setInt(idx++, maxZ);

            if (playerNameFilter != null && !playerNameFilter.isBlank()) {
                ps.setString(idx++, playerNameFilter);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long timeEpoch = rs.getLong("time_epoch");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    String actionType = rs.getString("action_type");
                    String newBlock = rs.getString("new_block");

                    result.add(new DbForwardAction(timeEpoch, x, y, z, actionType, newBlock));
                }
            }
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка выборки действий для restore", e);
        }

        return result;
    }

    public static final class RollbackSessionEntry {
        public final int id;
        public final int sessionId;
        public final String dimension;
        public final int x, y, z;
        public final String beforeBlock;
        public final String afterBlock;

        public RollbackSessionEntry(int id, int sessionId, String dimension, int x, int y, int z, String beforeBlock, String afterBlock) {
            this.id = id;
            this.sessionId = sessionId;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.beforeBlock = beforeBlock;
            this.afterBlock = afterBlock;
        }
    }

    public synchronized int createRollbackSession(String executor, String params) {
        if (connection == null) return -1;

        String sql = """
        INSERT INTO rollback_sessions (time_epoch, executor, params, restored)
        VALUES (?, ?, ?, 0);
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long nowEpoch = System.currentTimeMillis() / 1000L;

            ps.setLong(1, nowEpoch);
            ps.setString(2, executor);
            ps.setString(3, params);

            int updated = ps.executeUpdate();
            if (updated <= 0) {
                connection.rollback();
                return -1;
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    connection.commit();
                    return id;
                }
            }

            connection.rollback();
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка создания rollback-сессии", e);
            try { connection.rollback(); } catch (SQLException ignored) {}
        }

        return -1;
    }

    public synchronized void insertRollbackEntry(int sessionId,
                                                 ResourceKey<Level> dimension,
                                                 BlockPos pos,
                                                 BlockState before,
                                                 BlockState after) {
        insertRollbackEntry(sessionId, dimension, pos, serializeBlockState(before), serializeBlockState(after));
    }

    public synchronized void insertRollbackEntry(int sessionId,
                                                 ResourceKey<Level> dimension,
                                                 BlockPos pos,
                                                 String before,
                                                 String after) {
        String sql = """
            INSERT INTO rollback_session_entries (
                session_id, dimension, x, y, z, before_block, after_block
            ) VALUES (?, ?, ?, ?, ?, ?, ?);
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setString(2, dimension.location().toString());
            ps.setInt(3, pos.getX());
            ps.setInt(4, pos.getY());
            ps.setInt(5, pos.getZ());
            ps.setString(6, before);
            ps.setString(7, after);

            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка записи rollback-entry", e);
            try {
                connection.rollback();
            } catch (SQLException ex) {
                Coreprotect.LOGGER.error("[Coreprotect] Ошибка rollback при insertRollbackEntry", ex);
            }
        }
    }

    public synchronized List<RollbackSessionEntry> getRollbackEntries(int sessionId) {
        List<RollbackSessionEntry> result = new ArrayList<>();
        String sql = """
            SELECT id, session_id, dimension, x, y, z, before_block, after_block
            FROM rollback_session_entries
            WHERE session_id = ?
            ORDER BY id ASC;
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RollbackSessionEntry(
                            rs.getInt("id"),
                            rs.getInt("session_id"),
                            rs.getString("dimension"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getString("before_block"),
                            rs.getString("after_block")
                    ));
                }
            }
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка чтения rollback-сессии " + sessionId, e);
        }
        return result;
    }

    public synchronized void markSessionRestored(int sessionId) {
        String sql = "UPDATE rollback_sessions SET restored = 1 WHERE id = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка пометки сессии restored", e);
            try {
                connection.rollback();
            } catch (SQLException ex) {
                Coreprotect.LOGGER.error("[Coreprotect] Ошибка rollback при markSessionRestored", ex);
            }
        }
    }

    public synchronized int purgeOldData(long olderThanEpochSeconds,
                                         ResourceKey<Level> dimension,
                                         List<String> includeBlocks) {
        if (connection == null) return 0;

        StringBuilder sql = new StringBuilder("DELETE FROM block_actions WHERE time_epoch < ?");
        if (dimension != null) sql.append(" AND dimension = ?");

        if (includeBlocks != null && !includeBlocks.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(includeBlocks.size(), "?"));
            sql.append(" AND (old_block IN (").append(placeholders).append(") OR new_block IN (").append(placeholders).append("))");
        }

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setLong(idx++, olderThanEpochSeconds);
            if (dimension != null) ps.setString(idx++, dimension.location().toString());

            if (includeBlocks != null && !includeBlocks.isEmpty()) {
                for (String block : includeBlocks) ps.setString(idx++, block);
                for (String block : includeBlocks) ps.setString(idx++, block);
            }

            int deleted = ps.executeUpdate();
            connection.commit();
            return deleted;
        } catch (SQLException e) {
            Coreprotect.LOGGER.error("[Coreprotect] Ошибка purge старых данных", e);
            try {
                connection.rollback();
            } catch (SQLException ex) {
                Coreprotect.LOGGER.error("[Coreprotect] Ошибка rollback при purge", ex);
            }
        }

        return 0;
    }
}
