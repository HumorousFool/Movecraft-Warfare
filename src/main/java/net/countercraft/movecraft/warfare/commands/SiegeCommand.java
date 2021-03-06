package net.countercraft.movecraft.warfare.commands;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.repair.MovecraftRepair;
import net.countercraft.movecraft.warfare.config.Config;
import net.countercraft.movecraft.warfare.localisation.I18nSupport;
import net.countercraft.movecraft.utils.TopicPaginator;
import net.countercraft.movecraft.warfare.MovecraftWarfare;
import net.countercraft.movecraft.warfare.events.SiegePreStartEvent;
import net.countercraft.movecraft.warfare.siege.Siege;
import net.countercraft.movecraft.warfare.siege.SiegeManager;
import net.countercraft.movecraft.warfare.siege.SiegeStage;
import net.countercraft.movecraft.worldguard.MovecraftWorldGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Level;

import static net.countercraft.movecraft.utils.ChatUtils.MOVECRAFT_COMMAND_PREFIX;

public class SiegeCommand implements TabExecutor {
    //TODO: Add tab complete
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (!command.getName().equalsIgnoreCase("siege")) {
            return false;
        }
        if (!Config.SiegeEnable || MovecraftWarfare.getInstance().getSiegeManager().getSieges().size() == 0) {
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Siege Not Configured"));
            return true;
        }
        if (!commandSender.hasPermission("movecraft.siege")) {
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return true;
        }
        if (args.length == 0) {
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - No Argument"));
            return true;
        }

