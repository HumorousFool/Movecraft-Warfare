package net.countercraft.movecraft.warfare.events;

import net.countercraft.movecraft.warfare.assault.Assault;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AssaultStartEvent extends AssaultEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public AssaultStartEvent(@NotNull Assault assault) {
        super(assault);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}