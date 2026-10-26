package es.normsafio.muebles;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Muebles extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey keyId;       // id del mueble (item y entidades)
    private NamespacedKey keyDisplay;  // UUID del ItemDisplay (guardado en la Interaction)
    private NamespacedKey keySeat;     // marca de armor stand de asiento
    private final Map<String, Furniture> registry = new LinkedHashMap<>();
    private final Map<UUID, Integer> lastHandledTick = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyId = new NamespacedKey(this, "furniture_id");
        keyDisplay = new NamespacedKey(this, "furniture_display");
        keySeat = new NamespacedKey(this, "seat");
        for (Furniture f : Furniture.ALL) registry.put(f.id(), f);
        Bukkit.getPluginManager().registerEvents(this, this);
        var cmd = getCommand("mueble");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        getLogger().info(registry.size() + " muebles cargados.");
    }

    @Override
    public void onDisable() {
        for (World w : Bukkit.getWorlds())
            for (ArmorStand a : w.getEntitiesByClass(ArmorStand.class))
                if (a.getPersistentDataContainer().has(keySeat, PersistentDataType.BYTE)) a.remove();
    }

    // ------------------------------------------------------------------ utilidades
    private String msg(String key) {
        return getConfig().getString("messages." + key, key).replace('&', '\u00a7');
    }

    public ItemStack createItem(Furniture f) {
        ItemStack it = new ItemStack(Material.BRICK);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("\u00a7e" + f.name());
        m.setLore(List.of("\u00a77Mueble decorativo", "\u00a78Clic derecho para colocar"));
        m.setCustomModelData(f.cmd());
        m.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, f.id());
        it.setItemMeta(m);
        return it;
    }

    private Furniture fromItem(ItemStack it) {
        if (it == null || it.getType() != Material.BRICK || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        return id == null ? null : registry.get(id);
    }

    private Furniture fromEntity(Entity e) {
        if (!(e instanceof Interaction)) return null;
        String id = e.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        return id == null ? null : registry.get(id);
    }

    private ItemDisplay displayOf(Interaction hitbox) {
        String s = hitbox.getPersistentDataContainer().get(keyDisplay, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            Entity e = Bukkit.getEntity(UUID.fromString(s));
            return e instanceof ItemDisplay d ? d : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private float snap(float yaw) {
        float step = (float) Math.max(1, getConfig().getDouble("snap-degrees", 45));
        return Math.round(yaw / step) * step;
    }

    /** Evita procesar dos veces el mismo clic (el cliente envia INTERACT_AT + INTERACT). */
    private boolean alreadyHandled(Player p) {
        int now = Bukkit.getCurrentTick();
        Integer last = lastHandledTick.get(p.getUniqueId());
        if (last != null && last == now) return true;
        lastHandledTick.put(p.getUniqueId(), now);
        return false;
    }

    // ------------------------------------------------------------------ colocar
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Furniture f = fromItem(e.getItem());
        if (f == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("muebles.usar")) { p.sendMessage(msg("no-permission")); return; }

        Block clicked = e.getClickedBlock();
        BlockFace face = e.getBlockFace();
        Block target = (clicked.isReplaceable()) ? clicked : clicked.getRelative(face);
        if (!(target.isEmpty() || target.isReplaceable())) { p.sendMessage(msg("no-space")); return; }

        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        for (Entity near : target.getWorld().getNearbyEntities(center, 0.45, 0.45, 0.45))
            if (near instanceof Interaction && near.getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) {
                p.sendMessage(msg("no-space")); return;
            }

        // respeta WorldGuard & co: se lanza un BlockPlaceEvent "de prueba"
        BlockPlaceEvent test = new BlockPlaceEvent(target, target.getState(), clicked, e.getItem(), p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(test);
        if (test.isCancelled() || !test.canBuild()) return;

        float yaw = snap(p.getLocation().getYaw() + 180f + (float) getConfig().getDouble("yaw-offset", 0));
        spawn(f, center, yaw);
        if (p.getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
        target.getWorld().playSound(center, org.bukkit.Sound.BLOCK_WOOD_PLACE, 1f, 1f);
    }

    private void spawn(Furniture f, Location center, float yaw) {
        World w = center.getWorld();
        Location dl = center.clone();
        dl.setYaw(yaw);
        dl.setPitch(0);
        ItemDisplay display = w.spawn(dl, ItemDisplay.class, d -> {
            d.setItemStack(createItem(f));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, f.id());
        });
        Location hl = center.clone().subtract(0, 0.5, 0);
        w.spawn(hl, Interaction.class, i -> {
            i.setInteractionWidth((float) f.width());
            i.setInteractionHeight((float) f.height());
            i.setResponsive(true);
            i.setPersistent(true);
            PersistentDataContainer pdc = i.getPersistentDataContainer();
            pdc.set(keyId, PersistentDataType.STRING, f.id());
            pdc.set(keyDisplay, PersistentDataType.STRING, display.getUniqueId().toString());
        });
    }

    // ------------------------------------------------------------------ clic derecho: sentarse / girar / recoger
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Furniture f = fromEntity(e.getRightClicked());
        if (f == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (alreadyHandled(p)) return;
        if (!p.hasPermission("muebles.usar")) { p.sendMessage(msg("no-permission")); return; }
        Interaction hit = (Interaction) e.getRightClicked();
        ItemDisplay display = displayOf(hit);
        if (display == null) { hit.remove(); return; } // hitbox huerfana

        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (inHand.getType() == Material.STICK) {            // palo = herramienta de giro
            float yaw = snap(display.getLocation().getYaw() + (float) getConfig().getDouble("snap-degrees", 45));
            display.setRotation(yaw, 0f);
            p.sendMessage(msg("rotated"));
            return;
        }
        if (p.isSneaking() && inHand.getType() == Material.AIR) { // agachado + mano vacia = recoger
            pickUp(p, hit, display, f);
            return;
        }
        if (f.sittable()) {
            Vector click = (e instanceof PlayerInteractAtEntityEvent at) ? at.getClickedPosition() : null;
            sit(p, display, f, click);
        }
    }

    private void pickUp(Player p, Interaction hit, ItemDisplay display, Furniture f) {
        Location loc = display.getLocation();
        hit.remove();
        display.remove();
        if (p.getGameMode() != GameMode.CREATIVE) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(createItem(f));
            for (ItemStack rest : left.values()) loc.getWorld().dropItemNaturally(loc, rest);
        }
        loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_WOOD_BREAK, 1f, 1f);
        p.sendMessage(msg("picked-up"));
    }

    // ------------------------------------------------------------------ clic izquierdo: romper
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttackPaper(PrePlayerAttackEntityEvent e) {
        Furniture f = fromEntity(e.getAttacked());
        if (f == null) return;
        e.setCancelled(true);
        breakByPlayer(e.getPlayer(), (Interaction) e.getAttacked(), f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttackDamage(EntityDamageByEntityEvent e) {
        Furniture f = fromEntity(e.getEntity());
        if (f == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) breakByPlayer(p, (Interaction) e.getEntity(), f);
    }

    private void breakByPlayer(Player p, Interaction hit, Furniture f) {
        if (alreadyHandled(p)) return;
        if (!p.hasPermission("muebles.usar")) { p.sendMessage(msg("no-permission")); return; }
        ItemDisplay display = displayOf(hit);
        Location loc = hit.getLocation().add(0, 0.5, 0);
        hit.remove();
        if (display != null) display.remove();
        if (p.getGameMode() != GameMode.CREATIVE) loc.getWorld().dropItemNaturally(loc, createItem(f));
        loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_WOOD_BREAK, 1f, 1f);
    }

    // ------------------------------------------------------------------ sentarse
    private void sit(Player p, ItemDisplay display, Furniture f, Vector click) {
        Location base = display.getLocation();
        double yawRad = Math.toRadians(base.getYaw());
        double c = Math.cos(yawRad), s = Math.sin(yawRad);
        double wanted = 0;
        if (click != null && f.seats().length > 1) {
            // el clic viene relativo a la hitbox (en la base del mueble) -> proyectamos al eje local X
            wanted = click.getX() * c + click.getZ() * s;
        }
        double ox = f.seats()[0];
        double best = Double.MAX_VALUE;
        for (double cand : f.seats()) {
            double d = Math.abs(cand - wanted);
            if (d < best) { best = d; ox = cand; }
        }
        Location seat = base.clone().subtract(0, 0.5, 0)
                .add(ox * c, f.seatHeight() + getConfig().getDouble("seat-offset", -0.1), ox * s);
        seat.setYaw(base.getYaw());

        // asiento ocupado?
        for (Entity near : seat.getWorld().getNearbyEntities(seat, 0.4, 1.0, 0.4))
            if (near instanceof ArmorStand a && a.getPersistentDataContainer().has(keySeat, PersistentDataType.BYTE)
                    && !a.getPassengers().isEmpty()) {
                p.sendMessage(msg("occupied")); return;
            }
        ArmorStand stand = seat.getWorld().spawn(seat, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setSmall(true);
            a.setGravity(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setPersistent(false);
            a.getPersistentDataContainer().set(keySeat, PersistentDataType.BYTE, (byte) 1);
        });
        stand.addPassenger(p);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        Entity v = e.getDismounted();
        if (v instanceof ArmorStand a && a.getPersistentDataContainer().has(keySeat, PersistentDataType.BYTE)) {
            Entity rider = e.getEntity();
            Location out = a.getLocation().add(0, 0.9, 0);
            Bukkit.getScheduler().runTask(this, () -> {
                a.remove();
                if (rider.isValid()) rider.teleport(out);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Entity v = e.getPlayer().getVehicle();
        if (v instanceof ArmorStand a && a.getPersistentDataContainer().has(keySeat, PersistentDataType.BYTE)) a.remove();
        lastHandledTick.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ menu + comandos
    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private void openMenu(Player p) {
        Menu holder = new Menu();
        int size = 27;
        holder.inv = Bukkit.createInventory(holder, size, "\u00a76Muebles");
        int i = 0;
        for (Furniture f : registry.values()) holder.inv.setItem(i++, createItem(f));
        p.openInventory(holder.inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        Furniture f = fromItem(e.getCurrentItem());
        if (f == null) return;
        ItemStack give = createItem(f);
        if (e.isShiftClick()) give.setAmount(16);
        p.getInventory().addItem(give).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) { s.sendMessage("Usa /mueble dar <id> <jugador>"); return true; }
            if (!p.hasPermission("muebles.menu")) { p.sendMessage(msg("no-permission")); return true; }
            openMenu(p);
            return true;
        }
        if (a[0].equalsIgnoreCase("lista")) {
            s.sendMessage("\u00a76Muebles: \u00a7f" + String.join(", ", registry.keySet()));
            return true;
        }
        if (a[0].equalsIgnoreCase("dar")) {
            if (!s.hasPermission("muebles.admin")) { s.sendMessage(msg("no-permission")); return true; }
            if (a.length < 2) { s.sendMessage("\u00a7cUso: /mueble dar <id> [jugador] [cantidad]"); return true; }
            Furniture f = registry.get(a[1].toLowerCase());
            if (f == null) { s.sendMessage("\u00a7cMueble desconocido. /mueble lista"); return true; }
            Player target = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player pl ? pl : null);
            if (target == null) { s.sendMessage("\u00a7cJugador no encontrado."); return true; }
            int n = 1;
            if (a.length >= 4) try { n = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) { }
            ItemStack it = createItem(f);
            it.setAmount(n);
            target.getInventory().addItem(it).values().forEach(r -> target.getWorld().dropItemNaturally(target.getLocation(), r));
            s.sendMessage("\u00a7aDado " + n + "x " + f.name() + " a " + target.getName());
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) { out.add("dar"); out.add("lista"); }
        else if (a.length == 2 && a[0].equalsIgnoreCase("dar")) out.addAll(registry.keySet());
        else if (a.length == 3 && a[0].equalsIgnoreCase("dar")) Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(o -> !o.toLowerCase().startsWith(last));
        return out;
    }
}