        if(args[0].equalsIgnoreCase("list")){
            return listCommand(commandSender, args);
        } else if (args[0].equalsIgnoreCase("begin")) {
            return beginCommand(commandSender);
        } else if(args[0].equalsIgnoreCase("info")){
            return infoCommand(commandSender,args);
        } else if(args[0].equalsIgnoreCase("time")){
            return timeCommand(commandSender,args);
        } else if(args[0].equalsIgnoreCase("cancel")){
            return cancelCommand(commandSender,args);
        }
        commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Invalid Argument"));
        return true;

    }

    private boolean cancelCommand(CommandSender commandSender, String[] args) {
        if (!commandSender.hasPermission("movecraft.siege.cancel")) {
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return true;
        }
        if(args.length <=1 ) {
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Specify Region"));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for(int i = 1; i < args.length; i++) {
            if(i > 1) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        String region = sb.toString();

        for(Siege siege : MovecraftWarfare.getInstance().getSiegeManager().getSieges()) {
            if(siege.getStage().get() == SiegeStage.INACTIVE) {
                continue;
            }
            if(!region.equalsIgnoreCase(siege.getName())) {
                continue;
            }

            cancelSiege(siege);
        }
        return true;
    }

    private boolean timeCommand(CommandSender commandSender, String[] args) {
        int militaryTime = getMilitaryTime();
        commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + dayToString(getDayOfWeek()) + " - " + String.format("%02d", militaryTime / 100) + ":" + String.format("%02d",militaryTime % 100));
        return true;
    }

    private void cancelSiege(Siege siege) {
        @NotNull Player siegeLeader = Movecraft.getInstance().getServer().getPlayer(siege.getPlayerUUID());
        Bukkit.getServer().broadcastMessage(String.format(I18nSupport.getInternationalisedString("Siege - Siege Failure"),
                siege.getName(), siegeLeader.getDisplayName()));

        siege.setStage(SiegeStage.INACTIVE);

        List<String> commands = siege.getCommandsOnLose();
        for (String command : commands) {
            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command
                    .replaceAll("%r", siege.getCaptureRegion())
                    .replaceAll("%c", "" + siege.getCost())
                    .replaceAll("%l", siegeLeader.toString()));
        }
    }

    private boolean infoCommand(CommandSender commandSender, String[] args){
        if(args.length <=1){
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Specify Region"));
            return true;
        }
        String siegeName = String.join(" ", Arrays.copyOfRange(args, 1,args.length));
        Siege siege = null;
        for(Siege searchSiege : MovecraftWarfare.getInstance().getSiegeManager().getSieges()){
            if(searchSiege.getName().equalsIgnoreCase(siegeName)){
                siege = searchSiege;
                break;
            }
        }
        if(siege == null){
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Siege Region Not Found"));
            return true;
        }
        displayInfo(commandSender, siege);
        return true;

    }

    private boolean listCommand(CommandSender commandSender, String[] args){
        SiegeManager siegeManager = MovecraftWarfare.getInstance().getSiegeManager();
        int page;
        try {
            if (args.length <= 1)
                page = 1;
            else
                page = Integer.parseInt(args[1]);
        }catch(NumberFormatException e){
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Paginator - Invalid Page") +" \"" + args[1] + "\"");
            return true;
        }
        TopicPaginator paginator = new TopicPaginator("Sieges");
        for (Siege siege : siegeManager.getSieges()) {
            paginator.addLine("- " + siege.getName());
        }
        if(!paginator.isInBounds(page)){
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Paginator - Invalid Page") +" \"" + args[1] + "\"");
            return true;
        }
        for(String line : paginator.getPage(page))
            commandSender.sendMessage(line);
        return true;
    }

    private boolean beginCommand(CommandSender commandSender){

        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Must Be Player"));
            return true;
        }
        SiegeManager siegeManager = MovecraftWarfare.getInstance().getSiegeManager();
        Player player = (Player) commandSender;

        for (Siege siege : siegeManager.getSieges()) {
            if (siege.getStage().get() != SiegeStage.INACTIVE) {
                player.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Siege Already Underway"));
                return true;
            }
        }
        Siege siege = getSiege(player, siegeManager);

        if (siege == null) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - No Configuration Found"));
            return true;
        }
        long cost = calcSiegeCost(siege, siegeManager, player);

        if (!MovecraftRepair.getInstance().getEconomy().has(player, cost)) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX + String.format(I18nSupport.getInternationalisedString("Siege - Insufficient Funds"),cost));
            return true;
        }

        int currMilitaryTime = getMilitaryTime();
        int dayOfWeek = getDayOfWeek();
        if (currMilitaryTime <= siege.getScheduleStart() || currMilitaryTime >= siege.getScheduleEnd() || !siege.getDaysOfWeek().contains(dayOfWeek)) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Siege - Time Not During Schedule"));
            return true;
        }

        //check if piloting craft in siege region
        Craft siegeCraft = CraftManager.getInstance().getCraftByPlayer(player);
        if(siegeCraft == null) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("You must be piloting a craft!"));
            return true;
        }
        if(!siege.getCraftsToWin().contains(siegeCraft.getType().getCraftName())) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("You must be piloting a craft that can siege!"));
            return true;
        }
        if(!MovecraftWorldGuard.getInstance().getWGUtils().craftFullyInRegion(siege.getAttackRegion(), siegeCraft.getW(), siegeCraft)) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("You must be piloting a craft in the siege region!"));
            return true;
        }

        SiegePreStartEvent siegePreStartEvent = new SiegePreStartEvent(siege);
        Bukkit.getPluginManager().callEvent(siegePreStartEvent);

        if (siegePreStartEvent.isCancelled()) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX + siegePreStartEvent.getCancelReason());
            return true;
        }

        startSiege(siege, player, cost);
        return true;
    }

    private void startSiege(Siege siege, Player player, long cost) {
        for (String startCommand : siege.getCommandsOnStart()) {
            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), startCommand.replaceAll("%r", siege.getAttackRegion()).replaceAll("%c", "" + siege.getCost()));
        }
        Bukkit.getServer().broadcastMessage(String.format(I18nSupport.getInternationalisedString("Siege - Siege About To Begin")
                , player.getDisplayName(), siege.getName()) + String.format(I18nSupport.getInternationalisedString("Siege - Ending In X Minutes"), siege.getDelayBeforeStart() / 60));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1, 0.25F);
        }
        Movecraft.getInstance().getLogger().log(Level.INFO, String.format(I18nSupport.getInternationalisedString("Siege - Log Siege Start"), siege.getName(), player.getName(), cost));
        MovecraftRepair.getInstance().getEconomy().withdrawPlayer(player, cost);
        siege.setPlayerUUID(player.getUniqueId());
        siege.setStartTime(System.currentTimeMillis());
        siege.setStage(SiegeStage.PREPERATION);
    }

    private int getMilitaryTime() {
        Calendar rightNow = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int hour = rightNow.get(Calendar.HOUR_OF_DAY);
        int minute = rightNow.get(Calendar.MINUTE);
        return hour * 100 + minute;
    }

    private int getDayOfWeek() {
        Calendar rightNow = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        return rightNow.get(Calendar.DAY_OF_WEEK);
    }

    @Nullable
    private Siege getSiege(Player player, SiegeManager siegeManager) {
        Set<String> regions = MovecraftWorldGuard.getInstance().getWGUtils().getRegions(player.getLocation());
        for(String region : regions) {
            for(Siege siege : siegeManager.getSieges()) {
                if(siege.getAttackRegion().equalsIgnoreCase(region))
                    return siege;
            }
        }
        return null;
    }

    private long calcSiegeCost(Siege siege, SiegeManager siegeManager, Player player) {
        long cost = siege.getCost();
        for (Siege tempSiege : siegeManager.getSieges()) {
            Set<UUID> regionOwners = MovecraftWorldGuard.getInstance().getWGUtils().getUUIDOwners(tempSiege.getCaptureRegion(), player.getWorld());
            if(regionOwners == null)
                continue;

            if (tempSiege.isDoubleCostPerOwnedSiegeRegion() && regionOwners.contains(player.getUniqueId()))
                cost *= 2;
        }
        return cost;
    }

    private String militaryTimeIntToString(int militaryTime) {
        return String.format("%02d", militaryTime / 100) + ":" + String.format("%02d",militaryTime % 100);
    }

    private String secondsIntToString(int seconds) {
        return String.format("%02d", seconds / 60) + ":" + String.format("%02d",seconds % 60);
    }

    private String dayToString(int day){
        String output = "Error";
        switch (day){
            case 1:
                output = "Siege - Sunday";
                break;
            case 2:
                output = "Siege - Monday";
                break;
            case 3:
                output = "Siege - Tuesday";
                break;
            case 4:
                output = "Siege - Wednesday";
                break;
            case 5:
                output = "Siege - Thursday";
                break;
            case 6:
                output = "Siege - Friday";
                break;
            case 7:
                output = "Siege - Saturday";
                break;
        }
        output = I18nSupport.getInternationalisedString(output);
        return output;
    }

    private String daysOfWeekString(@NotNull List<Integer> days) {
        String str = new String();
        for(int i = 0; i < days.size(); i++) {
            if(days.get(i) == getDayOfWeek()) {
                str += ChatColor.GREEN;
            }
            else {
                str += ChatColor.RED;
            }
            str += dayToString(days.get(i));

            if(i != days.size()-1) {
                str += ChatColor.WHITE + ", ";
            }
        }
        return str;
    }

    private void displayInfo(@NotNull CommandSender sender, @NotNull Siege siege) {
        sender.sendMessage("" + ChatColor.YELLOW + ChatColor.BOLD  + "----- " + ChatColor.RESET + ChatColor.GOLD + siege.getName() + ChatColor.YELLOW + ChatColor.BOLD +" -----");
        ChatColor cost, start, end;

        if(sender instanceof Player) {
            cost = MovecraftRepair.getInstance().getEconomy().has((Player) sender, siege.getCost()) ? ChatColor.GREEN : ChatColor.RED;
        }
        else {
            cost = ChatColor.DARK_RED;
        }

        start = siege.getScheduleStart() < getMilitaryTime() ? ChatColor.GREEN : ChatColor.RED;
        end = siege.getScheduleEnd() > getMilitaryTime() ? ChatColor.GREEN : ChatColor.RED;

        sender.sendMessage(I18nSupport.getInternationalisedString("Siege - Siege Cost") + cost + currencyFormat.format(siege.getCost()));
        sender.sendMessage(I18nSupport.getInternationalisedString("Siege - Daily Income") + ChatColor.WHITE + currencyFormat.format(siege.getDailyIncome()));
        sender.sendMessage(I18nSupport.getInternationalisedString("Siege - Day of Week") + daysOfWeekString(siege.getDaysOfWeek()));
        sender.sendMessage(I18nSupport.getInternationalisedString("Siege - Start Time") + start + militaryTimeIntToString(siege.getScheduleStart()) + " UTC");
        sender.sendMessage(I18nSupport.getInternationalisedString("Siege - End Time") + end + militaryTimeIntToString(siege.getScheduleEnd()) + " UTC");
        sender.sendMessage(I18nSupport.getInternationalisedString("Siege - Duration") + ChatColor.WHITE + secondsIntToString(siege.getDuration()));
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        final List<String> tabCompletions = new ArrayList<>();
        if (strings.length <= 1) {
            tabCompletions.add("info");
            tabCompletions.add("begin");
            tabCompletions.add("list");
            tabCompletions.add("time");
            tabCompletions.add("cancel");
        } else if (strings[0].equalsIgnoreCase("info")) {
            for (Siege siege : MovecraftWarfare.getInstance().getSiegeManager().getSieges()) {
                tabCompletions.add(siege.getName());
            }
        }
        else if(strings[0].equalsIgnoreCase("cancel")) {
            for (Siege siege : MovecraftWarfare.getInstance().getSiegeManager().getSieges()) {
                if(siege.getStage().get() == SiegeStage.INACTIVE) {
                    continue;
                }
                tabCompletions.add(siege.getName());
            }
        }
        if (strings.length == 0) {
            return tabCompletions;
        }
        final List<String> completions = new ArrayList<>();
        for (String completion : tabCompletions) {
            if (!completion.startsWith(strings[strings.length - 1])) {
                continue;
            }
            completions.add(completion);
        }
        return completions;
    }
}