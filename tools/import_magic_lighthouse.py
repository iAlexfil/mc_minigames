#!/usr/bin/env python3
"""Convert the pinned NetherGames Lighthouse Bedrock asset, without generating terrain.
Requires amulet-leveldb==1.0.7 and PyYAML. Run with a local source world directory.
The output is a gzip palette/coordinate stream consumed by MagicBattleArena.
"""
import argparse
import collections
import gzip
import json
import math
import pathlib
import struct

class Reader:
    def __init__(self, data): self.data, self.pos = data, 0
    def take(self, n):
        value = self.data[self.pos:self.pos+n]
        if len(value) != n: raise ValueError('Truncated source NBT')
        self.pos += n
        return value
    def num(self, fmt): return struct.unpack('<'+fmt, self.take(struct.calcsize('<'+fmt)))[0]
    def string(self): return self.take(self.num('H')).decode('utf-8')
    def tag(self, kind):
        if kind == 1: return self.num('b')
        if kind == 2: return self.num('h')
        if kind == 3: return self.num('i')
        if kind == 4: return self.num('q')
        if kind == 5: return self.num('f')
        if kind == 6: return self.num('d')
        if kind == 7: return self.take(self.num('i'))
        if kind == 8: return self.string()
        if kind == 9:
            child, count = self.num('B'), self.num('i')
            return [self.tag(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while (child := self.num('B')):
                name = self.string(); result[name] = self.tag(child)
            return result
        if kind in (11,12): return [self.num('i' if kind == 11 else 'q') for _ in range(self.num('i'))]
        raise ValueError(f'Unsupported NBT tag {kind}')
    def root(self):
        kind = self.num('B'); self.string()
        return self.tag(kind)

def decode(source):
    import leveldb
    db = leveldb.LevelDB(str(source/'db'))
    blocks, statistics = {}, collections.Counter()
    try:
        for key, data in db.iterate():
            if len(key) != 10 or key[8] != 0x2f: continue
            cx, cz = struct.unpack('<ii', key[:8]); sy = struct.unpack('b', key[9:])[0]
            reader = Reader(data); version = reader.num('B')
            if version not in (8,9): raise ValueError(f'Unsupported subchunk version {version}')
            layers = reader.num('B')
            if version == 9: sy = reader.num('b')
            for layer in range(layers):
                bits = reader.num('B') >> 1
                per_word = 32 // bits if bits else 4096
                words = [reader.num('I') for _ in range(math.ceil(4096/per_word))] if bits else []
                count = reader.num('I') if bits else 1
                palette = [reader.root() for _ in range(count)]
                if layer != 0: continue
                for index in range(4096):
                    value = (words[index//per_word] >> ((index%per_word)*bits)) & ((1<<bits)-1) if bits else 0
                    state = palette[value]
                    if state['name'] in ('minecraft:air', 'minecraft:info_update'): continue
                    x, y, z = cx*16+(index>>8), sy*16+(index&15), cz*16+((index>>4)&15)
                    blocks[x,y,z] = state
                    statistics[json.dumps({'name':state['name'], 'states':state.get('states',{})},sort_keys=True)] += 1
    finally: db.close()
    return blocks, statistics


COLORS = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
WOODS = ['oak','spruce','birch','jungle','acacia','dark_oak']
SLABS = ['smooth_stone','sandstone','petrified_oak','cobblestone','brick','stone_brick','quartz','nether_brick']
SLABS2 = ['red_sandstone','purpur','prismarine','dark_prismarine','prismarine_brick','mossy_cobblestone','smooth_sandstone','red_nether_brick']
STAIRS = ['east','west','south','north']
FACING = {2:'north',3:'south',4:'west',5:'east'}

def java_state(state):
    name, value = state['name'].split(':')[1], state.get('val',0)
    if name == 'stone': return ['stone','granite','polished_granite','diorite','polished_diorite','andesite','polished_andesite'][value]
    if name == 'planks': return WOODS[value&7]+'_planks'
    if name == 'wood': return WOODS[value&7]+'_wood'
    if name == 'log': return WOODS[value&3]+'_log[axis='+('x' if value&12==4 else 'z' if value&12==8 else 'y')+']'
    if name == 'stained_hardened_clay': return COLORS[value]+'_terracotta'
    if name == 'hardened_clay': return 'terracotta'
    if name in ('stained_glass','stained_glass_pane','concrete','wool'): return COLORS[value]+'_'+name.replace('stained_','') if name not in ('stained_glass','stained_glass_pane') else COLORS[value]+'_'+name
    if name in ('stone_slab','stone_slab2','wooden_slab','double_stone_slab','double_stone_slab2','double_wooden_slab'):
        palette = WOODS if 'wooden' in name else SLABS2 if name.endswith('2') else SLABS
        material = palette[value&7]
        return material+'_slab[type='+('double' if name.startswith('double_') else 'top' if value&8 else 'bottom')+']'
    if name.endswith('_stairs'): return name+'[facing='+STAIRS[value&3]+',half='+('top' if value&4 else 'bottom')+',shape=straight]'
    if name == 'sandstone': return ['sandstone','chiseled_sandstone','cut_sandstone','smooth_sandstone'][value]
    if name == 'sand': return 'red_sand' if value else 'sand'
    if name == 'quartz_block': return ['quartz_block','chiseled_quartz_block','quartz_pillar[axis=y]','quartz_pillar[axis=x]','quartz_pillar[axis=z]'][value]
    if name == 'trapdoor': return 'oak_trapdoor[facing='+STAIRS[value&3]+',half='+('top' if value&8 else 'bottom')+',open='+str(bool(value&4)).lower()+']'
    if name == 'cobblestone_wall': return {0:'cobblestone_wall',3:'diorite_wall'}[value]
    if name == 'chest': return 'chest[facing='+FACING.get(value,'north')+',type=single]'
    if name == 'brick_block': return 'bricks'
    if name == 'fence': return WOODS[value&7]+'_fence'
    if name == 'cocoa': return 'cocoa[facing='+['south','west','north','east'][value&3]+',age='+str(min(2,value>>2))+']'
    if name == 'dirt': return ['dirt','coarse_dirt','podzol'][value]
    if name == 'torch': return 'torch' if value==5 else 'wall_torch[facing='+{1:'east',2:'west',3:'south',4:'north'}.get(value,'north')+']'
    if name in ('coal_block','iron_ore','diamond_ore','gold_block','cobblestone','mossy_cobblestone','glowstone','enchanting_table','cobblestone_wall','iron_bars'): return name
    raise ValueError('Unmapped source block '+str(state))

def export(source, output):
    import yaml
    blocks, _ = decode(source)
    converted = {p:'minecraft:'+java_state(state) for p,state in blocks.items()}
    # Legacy connected shapes were derived by the Bedrock engine rather than stored in NBT.
    for p, name in list(converted.items()):
        if name.endswith(('_fence','_wall','_pane','iron_bars')):
            directions = {'north':(0,0,-1),'south':(0,0,1),'west':(-1,0,0),'east':(1,0,0)}
            props = []
            for side,d in directions.items():
                neighbour = converted.get(tuple(p[a]+d[a] for a in range(3)))
                connected = neighbour is not None and not any(k in neighbour for k in ['torch','cocoa','trapdoor'])
                props.append(side+'='+('low' if connected else 'none') if name.endswith('_wall') else side+'='+str(connected).lower())
            if name.endswith('_wall'):props.append('up=true')
            converted[p] = name+'['+','.join(props)+']'
    palette = sorted(set(converted.values())); indices={v:i for i,v in enumerate(palette)}
    config=yaml.safe_load((source/'arenas.yml').read_text())['arenas']['SW-Lighthouse']
    out=bytearray(b'MAGICMAP1')
    out+=struct.pack('>I',len(palette))
    for name in palette:
        data=name.encode();out+=struct.pack('>H',len(data))+data
    out+=struct.pack('>I',len(converted))
    for (x,y,z),name in sorted(converted.items()):out+=struct.pack('>hhhH',x,y,z,indices[name])
    out+=struct.pack('>I',len(config['spawns']))
    for p in config['spawns']:out+=struct.pack('>ffff',p['x'],p['y'],p['z'],p['yaw'])
    output.parent.mkdir(parents=True,exist_ok=True)
    with open(output,'wb') as raw:
        with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as zipped:zipped.write(out)
    print('Imported',len(converted),'source blocks,',len(palette),'Java states,',len(config['spawns']),'original spawns into',output)
    (source/'java-states.txt').write_text('\n'.join(palette)+'\n')
    return converted,config

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=pathlib.Path);parser.add_argument('output',type=pathlib.Path)
    args=parser.parse_args();export(args.source,args.output)
