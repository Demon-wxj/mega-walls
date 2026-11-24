package com.megawalls.maintenance.client;

import com.megawalls.client.screen.EpicButton;
import com.megawalls.client.screen.EpicTheme;
import com.megawalls.maintenance.MatchTuning;
import com.megawalls.maintenance.SettingsPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

public final class MatchTuningScreen extends Screen {
    private final Screen parent;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, TextFieldWidget> fields = new LinkedHashMap<>();
    private NbtCompound state = new NbtCompound();
    private int page, rows, left, top, panelWidth, panelHeight, waitTicks;
    private static final String[] GROUPS={"赛制","队伍凋零","守卫技能","暴怒技能"};
    private int group;private java.util.List<MatchTuning.Rule> shown=java.util.List.of();
    private static int groupOf(String k){if(k.startsWith("wither_")||k.equals("core_hp"))return 1;
        if(k.equals("core_reach")||k.startsWith("core_skull")||k.startsWith("core_wave")||k.equals("core_charge"))return 2;return k.startsWith("core_")?3:0;}
    private boolean loaded, busy;
    private String message = "正在读取服务器参数…";

    public MatchTuningScreen(Screen parent) { super(Text.literal("对局参数")); this.parent = parent; }
    private int pages() { return Math.max(1,(shown.size()+rows-1)/rows); }
    private ButtonWidget button(String text, int x, int y, int width, Runnable action) {
        return addDrawableChild(new EpicButton(x,y,width,21,Text.literal(text), b -> action.run(), -3232399));
    }
    @Override protected void init() {
        fields.clear();
        panelWidth = Math.min(560,width-16); panelHeight = Math.min(350,height-12);
        left=(width-panelWidth)/2; top=(height-panelHeight)/2;
        shown=MatchTuning.RULES.stream().filter(r->groupOf(r.key())==group).toList();
        rows=Math.max(1,(panelHeight-169)/25); page=Math.min(page,pages()-1);
        int bottom=top+panelHeight, inner=panelWidth-24, col=(inner-8)/2, third=(inner-16)/3;
        int tab=inner/4;for(int i=0;i<4;i++){int j=i;button(GROUPS[i],left+12+i*tab,top+49,tab-2,()->{group=j;page=0;clearAndInit();}).active=i!=group&&!busy;}
        for (int i=0;i<rows && page*rows+i<shown.size();i++) {
            var rule=shown.get(page*rows+i);
            int y=top+74+i*25, fieldX=left+panelWidth-113;
            TextFieldWidget field=new TextFieldWidget(textRenderer,fieldX,y,66,21,Text.literal(rule.label()));
            field.setMaxLength(5); field.setText(values.getOrDefault(rule.key(),Integer.toString(rule.initial())));
            field.setChangedListener(value -> values.put(rule.key(),value));
            field.setTooltip(Tooltip.of(Text.literal(rule.help()+" 范围："+rule.min()+"～"+rule.max()+rule.unit())));
            field.setEditable(loaded && !busy && state.getBoolean("editable"));
            addDrawableChild(field); fields.put(rule.key(),field);
            button("−",fieldX-26,y,22,()->adjust(rule,-rule.step())).active=loaded&&!busy&&state.getBoolean("editable");
            button("+",fieldX+70,y,22,()->adjust(rule,rule.step())).active=loaded&&!busy&&state.getBoolean("editable");
        }
        button("上一页",left+12,bottom-80,third,()->{page--;clearAndInit();}).active=page>0;
        button("刷新参数",left+20+third,bottom-80,third,this::request).active=!busy;
        button("下一页",left+28+third*2,bottom-80,third,()->{page++;clearAndInit();}).active=page+1<pages();
        button("常规预设",left+12,bottom-55,col,()->preset(false)).active=loaded&&!busy&&state.getBoolean("editable");
        button("快局预设",left+20+col,bottom-55,col,()->preset(true)).active=loaded&&!busy&&state.getBoolean("editable");
        button("保存全部参数",left+12,bottom-30,col,this::save).active=loaded&&!busy&&state.getBoolean("editable");
        button("返回",left+20+col,bottom-30,col,this::close);
        if(!loaded&&!busy) request();
    }
    private void adjust(MatchTuning.Rule rule,int by) {
        int value;
        try { value=Integer.parseInt(values.getOrDefault(rule.key(),"")); } catch(NumberFormatException e) { value=rule.initial(); }
        fields.get(rule.key()).setText(Integer.toString(Math.clamp(value+by,rule.min(),rule.max())));
    }
    private void request() {
        if(!ClientPlayNetworking.canSend(SettingsPayload.ID)) { message="服务器未安装这版更新，请房主和玩家一起更新。"; return; }
        busy=true; waitTicks=0; message="正在读取服务器参数…";
        ClientPlayNetworking.send(new SettingsPayload(new NbtCompound()));
    }
    public void receive(NbtCompound nbt) {
        if(nbt==null)return;
        state=nbt.copy(); values.clear();
        for(var rule:MatchTuning.RULES) values.put(rule.key(),Integer.toString(state.getInt(rule.key())));
        loaded=true; busy=false;
        message=state.getString("message");
        if(message.isEmpty())message=state.getBoolean("editable")?"修改后点击保存；时间单位为秒。":"进行中的对局仅可查看，结束后可修改。";
        clearAndInit();
    }
    private void preset(boolean fast) {
        for(var rule:MatchTuning.RULES) values.put(rule.key(),Integer.toString(rule.initial()));
        if(fast) { values.put("prep","180");values.put("rage","180");values.put("battle","900");values.put("deathmatch","300");values.put("wither_decay","10");values.put("wither_interval","3"); }
        message="已填入"+(fast?"快局":"常规")+"预设，点击保存后生效。"; clearAndInit();
    }
    private void save() {
        NbtCompound request=new NbtCompound();
        for(var rule:MatchTuning.RULES) {
            int value;
            try { value=Integer.parseInt(values.getOrDefault(rule.key(),"")); } catch(NumberFormatException e) { message=rule.label()+"需要填写整数。"; return; }
            if(!rule.valid(value)) { message=rule.label()+"范围："+rule.min()+"～"+rule.max()+rule.unit();return; }
            request.putInt(rule.key(),value);
        }
        request.putInt("revision",state.getInt("revision"));request.putBoolean("apply",true);
        busy=true;waitTicks=0;message="正在保存全部参数…";
        ClientPlayNetworking.send(new SettingsPayload(request));clearAndInit();
    }
    @Override public void tick() {
        if(busy && ++waitTicks>100) {busy=false;loaded=true;message="服务器未回应，请刷新后重试。";clearAndInit();}
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        EpicTheme.background(context,width,height);EpicTheme.panel(context,left,top,panelWidth,panelHeight);
        EpicTheme.heading(context,textRenderer,"超级战墙 · "+GROUPS[group], "第 "+(page+1)+" / "+pages()+" 页 · 世界对局规则",left+34,top+6);
        context.drawItem((group==0?net.minecraft.item.Items.SHIELD:net.minecraft.item.Items.WITHER_SKELETON_SKULL).getDefaultStack(),left+11,top+10);
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(message,panelWidth-24),left+12,top+36,0xFFD4BADF);
        for(int i=0;i<rows && page*rows+i<shown.size();i++) {
            var rule=shown.get(page*rows+i);
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(rule.label()+"（"+rule.unit()+"）",panelWidth-170),left+12,top+81+i*25,0xFFEDF5F9);
        }
        for(var child:children())if(child instanceof net.minecraft.client.gui.Drawable d)d.render(context,mouseX,mouseY,delta);
    }
}
