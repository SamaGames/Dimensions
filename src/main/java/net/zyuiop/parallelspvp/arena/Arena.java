package net.zyuiop.parallelspvp.arena;

import net.samagames.network.Network;
import net.samagames.network.client.GameArena;
import net.samagames.network.client.GamePlayer;
import net.samagames.network.json.Status;
import net.zyuiop.coinsManager.CoinsManager;
import net.zyuiop.parallelspvp.ParallelsPVP;
import net.zyuiop.parallelspvp.tasks.BeginCountdown;
import net.zyuiop.parallelspvp.tasks.Deathmatch;
import net.zyuiop.parallelspvp.tasks.RandomEffects;
import net.zyuiop.parallelspvp.utils.Colors;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

/**
 * Created by zyuiop on 26/09/14.
 */
public class Arena extends GameArena {

    protected ParallelsPVP parallelsPVP;
    protected BukkitTask countdown;
    protected ArrayList<Location> spawns = new ArrayList<Location>();
    protected ArrayList<Location> deathmatchSpawns = new ArrayList<Location>();
    protected ArrayList<ChestDescriptor> chests = new ArrayList<ChestDescriptor>();
    protected Location waitLocation;
    protected DimensionsManager dimensionsManager;
    protected boolean isDeathmatch = false;
    protected BukkitTask dmCount = null;
    protected ArrayList<Material> allowed = new ArrayList<>();
    protected Scoreboard scoreboard = null;
    protected BukkitTask randomEffects = null;


    public boolean canBreak(Material madeOf) {
        return allowed.contains(madeOf);
    }

