package me.petr1furious.potatominigames.games.party;

import java.util.*;

record HoleWallPattern(List<Cell> cells, List<Gate> gates, String title) {
    static final int HALF_WIDTH=13, HEIGHT=5;
    record Cell(int u,int y) {}
    record Gate(int start,int width,int bottom,int height) {
        boolean contains(int u,int y){return u>=start&&u<start+width&&y>=bottom&&y<bottom+height;}
    }
    HoleWallPattern { cells=List.copyOf(cells);gates=List.copyOf(gates); }
    static HoleWallPattern generate(int stage,int radius,Random random){
        List<Gate> gates=new ArrayList<>();
        int kind=Math.floorMod(stage-1,10);
        String title;
        switch(kind){
            case 0 -> {title="Арка";gates.add(gate(3,0,3,radius,random));}
            case 1 -> {title="Двойные ворота";gates.add(new Gate(-radius,2,0,3));gates.add(new Gate(radius-1,2,0,3));}
            case 2 -> {title="Барьер";gates.add(gate(3,1,3,radius,random));}
            case 3 -> {title="Ступени";gates.add(new Gate(-radius,2,0,3));gates.add(new Gate(radius-1,2,1,3));}
            case 4 -> {title="Колонны";gates.add(gate(1,0,3,radius,random));}
            case 5 -> {title="Зубцы";gates.add(gate(2,1,3,radius,random));}
            case 6 -> {title="Зигзаг";gates.add(gate(2,0,3,radius,random));}
            case 7 -> {title="Акведук";gates.add(new Gate(-radius,2,0,3));gates.add(new Gate(radius-1,2,0,3));}
            case 8 -> {title="Навес";gates.add(gate(2,0,3,radius,random));}
            default -> {title="Замочная скважина";gates.add(gate(1,0,3,radius,random));}
        }
        List<Cell> cells=new ArrayList<>();
        for(int u=-HALF_WIDTH;u<=HALF_WIDTH;u++)for(int y=0;y<HEIGHT;y++){
            boolean hole=false;for(Gate gate:gates)if(gate.contains(u,y)){hole=true;break;}
            // Silhouettes vary as well as passages. Every advertised gate overrides the outline.
            boolean solid=switch(kind){
                case 0->y<4||Math.abs(u)<=4;
                case 2->y==0||(Math.abs(u)>=5&&Math.abs(u)%4==1&&y<4);
                case 3->y<=Math.floorMod(u+HALF_WIDTH,5);
                case 4->y<4&&(Math.floorMod(u,3)!=0||y==0);
                case 5->y<=(Math.floorMod(u,2)==0?3:1);
                case 6->y<4&&!(y>=2&&Math.floorMod(u+y,5)<=1);
                case 7->y==3||Math.floorMod(u,4)==0;
                case 8->y==2||y==3||(Math.abs(u)>=4&&Math.floorMod(u,4)==0);
                case 9->!(y>=2&&y<=3&&Math.abs(u)<=2);
                default->true;
            };
            if(!hole&&solid)cells.add(new Cell(u,y));
        }
        return new HoleWallPattern(cells,gates,title);
    }
    private static Gate gate(int width,int bottom,int height,int radius,Random random){
        // Keep landing margins where possible, but a three-wide arm also accepts a full-width gate.
        width=Math.min(width,2*radius+1);
        int padding=2*radius+1-width>=2?1:0;
        int min=-radius+padding,max=radius+1-width-padding;
        return new Gate(min+random.nextInt(max-min+1),width,bottom,height);
    }
}
