package net.acomputerdog.enderpull;

import net.minecraft.server.v1_11_R1.*;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_11_R1.inventory.CraftInventoryPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

public class PluginEnderPull extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player)sender;
            switch (command.getName()) {
                case "enderpull":
                    if (sender.hasPermission("enderpull.ender")) {
                        pullInvs(p, true, false);
                        break;
                    }
                case "invpull":
                    if (sender.hasPermission("enderpull.inventory")) {
                        pullInvs(p, false, true);
                        break;
                    }
                case "allpull":
                    if (sender.hasPermission("enderpull.all")) {
                        pullInvs(p, true, true);
                        break;
                    }
                    //if player does not have permission, then switch block will drop to this point
                    sender.sendMessage(ChatColor.RED + "You do not have permission!");
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown command, please report this!");
                    break;
            }
        }
        return true;
    }

    private void pullInvs(Player player, boolean doEnders, boolean doInvs) {
        int numEnders = 0;
        World defWorld = getServer().getWorlds().get(0);
        for (World world : getServer().getWorlds()) {
            if (world != defWorld) {
                File uidDatFile = new File(world.getWorldFolder(), "/playerdata/" + player.getUniqueId().toString() + ".dat");
                File nameDatFile = new File(world.getWorldFolder(), "/playerdata/" + player.getName() + ".dat");
                try {
                    if (uidDatFile.isFile()) {
                        pullInv(player, uidDatFile, doEnders, doInvs);
                        numEnders++;
                    } else if (nameDatFile.isFile()) {
                        pullInv(player, nameDatFile, doEnders, doInvs);
                        numEnders++;
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "An exception occurred pulling from \"" + world.getName() + "\"!  In the case of save file damage, a backup has been created.");
                    e.printStackTrace();
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "Skipping current world \"" + world.getName() + "\".");
            }
        }
        player.sendMessage(ChatColor.AQUA + "Successfully pulled from " + numEnders + " worlds.");
    }

    private void pullInv(Player player, File datFile, boolean doEnder, boolean doInv) throws IOException {
        File backupFile = new File(datFile.getPath() + ".bak"); //backup player file in case of errors
        backup(datFile, backupFile);

        InputStream in = new FileInputStream(datFile);
        NBTTagCompound root = NBTCompressedStreamTools.a(in); //load the player file
        in.close();


        CraftInventoryPlayer inventory = (CraftInventoryPlayer) player.getInventory();
        int numItems = 0;

        if (doEnder) {
            NBTTagList ender = root.getList("EnderItems", 10);
            numItems += copyList(ender, player, inventory);
        }
        if (doInv) {
            NBTTagList inv = root.getList("Inventory", 10);
            numItems += copyList(inv, player, inventory);
        }

        OutputStream out = new FileOutputStream(datFile);
        NBTCompressedStreamTools.a(root, out); //save the player file
        out.close();

        player.sendMessage(ChatColor.AQUA + "Pulled " + numItems + " items.");
    }

    private int copyList(NBTTagList inv, Player player, CraftInventoryPlayer inventory) {
        int numItems = 0;
        for (int i = 0; i < inv.size(); i++) {
            NBTTagCompound item = inv.get(i);
            upgradeItem(player, item);
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) {
                if (inventory.getInventory().canHold(stack) >= stack.getCount()) { //if the stack can fit
                    inventory.getInventory().pickup(stack); //add the stack
                    inv.remove(i); //remove from the old file
                    i--; //decrement because remove put the next item in this index
                    numItems++;
                } else {
                    player.sendMessage(ChatColor.YELLOW + "No room for item of type \"" + stack.getItem().getName() + "\".  It will be left in its current place.");
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "Unable to load item: " + item.toString());
            }
        }
        return numItems;
    }

    private void backup(File f1, File f2) throws IOException {
        try (InputStream in = new FileInputStream(f1)) {
            try (OutputStream out = new FileOutputStream(f2)) {
                while (in.available() > 0) {
                    out.write(in.read());
                }
            }
        }
    }

    /*
      Upgrades older worlds and fixes corruption on newer ones to ensure that NBT data is valid.
     */
    private void upgradeItem(Player p, NBTTagCompound item) {
        NBTBase id = item.get("id");
        if (id instanceof NBTTagShort) {
            //get the item name
            String name = Item.REGISTRY.b(Item.getById(((NBTTagShort)id).e())).a();
            item.setString("id", formatName(name));

        } else if (id instanceof NBTTagString) { //fix a name corruption bug caused by earlier plugin versions
            String name = ((NBTTagString)id).c_(); //get original name
            if (Item.b(name) == null) { //if name is not an item
                p.sendMessage("Item has invalid name, we will attempt to convert it.");
                if (name.startsWith("minecraft:tile.")) { //convert block
                    renameBlock(name, p, item);
                } else { //convert item
                    renameItem(name, p, item);
                }
            }

        } else {
            p.sendMessage(ChatColor.YELLOW + "Unknown item format, upgrade may fail!");
        }
    }

    /*
       Renames a corrupted block NBT structure
     */
    private static void renameBlock(String name, Player p, NBTTagCompound item) {
        name = name.replace("minecraft:", "");
        if (!name.startsWith("tile.")) { //make sure name starts with block.
            name = "tile." + name;
        }
        p.sendMessage("Looking for block called: " + name);
        for (Block block : Block.REGISTRY) {  //check each block
            if (name.equals(block.a())) { //if the name matches, then replace name with block id.
                String realName = Block.REGISTRY.b(block).a(); //get the id from registry
                item.setString("id", formatName(realName)); //replace the name
                p.sendMessage("Matching block id found: " + realName);
                break;
            }
        }
    }

    /*
      Renames a corrupted item NBT structure
     */
    private static void renameItem(String name, Player p, NBTTagCompound item) {
        int prefixIdx = name.indexOf(":");
        if (prefixIdx >= 0) {  //strip out minecraft: prefix
            name = name.substring(prefixIdx + 1);
        }
        if (!name.startsWith("item.")) { //make sure name starts with item.
            name = "item." + name;
        }
        p.sendMessage("Looking for item called: " + name);
        for (Item itm : Item.REGISTRY) {  //check each item
            if (name.equals(itm.getName())) { //if the name matches, then replace name with item id.
                String realName = Item.REGISTRY.b(itm).a(); //get the id from registry
                item.setString("id", formatName(realName)); //replace the name
                p.sendMessage("Matching item id found: " + realName);
                break;
            }
        }
    }

    private static String formatName(String name) {
        //stips out "item." and adds "minecraft:" prefix if needed
        if (name.length() > 5 && name.startsWith("item.")) {
            name = name.substring(5);
        }
        if (name.indexOf(':') == -1) {
            name = "minecraft:" + name;
        }
        return name;
    }
}