    public Arena(ParallelsPVP parallelsPVP, YamlConfiguration arenaData, int maxPlayers, int maxVIP, String mapName, UUID arenaId) {
        super(maxPlayers, maxVIP, mapName, arenaId, false);

        this.parallelsPVP = parallelsPVP;

        dimensionsManager = new DimensionsManager(this, arenaData.getInt("dimension-diff"), arenaData.getString("overworld-name"), arenaData.getString("hard-name"));
        waitLocation = null;
        World world = Bukkit.getWorlds().get(0);
        String[] locparts = arenaData.getString("wait-spawn", " ").split(";");
        if (locparts.length < 3) {
            Bukkit.getLogger().severe("ERREUR : coordonnées spawn invalides (not enough params) dans le fichier d'arène.");
        }

        if (locparts.length == 5) {
            waitLocation = new Location(world, Double.parseDouble(locparts[0]), Double.parseDouble(locparts[1]), Double.parseDouble(locparts[2]), Float.parseFloat(locparts[3]), Float.parseFloat(locparts[4]));
        } else {
            waitLocation = new Location(world, Double.parseDouble(locparts[0]), Double.parseDouble(locparts[1]), Double.parseDouble(locparts[2]));
        }

        this.mapName = arenaData.getString("map-name");

        int minPlayers = arenaData.getInt("min-players");

        for (Object material : arenaData.getList("allowed")) {
            try {
                if (material instanceof String) {
                    Material mat = Material.valueOf((String) material);
                    if (!allowed.contains(mat))
                        allowed.add(mat);
                } else if (material instanceof  Integer) {
                    Material mat = Material.getMaterial((Integer)material);
                    if (!allowed.contains(mat))
                        allowed.add(mat);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (Object spawn : arenaData.getList("spawns")) {
            if (!(spawn instanceof String)) {
                Bukkit.getLogger().severe("ERREUR : coordonnées invalides (not a string) dans le fichier d'arène.");
                continue;
            }

            String spawnString = (String) spawn;

            String[] parts = spawnString.split(";");
            if (parts.length < 3) {
                Bukkit.getLogger().severe("ERREUR : coordonnées invalides (not enough params) dans le fichier d'arène.");
                continue;
            }

            Location loc = null;
            if (parts.length == 5) {
                loc = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Float.parseFloat(parts[3]), Float.parseFloat(parts[4]));
            } else {
                loc = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            }

            spawns.add(loc);
        }

        for (Object spawn : arenaData.getList("deathmatchspawns")) {
            if (!(spawn instanceof String)) {
                Bukkit.getLogger().severe("ERREUR : coordonnées invalides (not a string) dans le fichier d'arène.");
                continue;
            }

            String spawnString = (String) spawn;

            String[] parts = spawnString.split(";");
            if (parts.length < 3) {
                Bukkit.getLogger().severe("ERREUR : coordonnées invalides (not enough params) dans le fichier d'arène.");
                continue;
            }

            Location loc = null;
            if (parts.length == 5) {
                loc = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Float.parseFloat(parts[3]), Float.parseFloat(parts[4]));
            } else {
                loc = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            }

            deathmatchSpawns.add(loc);
        }

        if (spawns.size() < (maxPlayers + maxVIP)) {
            maxPlayers = spawns.size() - maxVIP;
            Bukkit.getLogger().severe("ATTENTION : pas assez de spawns, nombre de joueurs max réduit a "+maxPlayers+" (+ "+maxVIP+" slots VIP)");
        }

        if (minPlayers > (maxPlayers + maxVIP)) {
            minPlayers = ((maxPlayers + maxVIP) / 100) * 50;
        }

        for (Object chest : arenaData.getList("chests")) {
            if (!(chest instanceof String)) {
                Bukkit.getLogger().severe("ERREUR : info de chest invalides (not a string) dans le fichier d'arène.");
                continue;
            }

            String chestString = (String) chest;

            String[] parts = chestString.split(";");
            if (parts.length < 4) {
                Bukkit.getLogger().severe("ERREUR : infos de chest invalides (not enough params) dans le fichier d'arène.");
                continue;
            }

            Location loc = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            int loots = Integer.parseInt(parts[3]);

            chests.add(new ChestDescriptor(loc, loots));
        }

        countdown = Bukkit.getScheduler().runTaskTimerAsynchronously(parallelsPVP, new BeginCountdown(this, maxPlayers + this.maxVIP, minPlayers), 0, 20L);

        this.setStatus(Status.Available);

        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

    }

    public ChestDescriptor getChestFromLocation(Location loc) {
        for (ChestDescriptor desc : chests)
            if (desc.getLocation().equals(loc))
                return desc;
        return null;
    }

    protected void updateStatus(Status newStatus) {
        this.status = newStatus;
        Network.getManager().refreshArena(this);
    }

    public DimensionsManager getDimensionsManager() {
        return dimensionsManager;
    }


    /*
     Methods related to game management
     */

    protected boolean isPVPEnabled = false;

    public void start() {
        try {
            countdown.cancel();
            countdown = null;
            Bukkit.getLogger().info("Cancelled thread");
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.setStatus(Status.InGame);

        Bukkit.broadcastMessage(ParallelsPVP.pluginTAG+ChatColor.GOLD+" La partie commence. Bonne chance !");
        Bukkit.broadcastMessage(ParallelsPVP.pluginTAG+ChatColor.GOLD+" Le PVP sera activé dans 3 minutes.");
        Bukkit.getScheduler().runTaskLater(parallelsPVP, new Runnable() {
            @Override
            public void run() {
                enablePVP();
            }
        }, 60*3*20L);

        ArrayList<GamePlayer> remove = new ArrayList<GamePlayer>();
        Iterator<GamePlayer> iterator = players.iterator();

        for (Location spawn : this.spawns) {
            if (!iterator.hasNext())
                break;
            GamePlayer vplayer = iterator.next();
            Player player = Bukkit.getPlayer(vplayer.getPlayerID());
            resetPlayer(player);
            if (player == null) {
                remove.add(vplayer);
                continue;
            } else {
                player.teleport(spawn);
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(ParallelsPVP.getCompass());
                player.getInventory().addItem(ParallelsPVP.getSwap());
            }
        }

        for (GamePlayer player : remove) {
            players.remove(player);
        }

        scoreboard.registerNewObjective("vie", "health").setDisplaySlot(DisplaySlot.BELOW_NAME);
        scoreboard.getObjective("vie").setDisplayName("♥");

        RandomEffects eff = new RandomEffects(this);
        this.randomEffects = Bukkit.getScheduler().runTaskTimerAsynchronously(parallelsPVP, eff, 0L, 20L);
    }

    public void enablePVP() {
        this.isPVPEnabled = true;
        Bukkit.broadcastMessage(ParallelsPVP.pluginTAG+ChatColor.GOLD+" Le PVP est activé ! C'est l'heure du d-d-d-duel !");
    }

    public void startDeathMatch() {
        this.dmCount.cancel();
        this.isDeathmatch = true;
        ArrayList<Player> offline = new ArrayList<Player>();
        Iterator<Location> spawns = this.deathmatchSpawns.iterator();
        for (GamePlayer ap : this.players) {
            Player player = Bukkit.getPlayer(ap.getPlayerID());
            if (!spawns.hasNext()) {
                Bukkit.broadcastMessage(ChatColor.RED+"Une erreur s'est produite, tous les joueurs ne peuvent pas être transférés en deathmatch.");
                break;
            } else {
                Location spawn = spawns.next();
                player.teleport(spawn);
            }
        }
    }

    public void finish(FinishReason reason) {
        if (randomEffects != null)
            randomEffects.cancel();

        if (reason.equals(FinishReason.WIN)) {
            if (players.size() == 0) {
                resetArena();
                return;
            }

            if (players.size() > 1)
                return;

            GamePlayer winner = players.get(0);
            final Player player = Bukkit.getPlayer(winner.getPlayerID());
            if (player == null) {
                resetArena();
                return;
            }

            Bukkit.broadcastMessage(parallelsPVP.pluginTAG+ChatColor.GREEN+ChatColor.MAGIC+"aaa"+ChatColor.GOLD+" Victoire ! "+ChatColor.GREEN+ChatColor.MAGIC+"aaa"+ChatColor.GOLD+" Bravo a "+ChatColor.LIGHT_PURPLE+player.getName()+ChatColor.GOLD+" !");

            Bukkit.getScheduler().runTaskAsynchronously(parallelsPVP, new Runnable() {
                @Override
                public void run() {
                    int montant = CoinsManager.syncCreditJoueur(player.getUniqueId(), 20, false, true);
                    player.sendMessage(ChatColor.GOLD + "Vous gagnez " + montant + " coins " + ChatColor.AQUA + "(Victoire !)");
                }
            });

            // Feux d'artifice swag
            final int nb = 20;
            Bukkit.getScheduler().scheduleSyncRepeatingTask(parallelsPVP, new Runnable() {
                int compteur = 0;

                public void run() {

                    if (compteur >= nb || player == null) {
                        return;
                    }

                    //Spawn the Firework, get the FireworkMeta.
                    Firework fw = (Firework) player.getWorld().spawnEntity(player.getPlayer().getLocation(), EntityType.FIREWORK);
                    FireworkMeta fwm = fw.getFireworkMeta();

                    //Our random generator
                    Random r = new Random();

                    //Get the type
                    int rt = r.nextInt(4) + 1;
                    FireworkEffect.Type type = FireworkEffect.Type.BALL;
                    if (rt == 1) type = FireworkEffect.Type.BALL;
                    if (rt == 2) type = FireworkEffect.Type.BALL_LARGE;
                    if (rt == 3) type = FireworkEffect.Type.BURST;
                    if (rt == 4) type = FireworkEffect.Type.CREEPER;
                    if (rt == 5) type = FireworkEffect.Type.STAR;

                    //Get our random colours
                    int r1i = r.nextInt(17) + 1;
                    int r2i = r.nextInt(17) + 1;
                    Color c1 = Colors.getColor(r1i);
                    Color c2 = Colors.getColor(r2i);

                    //Create our effect with this
                    FireworkEffect effect = FireworkEffect.builder().flicker(r.nextBoolean()).withColor(c1).withFade(c2).with(type).trail(r.nextBoolean()).build();

                    //Then apply the effect to the meta
                    fwm.addEffect(effect);

                    //Generate some random power and set it
                    int rp = r.nextInt(2) + 1;
                    fwm.setPower(rp);

                    //Then apply this to our rocket
                    fw.setFireworkMeta(fwm);

                    compteur++;
                }

            }, 5L, 5L);

            Bukkit.getScheduler().runTaskLater(parallelsPVP, new Runnable() {
                public void run() {
                    resetArena();
                }
            }, 30*20L);
        }
    }

    public void resetArena() {
        this.updateStatus(Status.Stopping);
        Bukkit.getServer().shutdown();
    }

    public void joinSpectators(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999999, 1));
        p.setGameMode(GameMode.CREATIVE);
        p.teleport(this.waitLocation);
        p.sendMessage(ChatColor.GOLD+"Vous rejoignez les spectateurs.");

        for (GamePlayer pl : this.players) {
            Player target = Bukkit.getPlayer(pl.getPlayerID());
            target.hidePlayer(p);
        }
    }

