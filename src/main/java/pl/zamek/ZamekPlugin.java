package pl.zamek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ZamekPlugin extends JavaPlugin implements Listener {

    // ====== WYMIARY ZAMKU - zmien tutaj, jesli maja byc inne ======
    private static final int SIZE = 17;          // dlugosc i szerokosc (nieparzysta)
    private static final int WALL_HEIGHT = 7;    // wysokosc murow (bez blankow)
    private static final int TOWER_SIZE = 5;     // wielkosc wiezy w rogach
    private static final int TOWER_HEIGHT = 11;  // wysokosc wiezy (poziom dachu)
    private static final int KEEP_FROM = 5;      // glowny budynek od...
    private static final int KEEP_TO = 11;       // ...do (wlacznie)
    private static final int KEEP_HEIGHT = 9;    // poziom dachu glownego budynku
    private static final int MAX_Y = 13;         // ile warstw w gore czyscimy/budujemy
    // ==============================================================

    private static final Material GLASS = Material.WHITE_STAINED_GLASS;
    private static final Material AIR = Material.AIR;

    private NamespacedKey key;

    @Override
    public void onEnable() {
        key = new NamespacedKey(this, "zamek");
        getServer().getPluginManager().registerEvents(this, this);
    }

    // ---------- komenda /zamek [gracz] ----------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = getServer().getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Nie ma takiego gracza online.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Uzycie: /zamek <gracz>", NamedTextColor.RED));
            return true;
        }
        target.getInventory().addItem(createItem()).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        sender.sendMessage(Component.text("Dano przedmiot Zamek graczowi " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.DEEPSLATE_BRICKS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Zamek", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Kliknij prawym przyciskiem na blok,", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("aby postawic zamek.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ---------- klikniecie przedmiotem ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("zamek.use")) {
            p.sendMessage(Component.text("Nie masz uprawnien.", NamedTextColor.RED));
            return;
        }

        Block clicked = e.getClickedBlock();
        World w = clicked.getWorld();
        if (clicked.getY() + MAX_Y >= w.getMaxHeight()) {
            p.sendMessage(Component.text("Za wysoko, zamek sie tu nie zmiesci.", NamedTextColor.RED));
            return;
        }

        build(clicked, p.getFacing());

        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        p.sendMessage(Component.text("Postawiles zamek!", NamedTextColor.GRAY));
    }

    // ---------- budowa ----------
    private void build(Block origin, BlockFace facing) {
        int fx = facing.getModX(), fz = facing.getModZ();   // kierunek "do przodu"
        int rx = -fz, rz = fx;                              // kierunek "w prawo"
        int half = SIZE / 2;
        World w = origin.getWorld();

        for (int lx = 0; lx < SIZE; lx++) {
            for (int lz = 0; lz < SIZE; lz++) {
                for (int y = 0; y < MAX_Y; y++) {
                    Material m = blockAt(lx, y, lz);
                    int wx = origin.getX() + rx * (lx - half) + fx * lz;
                    int wz = origin.getZ() + rz * (lx - half) + fz * lz;
                    w.getBlockAt(wx, origin.getY() + y, wz).setType(m, false);
                }
            }
        }
    }

    /** Zwraca material dla lokalnych wspolrzednych zamku (x, z: 0..SIZE-1, y: 0 = podloga). Brama jest od strony z = 0. */
    private Material blockAt(int x, int y, int z) {
        int last = SIZE - 1;

        // podloga
        if (y == 0) {
            boolean edge = x == 0 || x == last || z == 0 || z == last;
            if (edge) return Material.DEEPSLATE_BRICKS;
            return ((x + z) % 2 == 0) ? Material.POLISHED_DEEPSLATE : Material.POLISHED_BLACKSTONE;
        }

        // wieze w rogach
        boolean tx = x < TOWER_SIZE || x > last - TOWER_SIZE;
        boolean tz = z < TOWER_SIZE || z > last - TOWER_SIZE;
        if (tx && tz) {
            int a = x < TOWER_SIZE ? x : x - (SIZE - TOWER_SIZE);
            int b = z < TOWER_SIZE ? z : z - (SIZE - TOWER_SIZE);
            return tower(a, y, b);
        }

        // mury
        if (x == 0 || x == last || z == 0 || z == last) {
            if (y > WALL_HEIGHT) return AIR;
            if (y == WALL_HEIGHT) return ((x + z) % 2 == 0) ? Material.BLACKSTONE : AIR; // blanki
            int gateCenter = half();
            if (z == 0 && Math.abs(x - gateCenter) <= 1 && y <= 4) return AIR;            // brama
            int p = (z == 0 || z == last) ? x : z;
            if ((p == 5 || p == last - 5) && (y == 3 || y == 4)) return GLASS;             // okna
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }

        // glowny budynek (donjon)
        if (x >= KEEP_FROM && x <= KEEP_TO && z >= KEEP_FROM && z <= KEEP_TO) {
            return keep(x, y, z);
        }

        return AIR; // dziedziniec
    }

    private int half() {
        return SIZE / 2;
    }

    private Material tower(int a, int y, int b) {
        int t = TOWER_SIZE - 1;
        boolean perimeter = a == 0 || a == t || b == 0 || b == t;
        if (y == TOWER_HEIGHT) return Material.DEEPSLATE_TILES;                            // dach
        if (y == TOWER_HEIGHT + 1) {                                                       // blanki
            return (perimeter && (a + b) % 2 == 0) ? Material.POLISHED_BLACKSTONE_BRICKS : AIR;
        }
        if (y > TOWER_HEIGHT + 1 || !perimeter) return AIR;
        if ((a == t / 2 || b == t / 2) && (y == 5 || y == 8)) return GLASS;                // okna
        return Material.DEEPSLATE_TILES;
    }

    private Material keep(int x, int y, int z) {
        boolean perimeter = x == KEEP_FROM || x == KEEP_TO || z == KEEP_FROM || z == KEEP_TO;
        if (y == KEEP_HEIGHT) return Material.DEEPSLATE_TILES;                             // dach
        if (y == KEEP_HEIGHT + 1) {                                                        // blanki
            return (perimeter && (x + z) % 2 == 0) ? Material.BLACKSTONE : AIR;
        }
        if (y > KEEP_HEIGHT + 1 || !perimeter) return AIR;
        if (z == KEEP_FROM && x == half() && y <= 2) return AIR;                           // drzwi
        int p = (z == KEEP_FROM || z == KEEP_TO) ? x : z;
        if ((p == KEEP_FROM + 1 || p == KEEP_TO - 1) && (y == 4 || y == 5)) return GLASS;  // okna
        return Material.DEEPSLATE_BRICKS;
    }
}
