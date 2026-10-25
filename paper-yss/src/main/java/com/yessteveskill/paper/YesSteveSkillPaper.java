package com.yessteveskill.paper;
import com.yessteveattribute.paper.PaperAttributeService;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.plugin.RegisteredServiceProvider; import org.bukkit.plugin.java.JavaPlugin;
public final class YesSteveSkillPaper extends JavaPlugin implements Listener {
 private PaperAttributeService ysa;
 @Override public void onEnable(){RegisteredServiceProvider<PaperAttributeService> p=getServer().getServicesManager().getRegistration(PaperAttributeService.class);if(p==null){getServer().getPluginManager().disablePlugin(this);return;}ysa=p.getProvider();getServer().getPluginManager().registerEvents(this,this);}
 @EventHandler public void onMechanic(MythicMechanicLoadEvent e){String n=e.getMechanicName().toLowerCase(java.util.Locale.ROOT);if(n.equals("ysadamage")||n.equals("ysa_damage"))e.register(new YsaDamageMechanic(e.getConfig(),ysa));}
 @EventHandler public void onCondition(MythicConditionLoadEvent e){String n=e.getConditionName().toLowerCase(java.util.Locale.ROOT);if(n.equals("ysaattribute")||n.equals("ysa_attribute"))e.register(new YsaAttributeCondition(e.getConfig(),ysa));}
}
