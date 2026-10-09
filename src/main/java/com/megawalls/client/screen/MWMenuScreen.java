package com.megawalls.client.screen;

import com.megawalls.menu.ClassCatalog;
import com.megawalls.network.C2S_SelectClass;
import com.megawalls.player.MWPlayerData;
import com.megawalls.client.keybind.KeyBindings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import java.util.ArrayList;
import java.util.List;

/** Cinematic lobby; actions retain the existing server-authoritative protocol. */
public class MWMenuScreen extends Screen {
    private enum Page { HOME, CLASSES, DETAIL }
    private Page page=Page.HOME;
    private int selected=1,listPage,scroll,maxScroll;
    private int left,top,panelW,contentTop,contentBottom,heroX,heroW,teamY,rows,columns;
    private boolean compact,showPortrait;
    private String notice="";
    private ButtonWidget confirm,ready,practice,manage,skin,start;
    private EpicButton classChoice;
    private final List<ButtonWidget> teams=new ArrayList<>();
    private final List<OrderedText> reading=new ArrayList<>();
    private int pendingTicks,requestedClass;
    private String requestedTeam;
    private Boolean requestedReady,requestedPractice;
    private net.minecraft.nbt.NbtCompound match(){var d=MWPlayerData.getClientLocal();return d==null?new net.minecraft.nbt.NbtCompound():d.getMatch();}
    public MWMenuScreen(){super(Text.literal("超级战墙"));var d=MWPlayerData.getClientLocal();if(d!=null&&d.getClassId()>0)selected=d.getClassId();}
    private void go(Page next){page=next;scroll=0;notice="";clearAndInit();}
    private EpicButton button(String label,int x,int y,int w,int h,int accent,Runnable click){return addDrawableChild(new EpicButton(x,y,w,h,Text.literal(label),b->click.run(),accent));}
    @Override protected void init(){
        compact=height<350||width<480;
        showPortrait=width>=480;
        panelW=Math.min(940,width-24);left=(width-panelW)/2;top=compact?10:22;
        contentTop=top+(compact?40:64);contentBottom=height-30;
        confirm=null;ready=null;practice=null;manage=null;skin=null;start=null;classChoice=null;teams.clear();reading.clear();
        if(page==Page.HOME){
            int gap=compact?4:10,col=showPortrait?(int)(panelW*.54):panelW,avail=contentBottom-contentTop;
            heroX=left+col+12;heroW=panelW-col-12;
            int ch=compact?34:52,rh=compact?22:28,bh=compact?24:38,tg=compact?14:22;
            int total=ch+gap+rh+tg+rh+gap+bh+gap+rh;
            int y=contentTop+Math.max(0,(avail-total)/2);
            var d=MWPlayerData.getClientLocal();String current=d!=null&&d.getClassId()>0?ClassCatalog.get(d.getClassId()).name():"选择你的职业";
            classChoice=button("职业档案",left,y,col,ch,EpicTheme.GOLD,()->go(Page.CLASSES)).subtitle(current+"  /  13 个职业").icon(d==null?5:Math.max(1,d.getClassId()));y+=ch+gap;
            int half=(col-gap)/2;
            skin=button("传奇衣装",left,y,half,rh,EpicTheme.PURPLE,()->client.setScreen(new MWSkinScreen(this)));
            button("战斗状态",left+half+gap,y,col-half-gap,rh,EpicTheme.PURPLE,()->client.setScreen(new MWStatusScreen(this)));
            y+=rh+tg;teamY=y;
            int count=match().getInt("teams")==4?4:2,cell=(col-(count-1)*4)/count;
            int[] colors={0xFFD97977,0xFF78A8D6,0xFF8FBD83,0xFFD9BF74};
            for(int i=0;i<count;i++){String t=com.megawalls.game.MatchTeams.ALL.get(i);teams.add(button("",left+i*(cell+4),y,cell,rh,colors[i],()->{requestedTeam=t;command("mw team "+t);}));}
            y+=rh+gap;
            ready=button("参加游戏",left,y,half,bh,EpicTheme.GOLD,()->{requestedReady=!match().getBoolean("ready");command("mw ready");});
            start=button("开始游戏",left+half+gap,y,col-half-gap,bh,EpicTheme.GOLD,()->command("mw start"));y+=bh+gap;
            practice=button("进入练习",left,y,half,rh,EpicTheme.PURPLE,()->{var local=MWPlayerData.getClientLocal();requestedPractice=local==null||!local.isPractice();command("mw practice");});
            button("按键设置",left+half+gap,y,col-half-gap,rh,EpicTheme.GOLD,()->client.setScreen(new MWSettingsScreen(this)));
            button("返回主大厅",left,height-23,76,18,EpicTheme.MUTED,()->{client.player.networkHandler.sendChatCommand("hub");close();});
            if(match().getBoolean("can_manage"))manage=button("对局设置",left+panelW-82,height-23,82,18,EpicTheme.GOLD,()->client.setScreen(new MWMatchScreen(this)));
            if(!KeyBindings.inSession()){
                ready.setMessage(Text.literal("小游戏入口"));
            }
        }else if(page==Page.CLASSES){
            columns=panelW>=560?4:panelW>=430?3:2;
            int cardH=compact?42:56;
            rows=Math.max(1,(contentBottom-contentTop-10)/(cardH+7));
            int count=rows*columns,pages=(13+count-1)/count;listPage=Math.min(listPage,pages-1);
            int cell=(panelW-(columns-1)*8)/columns;
            for(int i=0;i<count&&listPage*count+i<13;i++){
                final int id=listPage*count+i+1;var c=ClassCatalog.get(id);
                button(c.name(),left+i%columns*(cell+8),contentTop+i/columns*(cardH+7),cell,cardH,id==5?EpicTheme.PURPLE:EpicTheme.GOLD,()->{selected=id;go(Page.DETAIL);}).icon(id).subtitle(c.role());
            }
            button("返回大厅",left,height-24,74,20,EpicTheme.MUTED,()->go(Page.HOME));
            button("上一页",left+panelW-154,height-24,72,20,EpicTheme.GOLD,()->{listPage=Math.max(0,listPage-1);clearAndInit();}).active=listPage>0;
            button("下一页",left+panelW-74,height-24,74,20,EpicTheme.GOLD,()->{listPage++;clearAndInit();}).active=listPage+1<pages;
        }else{
            var c=ClassCatalog.get(selected);
            paragraph("§6主动技能  /  "+c.skill());paragraph(c.active());
            paragraph("§6被动与采集");paragraph(c.passive());
            paragraph("§6能量与释放");paragraph("剑右键、弓左键或按技能键 "+KeyBindings.skillName()+"；鼹鼠也可用铲子右键。消耗 100 能量。");
            paragraph(com.megawalls.skill.EnergyRules.describe(selected));
            maxScroll=Math.max(0,reading.size()*13-(contentBottom-contentTop-51));scroll=Math.min(scroll,maxScroll);
            button("返回职业",left,height-24,80,20,EpicTheme.MUTED,()->go(Page.CLASSES));
            confirm=button("选择 "+c.name(),left+panelW-150,height-24,150,20,EpicTheme.GOLD,()->{
                if(pendingTicks>0)return;
                if(ClientPlayNetworking.canSend(com.megawalls.network.NetworkHandler.SELECT_CLASS_ID)){
                    ClientPlayNetworking.send(new C2S_SelectClass(selected));notice="等待服务器确认…";requestedClass=selected;pendingTicks=60;
                }else notice="服务器未连接超级战墙";
            });
        }
        updateButtons();
    }
    private void paragraph(String value){reading.addAll(textRenderer.wrapLines(Text.literal(value),panelW-36));reading.add(Text.empty().asOrderedText());}
    private void command(String value){if(pendingTicks==0&&client!=null&&client.player!=null){
        if(!KeyBindings.inSession()){requestedTeam=null;requestedReady=null;requestedPractice=null;client.player.networkHandler.sendChatCommand("games");return;}
        client.player.networkHandler.sendChatCommand(value);pendingTicks=60;notice="等待服务器确认…";updateButtons();}}
    private void updateButtons(){
        var d=MWPlayerData.getClientLocal();boolean running=d!=null&&d.getPhase()>=1&&d.getPhase()<=3;
        if(classChoice!=null)classChoice.subtitle((d!=null&&d.getClassId()>0?ClassCatalog.get(d.getClassId()).name():"尚未选择")+" / 13 个职业").icon(d!=null&&d.getClassId()>0?d.getClassId():5);
        boolean session=KeyBindings.inSession();
        boolean spectating=client.player==null||!client.player.isAlive()||client.player.isSpectator();
        if(confirm!=null){confirm.active=session&&!running&&!spectating&&pendingTicks==0&&(d==null||d.getClassId()!=selected);confirm.setMessage(Text.literal(!session?"请先进入超级战墙":running?"对局中无法换职业":spectating?"请先退出观战":d!=null&&d.getClassId()==selected?"已选择 · "+ClassCatalog.get(selected).name():"选择 "+ClassCatalog.get(selected).name()));}
        var n=match();boolean waiting=session&&d!=null&&d.getPhase()==0;
        for(int i=0;i<teams.size();i++){
            String t=com.megawalls.game.MatchTeams.ALL.get(i);boolean chosen=t.equals(n.getString("team_id"));
            String label=com.megawalls.game.MatchTeams.label(t);
            teams.get(i).setMessage(Text.literal((chosen?"✓ ":"")+label+" "+n.getInt(t+"_count")));
            teams.get(i).setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(label+" · "+n.getInt(t+"_count")+" 人"+(!session?"\n请先从小游戏入口进入超级战墙":!waiting?"\n等待本局结束并重置后才能选队":chosen?"\n你当前所在的队伍":"\n换队会取消准备"))));
            teams.get(i).active=waiting&&!chosen&&pendingTicks==0;
        }
        if(ready!=null){ready.setMessage(Text.literal(!session?"小游戏入口":n.getBoolean("ready")?"退出参加":"参加游戏"));ready.active=pendingTicks==0&&(!session||waiting&&d.getClassId()>0);ready.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(!session?"打开总菜单，选择超级战墙入口":d.getClassId()==0?"请先选择职业":!waiting?"对局进行或结算时不能准备":n.getString("start_issue"))));}
        if(start!=null)start.active=session&&pendingTicks==0&&n.getBoolean("can_start");
        if(manage!=null)manage.active=n.getBoolean("can_manage");
        if(skin!=null){skin.active=session&&!running&&d!=null&&d.getClassId()==5;skin.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("末影人职业外观；请先选择末影人")));}
        if(practice!=null){practice.active=session&&!running&&d!=null&&d.getClassId()>0&&pendingTicks==0&&client.player!=null&&!client.player.isSpectator();practice.setMessage(Text.literal(d!=null&&d.isPractice()?"离开训练场":"进入训练场"));}
    }
    @Override public void tick(){
        if(pendingTicks>0)pendingTicks--;var d=MWPlayerData.getClientLocal();
        if(requestedClass>0){if(d!=null&&d.getClassId()==requestedClass){requestedClass=0;pendingTicks=0;go(Page.HOME);}else if(pendingTicks==0){requestedClass=0;notice="选择未完成，请查看提示";}}
        if(requestedTeam!=null||requestedReady!=null||requestedPractice!=null){
            boolean confirmed=requestedTeam!=null&&requestedTeam.equals(match().getString("team_id"))
                ||requestedReady!=null&&requestedReady==match().getBoolean("ready")
                ||requestedPractice!=null&&d!=null&&requestedPractice==d.isPractice();
            if(confirmed||pendingTicks==0){requestedTeam=null;requestedReady=null;requestedPractice=null;pendingTicks=0;notice=confirmed?"服务器已确认":"操作未完成，请关闭菜单查看服务器提示";}
        }
        if(page==Page.HOME&&(teams.size()!=(match().getInt("teams")==4?4:2)||(manage!=null)!=match().getBoolean("can_manage")))clearAndInit();updateButtons();
    }
    private String status(){
        if(!notice.isEmpty())return notice;if(!KeyBindings.inSession())return "请从小游戏入口进入超级战墙";var n=match();int p=n.getInt("phase"),s=n.getInt("left"),winner=n.getInt("winner");
        if(p>=1&&p<=3)return n.getString("stage")+String.format("  %02d:%02d",s/60,s%60);
        if(p==4)return (winner>0&&winner<=4?com.megawalls.game.MatchTeams.label(com.megawalls.game.MatchTeams.ALL.get(winner-1))+"胜利":"平局")+" · 击杀 "+n.getInt("kills")+" / 最终 "+n.getInt("finals");
        return "待战集结  /  已准备 "+n.getInt("ready_count")+" 人";
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        EpicTheme.background(c,width,height);
        String title=switch(page){case HOME->"超级战墙";case CLASSES->"职业档案";case DETAIL->ClassCatalog.get(selected).name();};
        EpicTheme.large(c,textRenderer,title,left,top,compact?1.5f:2.4f,EpicTheme.INK);
        String sub=switch(page){case HOME->"SAO  /  传奇之章";case CLASSES->"选择你的战斗方式";case DETAIL->ClassCatalog.get(selected).role()+"  /  "+ClassCatalog.get(selected).weapon();};
        c.drawTextWithShadow(textRenderer,sub,left,top+(compact?21:31),EpicTheme.GOLD);
        if(panelW>400){int x=left+panelW-112;EpicTheme.emblem(c,x,top,22,EpicTheme.GOLD);c.drawTextWithShadow(textRenderer,"TEAMY / 天门",x+28,top+4,EpicTheme.INK);c.drawTextWithShadow(textRenderer,"MEGA WALLS",x+28,top+17,EpicTheme.MUTED);}
        if(page==Page.HOME){
            if(showPortrait){
            c.fillGradient(heroX,contentTop,heroX+heroW,contentBottom,0x44131020,0xBD0C0B14);
            c.drawBorder(heroX,contentTop,heroW,contentBottom-contentTop,0xFF5C4465);
            c.fill(heroX,contentTop,heroX+30,contentTop+2,EpicTheme.PURPLE);
            c.fill(heroX+heroW-30,contentBottom-2,heroX+heroW,contentBottom,EpicTheme.GOLD);
            String playerName=PlayerIdentity.name();
            c.drawTextWithShadow(textRenderer,"玩家档案",heroX+12,contentTop+10,EpicTheme.PURPLE);
            if(!compact)c.drawTextWithShadow(textRenderer,"MEGA WALLS",heroX+12,contentTop+25,EpicTheme.MUTED);
            EpicPortrait.render(c,heroX+4,contentTop+(compact?23:34),heroX+heroW-4,contentBottom-60,Math.min(110,(contentBottom-contentTop)/2),mx,my);
            c.fill(heroX+12,contentBottom-59,heroX+heroW-12,contentBottom-58,0xFF544160);
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(playerName,heroW-24),heroX+12,contentBottom-50,EpicTheme.INK);
            EpicTheme.face(c,PlayerIdentity.skin().texture(),heroX+12,contentBottom-34,22);
            c.drawTextWithShadow(textRenderer,PlayerIdentity.role(playerName),heroX+42,contentBottom-34,EpicTheme.PURPLE);
            if(PlayerIdentity.member())c.drawTextWithShadow(textRenderer,"TEAMY / 天门",heroX+42,contentBottom-20,EpicTheme.GOLD);
            }else{
                EpicTheme.face(c,PlayerIdentity.skin().texture(),left+panelW-24,top+1,22);
                c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(PlayerIdentity.name(),76),left+panelW-104,top+9,EpicTheme.MUTED);
            }
            c.drawTextWithShadow(textRenderer,"选择阵营",left,teamY-12,EpicTheme.MUTED);
            int space=panelW-(manage==null?82:172);if(space>80)c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status(),space),left+80,height-18,EpicTheme.GOLD);
        }else if(page==Page.CLASSES){
            int count=rows*columns;c.drawTextWithShadow(textRenderer,(listPage+1)+" / "+((13+count-1)/count),left+84,height-18,EpicTheme.MUTED);
        }else{
            EpicTheme.panel(c,left,contentTop,panelW,contentBottom-contentTop-2);
            EpicTheme.icon(c,selected,left+12,contentTop+8,28);var info=ClassCatalog.get(selected);
            c.drawTextWithShadow(textRenderer,info.skill(),left+50,contentTop+9,EpicTheme.GOLD);
            int cooldown=match().getInt("cooldown_"+selected);
            String timing=cooldown>0?String.format(java.util.Locale.ROOT,"%.2f",cooldown/20d).replaceAll("0+$", "").replaceAll("\\.$", ""):Integer.toString(info.cooldown());
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(notice.isEmpty()?timing+" 秒冷却  /  100 能量":notice,panelW-66),left+50,contentTop+24,notice.isEmpty()?EpicTheme.MUTED:EpicTheme.GOLD);
            int start=contentTop+47,end=contentBottom-5;c.fill(left+12,start-6,left+panelW-12,start-5,0xFF3D3349);
            c.enableScissor(left+10,start,left+panelW-10,end);
            for(int i=0;i<reading.size();i++){int y=start+i*13-scroll;if(y+12>=start&&y<end)c.drawTextWithShadow(textRenderer,reading.get(i),left+16,y,EpicTheme.INK);}c.disableScissor();
            if(maxScroll>0){int track=end-start,thumb=Math.max(12,track*track/(track+maxScroll));int y=start+(track-thumb)*scroll/maxScroll;c.fill(left+panelW-6,start,left+panelW-4,end,0xFF39313F);c.fill(left+panelW-6,y,left+panelW-4,y+thumb,EpicTheme.PURPLE);}
            if(!notice.isEmpty()&&panelW>400)c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(notice,panelW-252),left+90,height-18,EpicTheme.GOLD);
        }
        for(var child:children())if(child instanceof net.minecraft.client.gui.Drawable d)d.render(c,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(page==Page.DETAIL){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(vertical*26)));return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(page==Page.DETAIL&&(key==264||key==265||key==266||key==267)){int step=key==266||key==267?contentBottom-contentTop-50:26;scroll=Math.max(0,Math.min(maxScroll,scroll+(key==264||key==267?step:-step)));return true;}
        if(key==256&&page!=Page.HOME){go(page==Page.DETAIL?Page.CLASSES:Page.HOME);return true;}return super.keyPressed(key,scan,mods);
    }
    @Override public void removed(){EpicPortrait.clear();}
    @Override public boolean shouldPause(){return false;}
}
