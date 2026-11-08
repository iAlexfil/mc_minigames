package me.petr1furious.potatominigames.games.party;

import java.util.*;

record HoleWallPattern(List<Cell> cells, List<Gate> gates) {
    static final int HALF_WIDTH=13, HEIGHT=5;
    record Cell(int u,int y) {}
    record Gate(int start,int width,int bottom,int height) {
        boolean contains(int u,int y){return u>=start&&u<start+width&&y>=bottom&&y<bottom+height;}
    }
    HoleWallPattern { cells=List.copyOf(cells);gates=List.copyOf(gates); }
    static HoleWallPattern generate(int stage,int radius,Random random){
        List<Gate> gates=new ArrayList<>();
        int kind=Math.floorMod(stage-1,6);
        switch(kind){
            case 0 -> gates.add(gate(3,0,2,radius,random));
            case 1 -> {gates.add(new Gate(-radius/2-1,2,0,2));gates.add(new Gate(radius/2,2,0,2));}
            case 2 -> gates.add(gate(3,1,3,radius,random));
            case 3 -> {gates.add(new Gate(-radius/2-1,2,0,2));gates.add(new Gate(radius/2,2,1,3));}
            case 4 -> gates.add(gate(1,0,2,radius,random));
            default -> gates.add(gate(2,1,3,radius,random));
        }
        List<Cell> cells=new ArrayList<>();
        for(int u=-HALF_WIDTH;u<=HALF_WIDTH;u++)for(int y=0;y<HEIGHT;y++){
            boolean hole=false;for(Gate gate:gates)if(gate.contains(u,y)){hole=true;break;}
            if(!hole)cells.add(new Cell(u,y));
        }
        return new HoleWallPattern(cells,gates);
    }
    private static Gate gate(int width,int bottom,int height,int radius,Random random){
        // Keep a block of landing room on both sides of the passage.
        return new Gate(-radius+1+random.nextInt(2*radius-width),width,bottom,height);
    }
}
