package com.megawalls.command;

import com.megawalls.MWMod;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;

/** Player selectors for the Mod's public commands, with vanilla player/count validation. */
public final class GamePlayersArgument {
    private GamePlayersArgument(){}
    public static void register(){
        ArgumentTypeRegistry.registerArgumentType(MWMod.id("game_players"),Players.class,ConstantArgumentSerializer.of(Players::new));
        ArgumentTypeRegistry.registerArgumentType(MWMod.id("game_player"),Player.class,ConstantArgumentSerializer.of(Player::new));
    }
    public static EntityArgumentType players(){return new Players();}
    public static EntityArgumentType player(){return new Player();}
    private static <S> CompletableFuture<Suggestions> suggestions(CommandContext<S> context,SuggestionsBuilder builder){
        if(!(context.getSource() instanceof CommandSource source))return Suggestions.empty();
        var reader=new StringReader(builder.getInput());reader.setCursor(builder.getStart());
        var selector=new EntitySelectorReader(reader,true);
        try{selector.read();}catch(CommandSyntaxException ignored){}
        return selector.listSuggestions(builder,b->CommandSource.suggestMatching(source.getPlayerNames(),b));
    }
    public static final class Players extends EntityArgumentType {
        public Players(){super(false,true);}
        @Override public <S> EntitySelector parse(StringReader reader,S source)throws CommandSyntaxException{return super.parse(reader);}
        @Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context,SuggestionsBuilder builder){return suggestions(context,builder);}
    }
    public static final class Player extends EntityArgumentType {
        public Player(){super(true,true);}
        @Override public <S> EntitySelector parse(StringReader reader,S source)throws CommandSyntaxException{return super.parse(reader);}
        @Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context,SuggestionsBuilder builder){return suggestions(context,builder);}
    }
}