    @Override
    public void logout(UUID player) {
        GamePlayer arPlayer = new GamePlayer(player);
        if (!isStarted()) {
            players.remove(arPlayer);
            return;
        }

        if (!isPlaying(arPlayer))
            spectators.remove(arPlayer);
        else
            stumpPlayer(player, true);
    }

    public void stumpPlayer(UUID player, boolean logout) {
        GamePlayer arPlayer = new GamePlayer(player);

        this.players.remove(arPlayer);
        int left = this.players.size();
        boolean isWon = (left <= 1);

        Player rPlayer = arPlayer.getPlayer();


        if (logout && rPlayer != null) {
            Bukkit.broadcastMessage(ParallelsPVP.pluginTAG+ChatColor.RED+" "+rPlayer.getName()+" s'est déconnecté.");
        } else if (rPlayer != null) {
            Bukkit.broadcastMessage(ParallelsPVP.pluginTAG+ChatColor.RED+" "+rPlayer.getName()+" a été éliminé.");
            joinSpectators(rPlayer);
        }

        if (!isWon) {
            Bukkit.broadcastMessage(ChatColor.YELLOW+ "Il reste encore "+ChatColor.AQUA+left+ChatColor.YELLOW+" joueurs en vie.");
            if (left <= this.deathmatchSpawns.size() && !this.isDeathmatch && this.dmCount == null) {
                Deathmatch countdown = new Deathmatch(this);
                dmCount = Bukkit.getScheduler().runTaskTimer(parallelsPVP, countdown, 0L, 20L);
            }
        } else {
            finish(FinishReason.WIN);
        }
    }

