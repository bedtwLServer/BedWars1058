/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.commands.debug;

import com.andrei1058.bedwars.api.language.Messages;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import static com.andrei1058.bedwars.api.language.Language.getMsg;

public class DebugCommand extends BukkitCommand {

    public DebugCommand(String name) {
        super(name);
    }

    @Override
    public boolean execute(CommandSender s, String st, String[] args) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        if (args.length < 1) {
            p.sendMessage(getMsg(p, Messages.DEBUG_COMMAND_USAGE));
        }
        switch (args[0]) {
            case "fixpos": {
                Location loc = p.getLocation();

                double x = Math.floor(loc.getX()) + 0.5;
                double y = Math.floor(loc.getY()) + 0.5;
                double z = Math.floor(loc.getZ()) + 0.5;

                float yaw = loc.getYaw();
                float fixedYaw = Math.round(yaw / 90f) * 90f;

                float pitch = loc.getPitch();

                Location fixed = new Location(loc.getWorld(), x, y, z, fixedYaw, pitch);
                p.teleport(fixed);

                p.sendMessage(getMsg(p, Messages.DEBUG_COMMAND_FIXPOS_SUCCESS));
                break;
            }
            default: {
                p.sendMessage(getMsg(p, Messages.DEBUG_COMMAND_USAGE));
                break;
            }
        }
        return true;
    }
}
