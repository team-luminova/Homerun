package net.chlod.minecraft.homerun.command

import org.bukkit.permissions.Permission

interface IHomerunCommand {

    val name: String
    val description: String
    val permission: Permission

}