    public enum FinishReason {
        END_OF_TIME,
        WIN,
        NO_PLAYERS;
    }

    public boolean isPlaying(GamePlayer player) {
        return players.contains(player);
    }

    @Override
    public int countPlayersIngame() {
        return players.size();
    }

    public boolean isPVPEnabled() {
        return isPVPEnabled;
    }

    /*
     Methods related to players management
     */

    /**
     * This method *must* be called asynchroniously as it makes DB requests.
     * @param playerId id du joueur
     * @return
     */

    @Override
    public String finishJoinPlayer(UUID playerId) {
        String rep = super.finishJoinPlayer(playerId);
        if (!rep.equals("OK"))
            return rep;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null)
            return ChatColor.RED+"Une erreur de connexion s'est produite.";

        int nbPlayers = this.countPlayers();

        String reason = "";
        if (nbPlayers > maxPlayers)
            reason = ChatColor.GREEN+"[Slots Donateurs] ";

        // Setup du joueur
        player.sendMessage(ChatColor.GOLD+"Bienvenue dans "+ChatColor.RED+"Parallels PVP"+ChatColor.GOLD+" !");
        resetPlayer(player);
        nbPlayers++;
        Bukkit.broadcastMessage(ParallelsPVP.pluginTAG+ChatColor.YELLOW+" "+
                player.getName()+
                ChatColor.YELLOW+" a rejoint la partie ! "+reason+
                ChatColor.DARK_GRAY+"[" + ChatColor.RED + nbPlayers + ChatColor.DARK_GRAY + "/" + ChatColor.RED + maxPlayers + ChatColor.DARK_GRAY+"]");


        player.teleport(this.waitLocation);

        return "OK";
    }


    public void resetPlayer(Player p) {
        p.setHealth(20.0);
        p.setMaxHealth(20.0);
        p.setSaturation(20);
        p.getActivePotionEffects().clear();
        p.setAllowFlight(false);
        p.setGameMode(GameMode.ADVENTURE);
        p.setLevel(0);
        p.setExp(0);
        p.setFlying(false);
        p.setHealthScaled(false);
        p.setFireTicks(0);
        p.getInventory().clear();
        p.getInventory().setHelmet(new ItemStack(Material.AIR));
        p.getInventory().setChestplate(new ItemStack(Material.AIR));
        p.getInventory().setLeggings(new ItemStack(Material.AIR));
        p.getInventory().setBoots(new ItemStack(Material.AIR));
        p.setScoreboard(this.scoreboard);
    }

    public int countPlayers() {
        return players.size();
    }

}
