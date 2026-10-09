package com.megawalls.skill;
import com.megawalls.passive.*;
import com.megawalls.player.MWPlayerData;
import com.megawalls.game.GameState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
public final class SkillStatus {
    public static String seconds(int ticks){return String.format(java.util.Locale.ROOT,"%.1fs",ticks/20d);}
    private static String cd(ServerPlayerEntity p,String key){int n=PassiveHandler.remaining(p,key);return n>0?"冷却 "+seconds(n):"可触发";}
    private static String buff(ServerPlayerEntity p,String condition){int n=LegacyBattle.buffRemaining(p);return n>0?"生效 "+seconds(n):condition;}
    public static NbtCompound snapshot(ServerPlayerEntity p){
        var n=new NbtCompound();var d=MWPlayerData.getServer(p);int cls=d.getClassId();String a="",b="",g="";
        switch(cls){
            case 1->{a="坚韧：近战受击37.5% | 狂暴："+cd(p,"berserk");b="狂暴增伤："+buff(p,"箭受击24%");g="训练有素："+(GameState.phase(p)==1?"准备期急迫III":"采矿/伐木90%");}
            case 2->{a="敏捷："+cd(p,"agile")+"（7秒效果后4秒恢复）";b="回收：箭命中90%返箭";g="效率：矿石/原木双倍掉落";}
            case 3->{a="落震：摔伤至少4HP，2倍伤害，最多14HP";b="虫巢：死亡召唤3只蜘蛛";g="寻铁：天然泥土/沙/沙砾产铁";}
            case 4->{a="复生："+cd(p,"rejuvenate")+"（低于21HP）";b="墨汁：喝药触发致盲";g="海之眷顾：宝箱概率吸收药水";}
            case 5->{a="灵魂充能："+cd(p,"soulcharge");b="末影之心：可复活死亡39%保包";g="末影方块："+(GameState.phase(p)==3?"死斗禁用":"采集36%连锁挖掘");}
            case 6->{a="疾风：近战命中75%";b="力量："+buff(p,"击杀触发6秒");g="宝藏猎人：准备期挖矿 "+Math.round(MiningTreasure.rules().herobrineChestChance*100)+"%";}
            case 7->{a="意志："+cd(p,"willpower")+"（低于14HP）";b="裂变之心：死亡召唤";g="TNT采集：煤矿触发";}
            case 8->{a="灵魂吞噬："+cd(p,"souleater")+"（近战20%）";b="灵魂虹吸："+buff(p,"击杀触发5秒");g="暗物质：铁矿熔炼";}
            case 9->{a="钢铁之心："+cd(p,"ironheart");b="钢铁体质：箭受击抗性";g="化木为铁：伐木10%";}
            case 10->{a="熔火核心："+cd(p,"melting");b="烈焰召唤：击杀召唤2只";g="化石燃料："+cd(p,"fossil");}
            case 11->{a="自然之力："+seconds(PassiveHandler.natureRemaining(p));b="动物伙伴："+cd(p,"animal")+"（受击8%）";g="黄金眼镜：夜视 | 宝箱金苹果";}
            case 12->{a="捷径：铲类采集20%";b="能量曲奇：准备期采集2.1%";g="宝箱铁锭增加 | 曲奇+4能量";}
            case 13->{a="嗜血：连击 "+PassiveHandler.combo(p)+"/3（受伤重置）";b="吞食："+cd(p,"devour");g="肉食：击杀牛排 | 变身近战触发范围伤害";}
        }
        if(!GameState.active(p)){a="未进入战斗/练习";b="被动未启用";g="采集天赋未启用";}
        n.putInt("eps_hits_remaining",GameState.active(p)&&EnergyRules.needsHits(cls)?Math.max(0,2-d.getEpsHits()):0);
        n.putString("passive1",a);n.putString("passive2",b);n.putString("gathering",g);
        n.putInt("duration",cls==11?HunterTracker.remaining(p):cls==13?WerewolfCombat.remaining(p):0);
        n.putString("tracker",com.megawalls.item.PlayerCompass.label(com.megawalls.item.PlayerCompass.selected(p)));
        // Compact combat HUD is a separate server snapshot; detailed descriptions stay in the menu.
        String[] names=switch(cls){
            case 1->new String[]{"坚韧","狂暴"};case 2->new String[]{"敏捷","回收"};
            case 3->new String[]{"落震","虫巢"};case 4->new String[]{"复生","墨汁"};
            case 5->new String[]{"灵魂充能","末影之心"};case 6->new String[]{"疾风","力量"};
            case 7->new String[]{"意志","裂变之心"};case 8->new String[]{"灵魂吞噬","灵魂虹吸"};
            case 9->new String[]{"钢铁之心","钢铁体质"};case 10->new String[]{"熔火核心","烈焰召唤"};
            case 11->new String[]{"自然之力","动物伙伴"};case 12->new String[]{"捷径","能量曲奇"};
            case 13->new String[]{"嗜血","吞食"};default->new String[]{"",""};
        };
        String first="",second="";
        switch(cls){
            case 1->second=LegacyBattle.buffRemaining(p)>0?seconds(LegacyBattle.buffRemaining(p)):shortCd(p,"berserk");
            case 2->first=shortCd(p,"agile");
            case 3->first="≥4HP摔伤";
            case 4->first=shortCd(p,"rejuvenate");
            case 5->first=shortCd(p,"soulcharge");
            case 6,8->{second=LegacyBattle.buffRemaining(p)>0?seconds(LegacyBattle.buffRemaining(p)):"";if(cls==8)first=shortCd(p,"souleater");}
            case 7->first=shortCd(p,"willpower");case 9->first=shortCd(p,"ironheart");
            case 10->first=shortCd(p,"melting");
            case 11->{first=seconds(PassiveHandler.natureRemaining(p));second=shortCd(p,"animal");}
            case 13->{first=PassiveHandler.combo(p)+"/3";second=shortCd(p,"devour");}
        }
        if(!GameState.active(p)){first="—";second="—";}
        n.putString("hud_passive1",names[0]+(first.isEmpty()?"":" "+first));
        n.putString("hud_passive2",names[1]+(second.isEmpty()?"":" "+second));
        return n;
    }
    private static String shortCd(ServerPlayerEntity p,String key){int ticks=PassiveHandler.remaining(p,key);return ticks>0?seconds(ticks):"";}
}
