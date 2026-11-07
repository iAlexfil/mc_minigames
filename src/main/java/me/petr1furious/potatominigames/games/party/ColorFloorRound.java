package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.Material;
final class ColorFloorRound {
    static final Material[] COLORS={Material.RED_CONCRETE,Material.BLUE_CONCRETE,Material.YELLOW_CONCRETE,Material.LIME_CONCRETE,Material.ORANGE_CONCRETE,Material.PURPLE_CONCRETE};
    static final String[] TITLES={"Красный","Синий","Жёлтый","Лаймовый","Оранжевый","Фиолетовый"};
    private final Material[][] cells=new Material[21][21];
    private final int target;
    private ColorFloorRound(int target){this.target=target;}
    static ColorFloorRound generate(int stage,Random random) {
        var r=new ColorFloorRound(random.nextInt(COLORS.length));
        for(int x=0;x<21;x++)for(int z=0;z<21;z++)r.cells[x][z]=COLORS[random.nextInt(COLORS.length)];
        for(int x=0;x<21;x+=3)for(int z=0;z<21;z+=3)r.cells[x+random.nextInt(3)][z+random.nextInt(3)]=r.target();
        return r;
    }
    static int choiceTicks(int stage){return (int)Math.max(40,120-8L*(Math.max(1,stage)-1));}
    Material target(){return COLORS[target];}
    String title(){return TITLES[target];}
    Material material(int x,int z){return cells[x][z];}
    boolean safe(double x,double y,double z){return x>=0&&x<21&&z>=0&&z<21&&y>=81&&y<=83&&cells[(int)x][(int)z]==target();}
}
