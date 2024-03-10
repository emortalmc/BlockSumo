package dev.emortal.minestom.blocksumo.game;

import dev.emortal.minestom.blocksumo.damage.PlayerDamageHandler;
import dev.emortal.minestom.blocksumo.damage.PlayerDeathHandler;
import dev.emortal.minestom.blocksumo.scoreboard.Scoreboard;
import dev.emortal.minestom.blocksumo.scoreboard.ScoreboardManager;
import dev.emortal.minestom.blocksumo.spawning.PlayerRespawnHandler;
import dev.emortal.minestom.blocksumo.team.PlayerTeamManager;
import dev.emortal.minestom.blocksumo.utils.text.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.attribute.Attribute;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.other.AreaEffectCloudMeta;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

public final class PlayerManager {
    private static final Component SCOREBOARD_FOOTER = Component.text()
            .append(Component.text(TextUtil.convertToSmallFont("mc.emortal.dev"), NamedTextColor.DARK_GRAY))
            .append(Component.text("       ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
            .build();

    private final @NotNull BlockSumoGame game;
    private final @NotNull PlayerRespawnHandler respawnHandler;
    private final @NotNull ScoreboardManager scoreboardManager;
    private final @NotNull PlayerDeathHandler deathHandler;
    private final @NotNull PlayerTeamManager teamManager;
    private final @NotNull PlayerDamageHandler damageHandler;
    private final @NotNull PlayerBlockHandler blockHandler;
    private final @NotNull PlayerDiamondBlockHandler diamondBlockHandler;

    public PlayerManager(@NotNull BlockSumoGame game, @NotNull PlayerRespawnHandler respawnHandler, @NotNull ScoreboardManager scoreboardManager, int minAllowedHeight) {
        this.game = game;
        this.respawnHandler = respawnHandler;
        this.scoreboardManager = scoreboardManager;
        this.deathHandler = new PlayerDeathHandler(game, this, respawnHandler, minAllowedHeight);
        this.teamManager = new PlayerTeamManager();
        this.damageHandler = new PlayerDamageHandler(game);
        this.blockHandler = new PlayerBlockHandler(game);
        this.diamondBlockHandler = new PlayerDiamondBlockHandler(game);
    }

    public void registerPreGameListeners(@NotNull EventNode<Event> eventNode) {
        eventNode.addListener(PlayerSpawnEvent.class, this::onSpawn);
    }

    public void registerGameListeners(@NotNull EventNode<Event> eventNode) {
        this.deathHandler.registerListeners(eventNode);
        this.damageHandler.registerListeners(eventNode);
        this.blockHandler.registerListeners(eventNode);
        this.diamondBlockHandler.registerListeners(eventNode);
    }

    private void onSpawn(@NotNull PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        this.prepareInitialSpawn(player, player.getRespawnPoint());
        this.selectTeam(player);
        this.scoreboardManager.addViewer(player);
        this.updateLivesInHealth(player);
    }

    private void updateLivesInHealth(@NotNull Player player) {
        int lives = player.getTag(PlayerTags.LIVES);
        float health = lives * 2;

        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        player.setHealth(player.getMaxHealth());
    }

    private void prepareInitialSpawn(@NotNull Player player, @NotNull Pos pos) {
        this.respawnHandler.prepareSpawn(pos);
        this.createLockingEntity(this.game.getSpawningInstance(player), player, pos);
    }

    private void createLockingEntity(@NotNull Instance instance, @NotNull Player player, @NotNull Pos pos) {
        Entity entity = new Entity(EntityType.AREA_EFFECT_CLOUD);
        ((AreaEffectCloudMeta) entity.getEntityMeta()).setRadius(0);
        entity.setNoGravity(true);
        entity.setInstance(instance, pos.add(0, 0.1, 0)).thenRun(() -> entity.addPassenger(player));
    }

    public void addInitialTags(@NotNull Player player) {
        player.setTag(PlayerTags.LAST_DAMAGE_TIME, 0L);
        player.setTag(PlayerTags.LIVES, (byte) 5);
        player.setTag(PlayerTags.KILLS, 0);
        player.setTag(PlayerTags.FINAL_KILLS, 0);
        player.setTag(PlayerTags.CAN_BE_HIT, true);
        player.setTag(PlayerTags.SPAWN_PROTECTION_TIME, 0L);
    }

    private void selectTeam(@NotNull Player player) {
        this.teamManager.allocateTeam(player);
        this.scoreboardManager.updateScoreboard(this.game.getPlayers());
    }

    public void cleanUp() {
        this.teamManager.removeAllTeams();
        this.respawnHandler.stopAllScheduledRespawns();

        for (Player player : this.game.getPlayers()) {
            this.cleanUpPlayer(player);
        }
    }

    public void cleanUpPlayer(@NotNull Player player) {
        player.removeTag(PlayerTags.TEAM_COLOR);
        player.removeTag(PlayerTags.LIVES);
        player.removeTag(PlayerTags.LAST_DAMAGE_TIME);
        player.removeTag(PlayerTags.CAN_BE_HIT);

        this.scoreboardManager.removeViewer(player);
        this.scoreboardManager.updateScoreboard(this.game.getPlayers());
    }

    public void removeDeadPlayer() {
        this.scoreboardManager.updateScoreboard(this.game.getPlayers());
    }

    public void updateRemainingLives(@NotNull Player player, int lives) {
        this.teamManager.updateTeamLives(player, lives);
        this.scoreboardManager.updateScoreboard(this.game.getPlayers());
    }

    public @NotNull PlayerDamageHandler getDamageHandler() {
        return this.damageHandler;
    }
}
